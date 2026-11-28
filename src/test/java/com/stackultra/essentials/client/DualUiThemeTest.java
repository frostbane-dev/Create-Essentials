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

            assertFalse(vanillaTinted.textShadow(),
                    "VANILLA tinted backpack GUI must keep unshadowed labels for " + color);
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

            // VANILLA tinted UI must remain clearly lighter than DARK with crisp dark-text contrast
            double panelLuma = relativeLuma(vanillaTinted.panelBg());
            double darkPanelLuma = relativeLuma(darkTinted.panelBg());
            double titleLuma = relativeLuma(vanillaTinted.title());
            assertTrue(panelLuma >= 120.0,
                    "VANILLA tinted panel must stay in light mid-tone range for " + color + " (luma=" + panelLuma + ")");
            assertTrue(panelLuma - darkPanelLuma >= 35.0,
                    "VANILLA tinted panel must be clearly lighter than DARK panel for " + color);
            assertTrue(panelLuma - titleLuma >= 90.0,
                    "VANILLA title must maintain crisp contrast against light panel for " + color);

            // VANILLA tooltip background and inner panel must be rich mid-tone light, while DARK stays dark
            assertTrue(relativeLuma(vanillaTp.tooltipBgStart()) >= 110.0,
                    "VANILLA tooltip bg start must stay clearly light for " + color);
            assertTrue(relativeLuma(vanillaTp.panelBg()) >= 120.0,
                    "VANILLA tooltip inner panel must stay clearly light for " + color);
            assertTrue(relativeLuma(darkTp.tooltipBgStart()) < 50.0,
                    "DARK tooltip bg start must stay dark for " + color);
        }

        // Vibrant chroma checks (no pale/washed-out pastel dilution)
        DarkGuiStyling.Palette orangeVanilla =
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.ORANGE);
        int orangeR = (orangeVanilla.panelBg() >> 16) & 0xFF;
        int orangeB = orangeVanilla.panelBg() & 0xFF;
        assertTrue(orangeR - orangeB >= 100,
                "Orange VANILLA panel should have rich warm saturation (R=" + orangeR + ", B=" + orangeB + ")");

        DarkGuiStyling.Palette magentaVanilla =
                DarkGuiStyling.tintedPalette(ClientConfig.UiTheme.VANILLA, net.minecraft.world.item.DyeColor.MAGENTA);
        int magR = (magentaVanilla.panelBg() >> 16) & 0xFF;
        int magG = (magentaVanilla.panelBg() >> 8) & 0xFF;
        int magB = magentaVanilla.panelBg() & 0xFF;
        assertTrue(magR - magG >= 80 && magB - magG >= 75,
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
                "VANILLA theme tooltip must use light tinted slot palette");

        assertTrue(relativeLuma(darkTooltip.palette().tooltipBgStart()) < 50.0,
                "DARK tooltip background must remain dark");
        assertTrue(relativeLuma(vanillaTooltip.palette().tooltipBgStart()) >= 120.0,
                "VANILLA tooltip background must be rich mid-tone light");
        assertTrue(relativeLuma(vanillaTooltip.palette().panelBg()) > relativeLuma(darkTooltip.palette().panelBg()),
                "VANILLA tooltip panel must be lighter than DARK tooltip panel");

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
