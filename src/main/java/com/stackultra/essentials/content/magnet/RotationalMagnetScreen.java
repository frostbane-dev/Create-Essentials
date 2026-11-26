package com.stackultra.essentials.content.magnet;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.GuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RotationalMagnetScreen extends AbstractSimiContainerScreen<RotationalMagnetMenu> {

    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int FILTER_SLOTS_Y = 22;
    private static final int FILTER_SLOTS_X = 43;
    private static final int PLAYER_INVENTORY_Y = 84;
    private static final int HOTBAR_Y = 142;

    private static final int FILTER_STRIP_X = 41;
    private static final int FILTER_STRIP_Y = 20;
    private static final int FILTER_STRIP_W = 94;
    private static final int FILTER_STRIP_H = 22;

    public RotationalMagnetScreen(RotationalMagnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(176, 166);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        drawPanel(graphics);

        GuiStyling.drawInset(graphics, leftPos + FILTER_STRIP_X, topPos + FILTER_STRIP_Y,
                FILTER_STRIP_W, FILTER_STRIP_H);

        for (int i = 0; i < RotationalMagnetMenu.FILTER_SLOTS; i++)
            graphics.blitSprite(SLOT_SPRITE, leftPos + FILTER_SLOTS_X + i * 18 - 1, topPos + FILTER_SLOTS_Y - 1, 18, 18);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(SLOT_SPRITE, leftPos + 8 + col * 18 - 1, topPos + PLAYER_INVENTORY_Y + row * 18 - 1, 18, 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            graphics.blitSprite(SLOT_SPRITE, leftPos + 8 + col * 18 - 1, topPos + HOTBAR_Y - 1, 18, 18);
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        GuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyling.drawDivider(graphics, leftPos + 3, topPos + 16, imageWidth - 6);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        if (font.width(titleText) > 140)
            titleText = font.plainSubstrByWidth(titleText, 140 - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, GuiStyling.COLOR_TEXT, false);

        boolean enabled = menu.isEnabled();
        Component status = Component.translatable(enabled
                ? "createessentials.gui.enabled"
                : "createessentials.gui.disabled");
        graphics.drawString(font, status, leftPos + 8, topPos + 46, enabled ? 0x2E7D32 : 0xC62828, false);

        Component charge = Component.translatable("createessentials.tooltip.remaining",
                MagnetTier.formatRemaining(menu.getCharge()));
        graphics.drawString(font, charge, leftPos + 62, topPos + 46, GuiStyling.COLOR_TEXT, false);

        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + 70, GuiStyling.COLOR_TEXT, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
