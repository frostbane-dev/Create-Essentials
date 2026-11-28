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
        DarkGuiStyling.Palette p = DarkGuiStyling.activePalette();
        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHoveredOrFocused();
        int base = hovered ? p.buttonFillHover() : p.buttonFill();
        int light = hovered ? p.buttonLightHover() : p.buttonLight();

        graphics.fill(x, y, x + w, y + h, p.buttonOutline());
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, light);
        graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, p.buttonDark());
        graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, p.buttonDark());
        graphics.fill(x + 2, y + 2, x + w - 2, y + h - 2, base);

        Component label = labelSupplier.get();
        var font = Minecraft.getInstance().font;
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - 8) / 2;
        graphics.drawString(font, label, textX, textY, colorSupplier.get(), p.buttonTextShadow());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
