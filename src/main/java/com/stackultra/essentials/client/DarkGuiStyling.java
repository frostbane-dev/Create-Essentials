package com.stackultra.essentials.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DarkGuiStyling {

    public record Palette(
            int panelBg,
            int bevelLight,
            int bevelDark,
            int borderDark,
            int slotBg,
            int slotBorderDark,
            int slotBorderLight,
            int slotCorner,
            int text,
            int textDim,
            int title,
            boolean textShadow,
            int barBg,
            int barNormal,
            int barLow,
            int barCritical,
            int chargingText,
            int emptyChargeText,
            int blacklistText,
            int whitelistText,
            int buttonOutline,
            int buttonFill,
            int buttonFillHover,
            int buttonLight,
            int buttonLightHover,
            int buttonDark,
            int buttonText,
            boolean buttonTextShadow
    ) {
    }

    public static final int COLOR_PANEL = 0xFF4A4A4A;
    public static final int COLOR_BORDER = 0xFF6E6E6E;
    public static final int COLOR_BEVEL_LIGHT = 0xFF6E6E6E;
    public static final int COLOR_BEVEL_DARK = 0xFF2B2B2B;
    public static final int COLOR_BORDER_DARK = 0xFF1A1A1A;

    public static final int COLOR_SLOT = 0xFF2E2E2E;
    public static final int COLOR_SLOT_BORDER_DARK = 0xFF1B1B1B;
    public static final int COLOR_SLOT_BORDER = 0xFF646464;
    public static final int COLOR_SLOT_CORNER = 0xFF3C3C3C;

    public static final int COLOR_TEXT = 0xFFEAEAEA;
    public static final int COLOR_TEXT_DIM = 0xFFC8C8C8;
    public static final int COLOR_TITLE = 0xFFF2C24C;
    public static final int COLOR_BAR_BG = 0xFF101010;
    public static final int COLOR_BAR = 0xFF7FD7FF;
    public static final int SLOT_SIZE = 18;

    public static final float FILTER_GHOST_ALPHA = 0.30F;

    /**
     * Classic Minecraft container GUI palette (#C6C6C6 panel, #8B8B8B recessed slots, #404040 unshadowed labels).
     */
    public static final Palette PALETTE_VANILLA = new Palette(
            0xFFC6C6C6, // panelBg
            0xFFFFFFFF, // bevelLight
            0xFF555555, // bevelDark
            0xFF000000, // borderDark
            0xFF8B8B8B, // slotBg
            0xFF373737, // slotBorderDark
            0xFFFFFFFF, // slotBorderLight
            0xFF8B8B8B, // slotCorner
            0xFF404040, // text
            0xFF404040, // textDim
            0xFF404040, // title
            false,      // textShadow
            0xFF373737, // barBg
            0xFF1E88C8, // barNormal
            0xFFD48800, // barLow
            0xFFC82828, // barCritical
            0xFF0E6A8A, // chargingText
            0xFFA82020, // emptyChargeText
            0xFFEA9BFF, // blacklistText (high-contrast on vanilla stone button)
            0xFF9ED4FF, // whitelistText (high-contrast on vanilla stone button)
            0xFF000000, // buttonOutline
            0xFF8B8B8B, // buttonFill (classic Minecraft stone button grey)
            0xFFA0A0A0, // buttonFillHover
            0xFFD0D0D0, // buttonLight
            0xFFE6E6E6, // buttonLightHover
            0xFF555555, // buttonDark
            0xFFFFFFFF, // buttonText
            true        // buttonTextShadow
    );

    /**
     * Custom dark beveled GUI palette (1:1 identical to original dark theme).
     */
    public static final Palette PALETTE_DARK = new Palette(
            COLOR_PANEL,            // panelBg
            COLOR_BEVEL_LIGHT,      // bevelLight
            COLOR_BEVEL_DARK,       // bevelDark
            COLOR_BORDER_DARK,      // borderDark
            COLOR_SLOT,             // slotBg
            COLOR_SLOT_BORDER_DARK, // slotBorderDark
            COLOR_SLOT_BORDER,      // slotBorderLight
            COLOR_SLOT_CORNER,      // slotCorner
            COLOR_TEXT,             // text
            COLOR_TEXT_DIM,         // textDim
            COLOR_TITLE,            // title
            true,                   // textShadow
            COLOR_BAR_BG,           // barBg
            COLOR_BAR,              // barNormal
            0xFFF2C24C,             // barLow
            0xFFE05A5A,             // barCritical
            COLOR_BAR,              // chargingText
            0xFFE05A5A,             // emptyChargeText
            0xFFD66BFF,             // blacklistText
            0xFF78B8FF,             // whitelistText
            0xFF1A1A1A,             // buttonOutline
            0xFF5A5A5A,             // buttonFill
            0xFF6A6A6A,             // buttonFillHover
            0xFF828282,             // buttonLight
            0xFF969696,             // buttonLightHover
            0xFF323232,             // buttonDark
            COLOR_TEXT,             // buttonText
            true                    // buttonTextShadow
    );

    private DarkGuiStyling() {
    }

    public static Palette palette(ClientConfig.UiTheme theme) {
        return theme == ClientConfig.UiTheme.DARK ? PALETTE_DARK : PALETTE_VANILLA;
    }

    public static Palette activePalette() {
        return palette(ClientConfig.getUiTheme());
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        Palette p = activePalette();
        graphics.fill(x, y, x + w, y + h, p.borderDark());
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, p.bevelLight());
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, p.bevelDark());
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, p.bevelDark());
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, p.panelBg());
    }

    public static void drawPanelWithLeftTab(GuiGraphics g, int mx, int my, int mw, int mh,
                                            int tabWidth, int tabTopOffset, int tabHeight) {
        Palette p = activePalette();
        int tx = mx - tabWidth;
        int ty = my + tabTopOffset;
        int tb = ty + tabHeight;

        // Main & tab interior fills
        g.fill(mx, my, mx + mw, my + mh, p.panelBg());
        g.fill(tx, ty, mx, tb, p.panelBg());

        // Dark bottom/right inner bevels
        g.fill(mx + mw - 2, my + 1, mx + mw - 1, my + mh - 1, p.bevelDark());
        g.fill(mx + 1, my + mh - 2, mx + mw - 1, my + mh - 1, p.bevelDark());
        g.fill(tx + 1, tb - 2, mx + 1, tb - 1, p.bevelDark());

        // Light top/left inner bevels
        if (tabTopOffset == 0) {
            // Flush top-left alignment: continuous top bevel across left tab + main panel
            g.fill(tx + 1, my + 1, mx + mw - 1, my + 2, p.bevelLight());
            g.fill(tx + 1, ty + 1, tx + 2, tb - 2, p.bevelLight());
            g.fill(mx + 1, tb - 1, mx + 2, my + mh - 2, p.bevelLight());
        } else {
            g.fill(mx + 1, my + 1, mx + mw - 1, my + 2, p.bevelLight());
            g.fill(mx + 1, my + 1, mx + 2, ty + 2, p.bevelLight());
            g.fill(tx + 1, ty + 1, mx + 2, ty + 2, p.bevelLight());
            g.fill(tx + 1, ty + 1, tx + 2, tb - 2, p.bevelLight());
            g.fill(mx + 1, tb - 1, mx + 2, my + mh - 2, p.bevelLight());
        }

        // Outer 1px dark borders
        if (tabTopOffset == 0) {
            g.fill(tx, my, mx + mw, my + 1, p.borderDark());
        } else {
            g.fill(mx, my, mx + mw, my + 1, p.borderDark());
            g.fill(mx, my, mx + 1, ty + 1, p.borderDark());
            g.fill(tx, ty, mx + 1, ty + 1, p.borderDark());
        }
        g.fill(mx + mw - 1, my, mx + mw, my + mh, p.borderDark());
        g.fill(mx, my + mh - 1, mx + mw, my + mh, p.borderDark());
        g.fill(mx, tb - 1, mx + 1, my + mh, p.borderDark());
        g.fill(tx, ty, tx + 1, tb, p.borderDark());
        g.fill(tx, tb - 1, mx + 1, tb, p.borderDark());
    }

    /**
     * Draws a main panel seamlessly extended downward with an attached horizontal Toolbox bar.
     */
    public static void drawPanelWithBottomTab(GuiGraphics g, int mx, int my, int mw, int mh, int bottomTabH) {
        drawPanel(g, mx, my, mw, mh + bottomTabH);
        drawDivider(g, mx + 6, my + mh - 2, mw - 12);
    }

    /**
     * Draws a main panel with a flush-top left tab AND an attached horizontal bottom Toolbox bar.
     */
    public static void drawPanelWithLeftAndBottomTabs(GuiGraphics g, int mx, int my, int mw, int mh,
                                                      int leftTabW, int leftTabTop, int leftTabH,
                                                      int bottomTabH) {
        drawPanelWithLeftTab(g, mx, my, mw, mh + bottomTabH, leftTabW, leftTabTop, leftTabH);
        drawDivider(g, mx + 6, my + mh - 2, mw - 12);
    }

    public static void drawSlot(GuiGraphics graphics, int itemX, int itemY) {
        drawSlot(graphics, itemX, itemY, activePalette());
    }

    public static void drawDarkSlot(GuiGraphics graphics, int itemX, int itemY) {
        drawSlot(graphics, itemX, itemY, PALETTE_DARK);
    }

    private static void drawSlot(GuiGraphics graphics, int itemX, int itemY, Palette p) {
        int x = itemX - 1;
        int y = itemY - 1;
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, p.slotBg());
        graphics.fill(x, y, x + SLOT_SIZE - 1, y + 1, p.slotBorderDark());
        graphics.fill(x, y, x + 1, y + SLOT_SIZE - 1, p.slotBorderDark());
        graphics.fill(x + 1, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, p.slotBorderLight());
        graphics.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, p.slotBorderLight());
        graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + 1, p.slotCorner());
        graphics.fill(x, y + SLOT_SIZE - 1, x + 1, y + SLOT_SIZE, p.slotCorner());
    }

    public static void drawDivider(GuiGraphics graphics, int x, int y, int w) {
        Palette p = activePalette();
        graphics.fill(x, y, x + w, y + 1, p.bevelDark());
        graphics.fill(x, y + 1, x + w, y + 2, p.bevelLight());
    }

    public static void drawVerticalBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio) {
        Palette p = activePalette();
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, p.bevelLight());
        graphics.fill(x, y, x + w, y + h, p.barBg());
        int filled = Mth.clamp((int) (h * ratio), 0, h);
        if (filled > 0)
            graphics.fill(x, y + h - filled, x + w, y + h, p.barNormal());
    }

    public static void drawHorizontalBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio, int fillColor) {
        Palette p = activePalette();
        graphics.fill(x, y, x + w, y + h, p.barBg());
        int filled = Mth.clamp(Math.round(w * Mth.clamp(ratio, 0.0F, 1.0F)), 0, w);
        if (filled > 0)
            graphics.fill(x, y, x + filled, y + h, fillColor);
    }

    public static void drawGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
        drawGhostItemWithBg(graphics, stack, x, y, alpha, activePalette().slotBg());
    }

    public static void drawDarkGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
        drawGhostItemWithBg(graphics, stack, x, y, alpha, PALETTE_DARK.slotBg());
    }

    private static void drawGhostItemWithBg(GuiGraphics graphics, ItemStack stack, int x, int y,
                                            float alpha, int slotBg) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        graphics.renderItem(stack, x, y);
        int veilAlpha = Math.round((1.0F - clamped) * 255.0F);
        if (veilAlpha > 0) {
            int veilColor = (veilAlpha << 24) | (slotBg & 0x00FFFFFF);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x, y, x + 16, y + 16, veilColor);
            graphics.pose().popPose();
        }
    }

    public static void drawCenteredText(GuiGraphics graphics, Font font, String text,
                                        int centerX, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, dropShadow);
    }

    public static void drawCenteredText(GuiGraphics graphics, Font font, Component text,
                                        int centerX, int y, int color, boolean dropShadow) {
        graphics.drawString(font, text, centerX - font.width(text) / 2, y, color, dropShadow);
    }

    public static void drawCompactCount(GuiGraphics graphics, Font font, int count, int itemX, int itemY) {
        if (count <= 1)
            return;
        String text = String.valueOf(count);
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        if (count >= 100) {
            float scale = 0.72F;
            graphics.pose().scale(scale, scale, 1.0F);
            int drawX = Math.round((itemX + 16) / scale) - font.width(text);
            int drawY = Math.round((itemY + 16) / scale) - 8;
            graphics.drawString(font, text, drawX, drawY, 0xFFFFFFFF, true);
        } else {
            int drawX = itemX + 17 - font.width(text);
            int drawY = itemY + 9;
            graphics.drawString(font, text, drawX, drawY, 0xFFFFFFFF, true);
        }
        graphics.pose().popPose();
    }
}
