package com.stackultra.essentials.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Supplier;

@OnlyIn(Dist.CLIENT)
public class DarkTextButton extends AbstractWidget {

    private static final int COLOR_OUTLINE = 0xFF1A1A1A;
    private static final int COLOR_FILL = 0xFF5A5A5A;
    private static final int COLOR_FILL_HOVER = 0xFF6A6A6A;
    private static final int COLOR_LIGHT = 0xFF828282;
    private static final int COLOR_LIGHT_HOVER = 0xFF969696;
    private static final int COLOR_DARK = 0xFF323232;

    private final Supplier<Component> labelSupplier;
    private final Supplier<Integer> colorSupplier;
    private final Runnable onPress;

    public DarkTextButton(int x, int y, int width, int height,
                          Supplier<Component> labelSupplier,
                          Supplier<Integer> colorSupplier,
                          Runnable onPress) {
        super(x, y, width, height, labelSupplier.get());
        this.labelSupplier = labelSupplier;
        this.colorSupplier = colorSupplier;
        this.onPress = onPress;
    }

    @Override
    public Component getMessage() {
        return labelSupplier.get();
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
        int base = hovered ? COLOR_FILL_HOVER : COLOR_FILL;
        int light = hovered ? COLOR_LIGHT_HOVER : COLOR_LIGHT;

        graphics.fill(x, y, x + w, y + h, COLOR_OUTLINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, light);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, COLOR_DARK);
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, COLOR_DARK);
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, base);

        Component label = labelSupplier.get();
        var font = Minecraft.getInstance().font;
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, label, textX, textY, colorSupplier.get(), true);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
