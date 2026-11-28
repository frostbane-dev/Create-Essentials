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
        assertSame(DarkGuiStyling.PALETTE_VANILLA, assertDoesNotThrow(DarkGuiStyling::activePalette));
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
}
