package com.stackultra.essentials.client;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class GuiStyling {

    public static final int COLOR_BORDER = 0xFF2A2A2A;
    public static final int COLOR_BEVEL_LIGHT = 0xFFEDEDED;
    public static final int COLOR_BEVEL_DARK = 0xFF8B8B8B;
    public static final int COLOR_PANEL = 0xFFC6C6C6;
    public static final int COLOR_INSET = 0xFFB4B4B4;
    public static final int COLOR_TEXT = 0xFF3F3F3F;

    private GuiStyling() {
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_BEVEL_LIGHT);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, COLOR_BEVEL_DARK);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, COLOR_BEVEL_DARK);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, COLOR_PANEL);
    }

    public static void drawInset(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_BEVEL_DARK);
        graphics.fill(x + 1, y + 1, x + w, y + h, COLOR_BEVEL_LIGHT);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, COLOR_INSET);
    }

    public static void drawDivider(GuiGraphics graphics, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 1, COLOR_BEVEL_DARK);
        graphics.fill(x, y + 1, x + w, y + 2, COLOR_BEVEL_LIGHT);
    }
}
