package com.stackultra.essentials.content.magnet;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.client.GuiStyling;
import com.stackultra.essentials.network.BlacklistTogglePayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

@OnlyIn(Dist.CLIENT)
public class RotationalMagnetScreen extends AbstractSimiContainerScreen<RotationalMagnetMenu> {

    private boolean enabledState;
    private boolean blacklistState;

    public RotationalMagnetScreen(RotationalMagnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(176, 178);
        super.init();

        enabledState = menu.isEnabled();
        blacklistState = readBlacklist();

        DarkTextButton toggle = new DarkTextButton(leftPos + 8, topPos + 18, 60, 14,
                () -> Component.translatable("createessentials.gui.magnet_status",
                        Component.translatable(enabledState ? "createessentials.gui.on" : "createessentials.gui.off")),
                () -> enabledState ? 0xFF55FF55 : 0xFFFF5555,
                () -> {
                    enabledState = !enabledState;
                    PacketDistributor.sendToServer(togglePayload());
                });

        DarkTextButton blacklist = new DarkTextButton(leftPos + 74, topPos + 18, 60, 14,
                () -> Component.translatable(blacklistState
                        ? "createessentials.gui.blacklist" : "createessentials.gui.whitelist"),
                () -> blacklistState ? 0xFFAA00AA : 0xFF5555FF,
                () -> {
                    blacklistState = !blacklistState;
                    PacketDistributor.sendToServer(blacklistPayload());
                });

        addRenderableWidget(toggle);
        addRenderableWidget(blacklist);
    }

    private RotationalMagnetTogglePayload togglePayload() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        return be != null
                ? new RotationalMagnetTogglePayload(be.getBlockPos(), false)
                : new RotationalMagnetTogglePayload(BlockPos.ZERO, true);
    }

    private BlacklistTogglePayload blacklistPayload() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        return be != null
                ? new BlacklistTogglePayload(be.getBlockPos(), false)
                : new BlacklistTogglePayload(BlockPos.ZERO, true);
    }

    private boolean readBlacklist() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        if (be != null)
            return be.isBlacklist();
        ItemStack magnet = MagnetEquipment.findEquippedMagnet(this.minecraft.player);
        return !magnet.isEmpty() && magnet.getOrDefault(ExtraDataComponents.BLACKLIST, false);
    }

    private int currentCharge() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        if (be != null)
            return menu.getCharge();
        ItemStack magnet = MagnetEquipment.findEquippedMagnet(this.minecraft.player);
        if (!magnet.isEmpty() && magnet.getItem() instanceof RotationalMagnetItem item)
            return item.getCharge(magnet);
        return menu.getCharge();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        ItemStack ghost = AllItems.FILTER.asStack();
        for (int i = 0; i < RotationalMagnetMenu.FILTER_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem())
                continue;
            GuiStyling.drawGhostItem(graphics, ghost, leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        int maxTitle = imageWidth - 16;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, DarkGuiStyling.COLOR_TITLE, false);

        graphics.drawString(font, Component.translatable("createessentials.gui.item_filter"),
                leftPos + 8, topPos + 38, DarkGuiStyling.COLOR_TITLE, false);

        int playerStart = menu.slots.size() - 36;
        int playerLabelY = menu.slots.get(playerStart).y - 12;

        int seconds = Math.max(0, currentCharge()) / 20;
        String remaining = String.format("%02dm %02ds", seconds / 60, seconds % 60);
        Component timeText = Component.translatable("createessentials.gui.time_remaining", remaining);
        graphics.drawString(font, timeText, leftPos + 8, topPos + 72, DarkGuiStyling.COLOR_TEXT_DIM, false);

        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerLabelY,
                DarkGuiStyling.COLOR_TEXT_DIM, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
