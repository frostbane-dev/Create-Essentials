package com.stackultra.essentials.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualUiThemeTest {

    @Test
    void unloadedConfigSafelyDefaultsToVanilla() {
        assertFalse(ClientConfig.SPEC.isLoaded(), "ClientConfig.SPEC should not be loaded in headless unit tests");
        ClientConfig.UiTheme theme = assertDoesNotThrow(ClientConfig::getUiTheme);
        assertEquals(ClientConfig.UiTheme.VANILLA, theme);
        assertSame(DarkGuiStyling.PALETTE_VANILLA, assertDoesNotThrow(() -> DarkGuiStyling.activePalette()));
    }

    @Test
    void vanillaPaletteMatchesClassicMinecraftContainerColors() {
        DarkGuiStyling.Palette p = DarkGuiStyling.palette(ClientConfig.UiTheme.VANILLA);
        assertSame(DarkGuiStyling.PALETTE_VANILLA, p);

        assertEquals(0xFFC6C6C6, p.panelBg());
        assertEquals(0xFFFFFFFF, p.bevelLight());
        assertEquals(0xFF555555, p.bevelDark());
        assertEquals(0xFF000000, p.borderDark());

        assertEquals(0xFF8B8B8B, p.slotBg());
        assertEquals(0xFF373737, p.slotBorderDark());
        assertEquals(0xFFFFFFFF, p.slotBorderLight());
        assertEquals(0xFF8B8B8B, p.slotCorner());

        assertEquals(0xFF404040, p.title());
        assertEquals(0xFF404040, p.text());
        assertEquals(0xFF404040, p.textDim());
        assertFalse(p.textShadow(), "VANILLA container labels should render without drop shadow");
    }

    @Test
    void darkPalettePreservesOriginalDarkBeveledColorsOneToOne() {
        DarkGuiStyling.Palette p = DarkGuiStyling.palette(ClientConfig.UiTheme.DARK);
        assertSame(DarkGuiStyling.PALETTE_DARK, p);

        assertEquals(DarkGuiStyling.COLOR_PANEL, p.panelBg());
        assertEquals(DarkGuiStyling.COLOR_BEVEL_LIGHT, p.bevelLight());
        assertEquals(DarkGuiStyling.COLOR_BEVEL_DARK, p.bevelDark());
        assertEquals(DarkGuiStyling.COLOR_BORDER_DARK, p.borderDark());

        assertEquals(DarkGuiStyling.COLOR_SLOT, p.slotBg());
        assertEquals(DarkGuiStyling.COLOR_SLOT_BORDER_DARK, p.slotBorderDark());
        assertEquals(DarkGuiStyling.COLOR_SLOT_BORDER, p.slotBorderLight());
        assertEquals(DarkGuiStyling.COLOR_SLOT_CORNER, p.slotCorner());

        assertEquals(DarkGuiStyling.COLOR_TITLE, p.title());
        assertEquals(DarkGuiStyling.COLOR_TEXT, p.text());
        assertEquals(DarkGuiStyling.COLOR_TEXT_DIM, p.textDim());
        assertTrue(p.textShadow(), "DARK theme labels should preserve drop shadow");

        assertEquals(0xFF1A1A1A, p.buttonOutline());
        assertEquals(0xFF5A5A5A, p.buttonFill());
        assertEquals(0xFF6A6A6A, p.buttonFillHover());
        assertEquals(0xFF828282, p.buttonLight());
        assertEquals(0xFF969696, p.buttonLightHover());
        assertEquals(0xFF323232, p.buttonDark());
    }

    @Test
    void nullThemeSafelyFallsBackToVanilla() {
        assertSame(DarkGuiStyling.PALETTE_VANILLA, DarkGuiStyling.palette(null));
    }

    @Test
    void dynamicBackpackGuiPalettesTintAllSixteenColorsAndPreserveThemeSemantics() {
        assertSame(DarkGuiStyling.PALETTE_VANILLA,
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, null));
        assertSame(DarkGuiStyling.PALETTE_DARK,
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.DARK, null));
        assertSame(DarkGuiStyling.PALETTE_VANILLA,
                DarkGuiStyling.activePalette(null));

        for (net.minecraft.world.item.DyeColor color : net.minecraft.world.item.DyeColor.values()) {
            DarkGuiStyling.Palette vanillaTinted =
                    assertDoesNotThrow(() -> DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, color));
            DarkGuiStyling.Palette darkTinted =
                    assertDoesNotThrow(() -> DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.DARK, color));

            // Deeper VANILLA luminance hierarchy: bevelLight > panelBg > slotBg > bevelDark > slotBorderDark
            double panelLum = DarkGuiStyling.relativeLuminance(vanillaTinted.panelBg());
            double slotLum = DarkGuiStyling.relativeLuminance(vanillaTinted.slotBg());
            double bevelLightLum = DarkGuiStyling.relativeLuminance(vanillaTinted.bevelLight());
            double bevelDarkLum = DarkGuiStyling.relativeLuminance(vanillaTinted.bevelDark());
            assertTrue(bevelLightLum > panelLum,
                    "VANILLA bevelLight must be brighter than panelBg for " + color);
            assertTrue(slotLum < panelLum,
                    "VANILLA recessed slotBg must be darker than panelBg for " + color);
            assertTrue(bevelDarkLum < slotLum,
                    "VANILLA bevelDark must be darker than slotBg for " + color);

            // Contrast & readability on every dyed VANILLA panel and recessed slot
            assertTrue(DarkGuiStyling.contrastRatio(vanillaTinted.panelBg(), vanillaTinted.text()) >= 4.5,
                    "VANILLA text contrast on panelBg must be >= 4.5 for " + color);
            assertTrue(DarkGuiStyling.contrastRatio(vanillaTinted.panelBg(), vanillaTinted.title()) >= 4.5,
                    "VANILLA title contrast on panelBg must be >= 4.5 for " + color);
            assertTrue(DarkGuiStyling.contrastRatio(vanillaTinted.slotBg(), 0xFFFFFFFF) >= 4.3,
                    "White stack numbers must stay readable on VANILLA slotBg for " + color);

            // DARK theme must remain untouched
            assertTrue(darkTinted.textShadow(),
                    "DARK tinted backpack GUI must keep drop-shadowed labels for " + color);
            assertEquals(DarkGuiStyling.PALETTE_DARK.slotBg(), darkTinted.slotBg(),
                    "DARK tinted backpack GUI must keep crisp dark slot interiors for " + color);
            assertTrue(vanillaTinted.panelBg() != DarkGuiStyling.PALETTE_VANILLA.panelBg(),
                    "VANILLA tinted panel should visibly shift from default gray for " + color);
            assertTrue(darkTinted.panelBg() != DarkGuiStyling.PALETTE_DARK.panelBg(),
                    "DARK tinted panel should visibly shift from default dark gray for " + color);

            BackpackTooltipPalette darkTp = BackpackTooltipPalette.resolve(
                    com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MECHANICAL,
                    color,
                    com.stackultra.essentials.content.backpack.BackpackTooltipData.NO_CUSTOM_RGB,
                    ClientConfig.UiTheme.DARK);
            BackpackTooltipPalette vanillaTp = BackpackTooltipPalette.resolve(
                    com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MECHANICAL,
                    color,
                    com.stackultra.essentials.content.backpack.BackpackTooltipData.NO_CUSTOM_RGB,
                    ClientConfig.UiTheme.VANILLA);

            assertTrue(darkTp.borderStart() != darkTp.borderEnd(),
                    "DARK tooltip palette border gradient should be distinct for " + color);
            assertTrue(vanillaTp.borderStart() != vanillaTp.borderEnd(),
                    "VANILLA tooltip palette border gradient should be distinct for " + color);
            assertEquals(vanillaTinted.panelBg(), vanillaTp.panelBg(),
                    "VANILLA tooltip panelBg must match VANILLA GUI panelBg for " + color);
            assertTrue(relativeLuma(darkTp.tooltipBgStart()) < 50.0,
                    "DARK tooltip bg start must stay dark for " + color);
            assertTrue(relativeLuma(vanillaTp.tooltipBgStart()) < 50.0,
                    "VANILLA dyed tooltip text backdrop must stay dark-tinted for crisp text readability for " + color);
        }

        // Deep saturated Cyan check matching the Shulker-Box-Tooltip reference tone
        DarkGuiStyling.Palette cyanVanilla =
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.CYAN);
        assertEquals(0xFF1E7979, cyanVanilla.panelBg());
        assertEquals(0xFF155555, cyanVanilla.slotBg());
        assertEquals(0xFF269C9C, cyanVanilla.bevelLight());
        assertEquals(0xFF0D3434, cyanVanilla.bevelDark());
        assertEquals(0xFF082222, cyanVanilla.slotBorderDark());

        // Vibrant chroma checks (deep saturated dye tones rather than pale pastel dilution)
        DarkGuiStyling.Palette orangeVanilla =
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.ORANGE);
        int orangeR = (orangeVanilla.panelBg() >> 16) & 0xFF;
        int orangeB = orangeVanilla.panelBg() & 0xFF;
        assertTrue(orangeR - orangeB >= 120,
                "Orange VANILLA panel should have rich warm saturation (R=" + orangeR + ", B=" + orangeB + ")");

        DarkGuiStyling.Palette magentaVanilla =
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.MAGENTA);
        int magR = (magentaVanilla.panelBg() >> 16) & 0xFF;
        int magG = (magentaVanilla.panelBg() >> 8) & 0xFF;
        int magB = magentaVanilla.panelBg() & 0xFF;
        assertTrue(magR - magG >= 90 && magB - magG >= 85,
                "Magenta VANILLA panel should be rich vivid magenta rather than pale pastel (R=" + magR + ", G=" + magG + ", B=" + magB + ")");
    }

    @Test
    void backpackContentTooltipSwitchesBetweenDarkAndVanillaThemes() {
        com.stackultra.essentials.content.backpack.BackpackTooltipData data =
                new com.stackultra.essentials.content.backpack.BackpackTooltipData(
                        com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MAGNETIC,
                        net.minecraft.world.item.DyeColor.ORANGE,
                        com.stackultra.essentials.content.backpack.BackpackTooltipData.NO_CUSTOM_RGB,
                        java.util.List.of(),
                        0,
                        java.util.List.of(),
                        false);

        ClientBackpackTooltip darkTooltip = new ClientBackpackTooltip(data, ClientConfig.UiTheme.DARK);
        ClientBackpackTooltip vanillaTooltip = new ClientBackpackTooltip(data, ClientConfig.UiTheme.VANILLA);

        assertSame(DarkGuiStyling.PALETTE_DARK, darkTooltip.slotPalette(),
                "DARK theme tooltip must keep dark slot palette");
        assertSame(DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.ORANGE),
                vanillaTooltip.slotPalette(),
                "VANILLA theme tooltip must use rich tinted slot palette");

        assertTrue(relativeLuma(darkTooltip.palette().tooltipBgStart()) < 50.0,
                "DARK tooltip background must remain dark");
        assertEquals(DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.ORANGE).panelBg(),
                vanillaTooltip.palette().panelBg(),
                "VANILLA tooltip inner panel must match VANILLA GUI panel");

        // Unpainted backpack tooltips also respect DARK vs VANILLA
        BackpackTooltipPalette unpaintedDark = BackpackTooltipPalette.resolve(
                com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MECHANICAL,
                null,
                com.stackultra.essentials.content.backpack.BackpackTooltipData.NO_CUSTOM_RGB,
                ClientConfig.UiTheme.DARK);
        BackpackTooltipPalette unpaintedVanilla = BackpackTooltipPalette.resolve(
                com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MECHANICAL,
                null,
                com.stackultra.essentials.content.backpack.BackpackTooltipData.NO_CUSTOM_RGB,
                ClientConfig.UiTheme.VANILLA);
        assertEquals(DarkGuiStyling.COLOR_PANEL, unpaintedDark.panelBg());
        assertEquals(DarkGuiStyling.PALETTE_VANILLA.panelBg(), unpaintedVanilla.panelBg());
        assertTrue(relativeLuma(unpaintedVanilla.tooltipBgStart()) >= 180.0);
        assertTrue(relativeLuma(unpaintedDark.tooltipBgStart()) < 50.0);
    }

    private static double relativeLuma(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }
}
