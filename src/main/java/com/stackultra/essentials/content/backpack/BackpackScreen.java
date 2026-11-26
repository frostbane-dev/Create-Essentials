package com.stackultra.essentials.content.backpack;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BackpackScreen extends AbstractSimiContainerScreen<BackpackMenu> {

    public BackpackScreen(BackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        int maxY = 0;
        for (Slot slot : menu.slots)
            maxY = Math.max(maxY, slot.y);
        setWindowSize(176, maxY + 28);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        int maxTitle = imageWidth - 16;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 5, DarkGuiStyling.COLOR_TEXT, false);

        int playerStart = menu.slots.size() - 36;
        int playerY = menu.slots.get(playerStart).y;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerY - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
