package com.stackultra.essentials.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DarkGuiStyling {

    public static final int COLOR_PANEL = 0xFF373737;
    public static final int COLOR_BORDER = 0xFF8B8B8B;
    public static final int COLOR_BORDER_DARK = 0xFF151515;
    public static final int COLOR_SLOT = 0xFF1E1E1E;
    public static final int COLOR_SLOT_BORDER = 0xFF5A5A5A;
    public static final int COLOR_TEXT = 0xFFE6E6E6;
    public static final int COLOR_TEXT_DIM = 0xFFA8A8A8;
    public static final int COLOR_TITLE = 0xFFFFFF55;
    public static final int COLOR_BAR_BG = 0xFF101010;
    public static final int COLOR_BAR = 0xFF7FD7FF;
    public static final int SLOT_SIZE = 18;

    private DarkGuiStyling() {
    }

    public static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, COLOR_PANEL);
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);
    }

    public static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, COLOR_SLOT_BORDER);
        graphics.fill(x, y, x + 16, y + 16, COLOR_SLOT);
    }

    public static void drawVerticalBar(GuiGraphics graphics, int x, int y, int w, int h, float ratio) {
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COLOR_BORDER);
        graphics.fill(x, y, x + w, y + h, COLOR_BAR_BG);
        int filled = Mth.clamp((int) (h * ratio), 0, h);
        if (filled > 0)
            graphics.fill(x, y + h - filled, x + w, y + h, COLOR_BAR);
    }

    public static void drawGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.renderItem(stack, x, y);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
        graphics.pose().popPose();
    }
}
