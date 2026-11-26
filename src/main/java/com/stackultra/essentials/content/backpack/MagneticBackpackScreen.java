package com.stackultra.essentials.content.backpack;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.GuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackScreen extends AbstractSimiContainerScreen<MagneticBackpackMenu> {

    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int PANEL_WIDTH = 30;
    private static final int FILTER_X = 6;
    private static final int FILTER_Y = 18;
    private static final int STORAGE_X = 8 + PANEL_WIDTH;
    private static final int STORAGE_Y = 18;
    private static final int PLAYER_Y = 85;
    private static final int HOTBAR_Y = 143;
    private static final int LABEL_Y = 73;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(176 + PANEL_WIDTH, 168);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        drawPanel(graphics);

        for (int i = 0; i < MagneticBackpackMenu.FILTER_SLOTS; i++)
            graphics.blitSprite(SLOT_SPRITE, leftPos + FILTER_X - 1, topPos + FILTER_Y + i * 18 - 1, 18, 18);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + STORAGE_Y + row * 18 - 1, 18, 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + PLAYER_Y + row * 18 - 1, 18, 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + HOTBAR_Y - 1, 18, 18);
        }
    }

    private void drawPanel(GuiGraphics graphics) {
        GuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        GuiStyling.drawDivider(graphics, leftPos + 3, topPos + 16, imageWidth - 6);
        GuiStyling.drawInset(graphics, leftPos + 4, topPos + FILTER_Y - 2,
                PANEL_WIDTH - 8, MagneticBackpackMenu.FILTER_SLOTS * 18 + 4);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        if (font.width(titleText) > imageWidth - 32)
            titleText = font.plainSubstrByWidth(titleText, imageWidth - 32 - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, GuiStyling.COLOR_TEXT, false);

        graphics.drawString(font, playerInventoryTitle, leftPos + STORAGE_X, topPos + LABEL_Y, GuiStyling.COLOR_TEXT, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
