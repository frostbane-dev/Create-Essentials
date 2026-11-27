package com.stackultra.essentials.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DarkGuiStyling {

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

    private DarkGuiStyling() {
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_BORDER_DARK);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_BEVEL_LIGHT);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, COLOR_BEVEL_DARK);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, COLOR_BEVEL_DARK);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, COLOR_PANEL);
    }

    public static void drawPanelWithLeftTab(GuiGraphics g, int mx, int my, int mw, int mh,
                                            int tabWidth, int tabTopOffset, int tabHeight) {
        int tx = mx - tabWidth;
        int ty = my + tabTopOffset;
        int tb = ty + tabHeight;

        // Main & tab interior fills
        g.fill(mx, my, mx + mw, my + mh, COLOR_PANEL);
        g.fill(tx, ty, mx, tb, COLOR_PANEL);

        // Dark bottom/right inner bevels
        g.fill(mx + mw - 2, my + 1, mx + mw - 1, my + mh - 1, COLOR_BEVEL_DARK);
        g.fill(mx + 1, my + mh - 2, mx + mw - 1, my + mh - 1, COLOR_BEVEL_DARK);
        g.fill(tx + 1, tb - 2, mx + 1, tb - 1, COLOR_BEVEL_DARK);

        // Light top/left inner bevels
        if (tabTopOffset == 0) {
            // Flush top-left alignment: continuous top bevel across left tab + main panel
            g.fill(tx + 1, my + 1, mx + mw - 1, my + 2, COLOR_BEVEL_LIGHT);
            g.fill(tx + 1, ty + 1, tx + 2, tb - 2, COLOR_BEVEL_LIGHT);
            g.fill(mx + 1, tb - 1, mx + 2, my + mh - 2, COLOR_BEVEL_LIGHT);
        } else {
            g.fill(mx + 1, my + 1, mx + mw - 1, my + 2, COLOR_BEVEL_LIGHT);
            g.fill(mx + 1, my + 1, mx + 2, ty + 2, COLOR_BEVEL_LIGHT);
            g.fill(tx + 1, ty + 1, mx + 2, ty + 2, COLOR_BEVEL_LIGHT);
            g.fill(tx + 1, ty + 1, tx + 2, tb - 2, COLOR_BEVEL_LIGHT);
            g.fill(mx + 1, tb - 1, mx + 2, my + mh - 2, COLOR_BEVEL_LIGHT);
        }

        // Outer 1px dark borders
        if (tabTopOffset == 0) {
            g.fill(tx, my, mx + mw, my + 1, COLOR_BORDER_DARK);
        } else {
            g.fill(mx, my, mx + mw, my + 1, COLOR_BORDER_DARK);
            g.fill(mx, my, mx + 1, ty + 1, COLOR_BORDER_DARK);
            g.fill(tx, ty, mx + 1, ty + 1, COLOR_BORDER_DARK);
        }
        g.fill(mx + mw - 1, my, mx + mw, my + mh, COLOR_BORDER_DARK);
        g.fill(mx, my + mh - 1, mx + mw, my + mh, COLOR_BORDER_DARK);
        g.fill(mx, tb - 1, mx + 1, my + mh, COLOR_BORDER_DARK);
        g.fill(tx, ty, tx + 1, tb, COLOR_BORDER_DARK);
        g.fill(tx, tb - 1, mx + 1, tb, COLOR_BORDER_DARK);
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
        int x = itemX - 1;
        int y = itemY - 1;
        graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT);
        graphics.fill(x, y, x + SLOT_SIZE - 1, y + 1, COLOR_SLOT_BORDER_DARK);
        graphics.fill(x, y, x + 1, y + SLOT_SIZE - 1, COLOR_SLOT_BORDER_DARK);
        graphics.fill(x + 1, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BORDER);
        graphics.fill(x + SLOT_SIZE - 1, y + 1, x + SLOT_SIZE, y + SLOT_SIZE, COLOR_SLOT_BORDER);
        graphics.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + 1, COLOR_SLOT_CORNER);
        graphics.fill(x, y + SLOT_SIZE - 1, x + 1, y + SLOT_SIZE, COLOR_SLOT_CORNER);
    }

    public static void drawDivider(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BEVEL_DARK);
        graphics.fill(x, y + 1, x + w, y + 2, COLOR_BEVEL_LIGHT);
    }

    public static void drawVerticalBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_BORDER);
        graphics.fill(x, y, x + w, y + h, COLOR_BAR_BG);
        int filled = Mth.clamp((int) (h * ratio), 0, h);
        if (filled > 0)
            graphics.fill(x, y + h - filled, x + w, y + h, COLOR_BAR);
    }

    public static void drawHorizontalBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio, int fillColor) {
        graphics.fill(x, y, x + w, y + h, COLOR_BAR_BG);
        int filled = Mth.clamp(Math.round(w * Mth.clamp(ratio, 0.0F, 1.0F)), 0, w);
        if (filled > 0)
            graphics.fill(x, y, x + filled, y + h, fillColor);
    }

    public static void drawGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        graphics.renderItem(stack, x, y);
        int veilAlpha = Math.round((1.0F - clamped) * 255.0F);
        if (veilAlpha > 0) {
            int veilColor = (veilAlpha << 24) | (COLOR_SLOT & 0x00FFFFFF);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x, y, x + 16, y + 16, veilColor);
            graphics.pose().popPose();
        }
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
