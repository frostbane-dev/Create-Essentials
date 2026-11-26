package com.stackultra.essentials.content.magnet;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.network.BlacklistTogglePayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
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

    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 192;

    private static final int TITLE_Y = 7;
    private static final int BUTTON_Y = 20;
    private static final int BUTTON_W = 78;
    private static final int BUTTON_H = 18;
    private static final int FILTER_LABEL_Y = 44;
    private static final int TIMER_Y = 80;
    private static final int DIVIDER_Y = 94;

    private static final int COLOR_BLACKLIST = 0xFFD66BFF;
    private static final int COLOR_WHITELIST = 0xFF78B8FF;

    private boolean enabledState;
    private boolean blacklistState;

    public RotationalMagnetScreen(RotationalMagnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();

        enabledState = menu.isEnabled();
        blacklistState = readBlacklist();

        DarkTextButton toggle = new DarkTextButton(leftPos + 8, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> Component.translatable("createessentials.gui.magnet_status",
                        Component.translatable(enabledState ? "createessentials.gui.on" : "createessentials.gui.off")
                                .withStyle(enabledState ? ChatFormatting.GREEN : ChatFormatting.RED)),
                () -> DarkGuiStyling.COLOR_TEXT,
                () -> {
                    enabledState = !enabledState;
                    menu.setEnabledClient(enabledState);
                    PacketDistributor.sendToServer(togglePayload());
                });

        DarkTextButton blacklist = new DarkTextButton(leftPos + 90, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> Component.translatable(blacklistState
                        ? "createessentials.gui.blacklist" : "createessentials.gui.whitelist"),
                () -> blacklistState ? COLOR_BLACKLIST : COLOR_WHITELIST,
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
        if (this.minecraft == null || this.minecraft.player == null)
            return false;
        ItemStack magnet = MagnetEquipment.findEquippedMagnet(this.minecraft.player);
        return !magnet.isEmpty() && magnet.getOrDefault(ExtraDataComponents.BLACKLIST, false);
    }

    private int currentCharge() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        if (be != null)
            return menu.getCharge();
        if (this.minecraft == null || this.minecraft.player == null)
            return menu.getCharge();
        ItemStack magnet = MagnetEquipment.findEquippedMagnet(this.minecraft.player);
        if (!magnet.isEmpty() && magnet.getItem() instanceof RotationalMagnetItem item)
            return item.getCharge(magnet);
        return menu.getCharge();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        DarkGuiStyling.drawDivider(graphics, leftPos + 6, topPos + DIVIDER_Y, imageWidth - 12);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        ItemStack ghost = AllItems.FILTER.asStack();
        for (int i = 0; i < RotationalMagnetMenu.FILTER_SLOTS; i++) {
            Slot slot = menu.slots.get(i);
            if (slot.hasItem())
                continue;
            DarkGuiStyling.drawGhostItem(graphics, ghost, leftPos + slot.x, topPos + slot.y,
                    DarkGuiStyling.FILTER_GHOST_ALPHA);
        }
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        int maxTitle = imageWidth - 16;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawCenteredString(font, titleText, leftPos + imageWidth / 2, topPos + TITLE_Y,
                DarkGuiStyling.COLOR_TITLE);

        Component filterHeader = Component.translatable("createessentials.gui.item_filter");
        graphics.drawCenteredString(font, filterHeader, leftPos + imageWidth / 2, topPos + FILTER_LABEL_Y,
                DarkGuiStyling.COLOR_TITLE);

        int seconds = Math.max(0, currentCharge()) / 20;
        String remaining = String.format("%02dm %02ds", seconds / 60, seconds % 60);
        Component timeText = Component.translatable("createessentials.gui.time_remaining", remaining);
        graphics.drawCenteredString(font, timeText, leftPos + imageWidth / 2, topPos + TIMER_Y,
                DarkGuiStyling.COLOR_TEXT);

        int playerStart = menu.slots.size() - 36;
        int playerLabelY = menu.slots.get(playerStart).y - 11;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerLabelY,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
