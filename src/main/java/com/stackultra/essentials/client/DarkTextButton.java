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
        int base = isHovered ? 0xFF5A5A5A : 0xFF4C4C4C;

        graphics.fill(x, y, x + w, y + h, base);
        graphics.fill(x, y, x + w, y + 1, 0xFF7A7A7A);
        graphics.fill(x, y, x + 1, y + h, 0xFF7A7A7A);
        graphics.fill(x, y + h - 1, x + w, y + h, 0xFF2B2B2B);
        graphics.fill(x + w - 1, y, x + w, y + h, 0xFF2B2B2B);

        Component label = labelSupplier.get();
        var font = Minecraft.getInstance().font;
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, label, textX, textY, colorSupplier.get(), false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
