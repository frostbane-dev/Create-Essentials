package com.stackultra.essentials.content.magnet;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.GuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class RotationalMagnetScreen extends AbstractSimiContainerScreen<RotationalMagnetMenu> {

    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int FILTER_SLOTS_Y = 20;
    private static final int FILTER_SLOTS_X = 8;
    private static final int PLAYER_INVENTORY_Y = 76;
    private static final int HOTBAR_Y = 134;

    public RotationalMagnetScreen(RotationalMagnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(176, 158);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        drawPanel(graphics);

        for (int i = 0; i < RotationalMagnetMenu.FILTER_SLOTS; i++)
            graphics.blitSprite(SLOT_SPRITE, leftPos + FILTER_SLOTS_X + i * 18 - 1, topPos + FILTER_SLOTS_Y - 1, 18, 18);

        ItemStack ghostFilter = AllItems.FILTER.asStack();
        for (int i = 0; i < RotationalMagnetMenu.FILTER_SLOTS; i++) {
            if (menu.slots.get(i).hasItem())
                continue;
            GuiStyling.drawGhostItem(graphics, ghostFilter, leftPos + FILTER_SLOTS_X + i * 18, topPos + FILTER_SLOTS_Y);
        }

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
        graphics.drawString(font, status, leftPos + 8, topPos + 44, enabled ? 0x2E7D32 : 0xC62828, false);

        Component charge = Component.translatable("createessentials.tooltip.remaining",
                MagnetTier.formatRemaining(menu.getCharge()));
        graphics.drawString(font, charge, leftPos + 62, topPos + 44, GuiStyling.COLOR_TEXT, false);

        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + 62, GuiStyling.COLOR_TEXT, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
