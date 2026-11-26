package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackScreen extends AbstractSimiContainerScreen<MagneticBackpackMenu> {

    private static final float GHOST_ALPHA = 1.0F;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(204, 178);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        ItemStack ghost = AllItems.FILTER.asStack();
        for (int i = 0; i < MagneticBackpackMenu.FILTER_SLOTS; i++) {
            Slot slot = menu.slots.get(MagneticBackpackMenu.STORAGE_SLOTS + i);
            if (slot.hasItem())
                continue;
            DarkGuiStyling.drawGhostItem(graphics, ghost, leftPos + slot.x, topPos + slot.y, GHOST_ALPHA);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        int maxTitle = imageWidth - 16;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, DarkGuiStyling.COLOR_TITLE, false);

        Slot playerSlot = menu.slots.get(MagneticBackpackMenu.PLAYER_SLOT_START);
        graphics.drawString(font, playerInventoryTitle, leftPos + playerSlot.x, topPos + playerSlot.y - 12,
                DarkGuiStyling.COLOR_TEXT_DIM, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
