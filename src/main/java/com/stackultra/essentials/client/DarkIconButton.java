package com.stackultra.essentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class DarkIconButton extends AbstractWidget {

    public enum Icon {
        TRANSFER_TO_STORAGE,
        TRANSFER_TO_INVENTORY,
        SORT,
        TEXT_BADGE
    }

    private static final int COLOR_OUTLINE = 0xFF1A1A1A;
    private static final int COLOR_FILL = 0xFF5A5A5A;
    private static final int COLOR_FILL_HOVER = 0xFF6A6A6A;
    private static final int COLOR_LIGHT = 0xFF828282;
    private static final int COLOR_LIGHT_HOVER = 0xFF969696;
    private static final int COLOR_DARK = 0xFF323232;

    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final int COLOR_GREEN_SHADOW = 0xFF1A421A;
    private static final int COLOR_RED = 0xFFFF5555;
    private static final int COLOR_RED_SHADOW = 0xFF4D1818;
    private static final int COLOR_GOLD = 0xFFF2C24C;
    private static final int COLOR_GOLD_SHADOW = 0xFF453610;

    private final Icon icon;
    private final Supplier<String> badgeSupplier;
    private final Supplier<List<Component>> tooltipSupplier;
    private final Runnable onPress;

    public DarkIconButton(int x, int y, int size,
                          Icon icon,
                          Supplier<String> badgeSupplier,
                          Supplier<List<Component>> tooltipSupplier,
                          Runnable onPress) {
        super(x, y, size, size, Component.empty());
        this.icon = icon;
        this.badgeSupplier = badgeSupplier;
        this.tooltipSupplier = tooltipSupplier;
        this.onPress = onPress;
    }

    public List<Component> getTooltipLines() {
        return tooltipSupplier.get();
    }

    @Override
    public Component getMessage() {
        List<Component> lines = tooltipSupplier.get();
        return lines.isEmpty() ? Component.empty() : lines.get(0);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        onPress.run();
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused();
        int fill = hovered ? COLOR_FILL_HOVER : COLOR_FILL;
        int light = hovered ? COLOR_LIGHT_HOVER : COLOR_LIGHT;

        graphics.fill(x, y, x + w, y + h, COLOR_OUTLINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, light);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, COLOR_DARK);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, COLOR_DARK);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);

        int ix = x + 2;
        int iy = y + 2;

        switch (icon) {
            case TRANSFER_TO_STORAGE -> drawUpArrow(graphics, ix, iy);
            case TRANSFER_TO_INVENTORY -> drawDownArrow(graphics, ix, iy);
            case SORT -> drawSortBars(graphics, ix, iy);
            case TEXT_BADGE -> {
                String badge = badgeSupplier.get();
                Font font = Minecraft.getInstance().font;
                int tx = x + (w - font.width(badge)) / 2;
                int ty = y + (h - 7) / 2;
                graphics.drawString(font, badge, tx, ty, COLOR_GREEN, true);
            }
        }
    }

    private static void drawUpArrow(GuiGraphics g, int ix, int iy) {
        // Shadow
        drawUpArrowShape(g, ix + 1, iy + 1, COLOR_GREEN_SHADOW);
        // Foreground
        drawUpArrowShape(g, ix, iy, COLOR_GREEN);
    }

    private static void drawUpArrowShape(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 1, x + 5, y + 2, color);
        g.fill(x + 2, y + 2, x + 6, y + 3, color);
        g.fill(x + 1, y + 3, x + 7, y + 4, color);
        g.fill(x + 3, y + 4, x + 5, y + 7, color);
    }

    private static void drawDownArrow(GuiGraphics g, int ix, int iy) {
        // Shadow
        drawDownArrowShape(g, ix + 1, iy + 1, COLOR_RED_SHADOW);
        // Foreground
        drawDownArrowShape(g, ix, iy, COLOR_RED);
    }

    private static void drawDownArrowShape(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 3, y + 1, x + 5, y + 4, color);
        g.fill(x + 1, y + 4, x + 7, y + 5, color);
        g.fill(x + 2, y + 5, x + 6, y + 6, color);
        g.fill(x + 3, y + 6, x + 5, y + 7, color);
    }

    private static void drawSortBars(GuiGraphics g, int ix, int iy) {
        drawSortBarsShape(g, ix + 1, iy + 1, COLOR_GOLD_SHADOW);
        drawSortBarsShape(g, ix, iy, COLOR_GOLD);
    }

    private static void drawSortBarsShape(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y + 1, x + 7, y + 2, color);
        g.fill(x + 1, y + 3, x + 5, y + 4, color);
        g.fill(x + 1, y + 5, x + 3, y + 6, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
