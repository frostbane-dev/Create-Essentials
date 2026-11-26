package com.stackultra.essentials.content.backpack;

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
public class MagneticBackpackScreen extends AbstractSimiContainerScreen<MagneticBackpackMenu> {

    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 191;

    private static final int TITLE_Y = 6;
    private static final int BUTTON_Y = 19;
    private static final int BUTTON_W = 78;
    private static final int BUTTON_H = 18;

    private static final int TAB_WIDTH = 25;
    private static final int TAB_TOP = 16;
    private static final int TAB_HEIGHT = 62;

    private static final int COLOR_BLACKLIST = 0xFFD66BFF;
    private static final int COLOR_WHITELIST = 0xFF78B8FF;

    private boolean enabledState;
    private boolean blacklistState;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
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
        MagneticBackpackBlockEntity be = menu.getBlockEntity();
        return be != null
                ? new RotationalMagnetTogglePayload(be.getBlockPos(), false)
                : new RotationalMagnetTogglePayload(BlockPos.ZERO, true);
    }

    private BlacklistTogglePayload blacklistPayload() {
        MagneticBackpackBlockEntity be = menu.getBlockEntity();
        return be != null
                ? new BlacklistTogglePayload(be.getBlockPos(), false)
                : new BlacklistTogglePayload(BlockPos.ZERO, true);
    }

    private boolean readBlacklist() {
        MagneticBackpackBlockEntity be = menu.getBlockEntity();
        if (be != null)
            return be.isBlacklist();
        if (this.minecraft == null || this.minecraft.player == null)
            return false;
        ItemStack backpack = MagneticBackpackEquipment.findCarriedMagneticBackpack(this.minecraft.player);
        return !backpack.isEmpty() && backpack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean inLeftTab = mouseX >= guiLeft - TAB_WIDTH && mouseX < guiLeft
                && mouseY >= guiTop + TAB_TOP && mouseY < guiTop + TAB_TOP + TAB_HEIGHT;
        return !inLeftTab && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanelWithLeftTab(graphics, leftPos, topPos, imageWidth, imageHeight,
                TAB_WIDTH, TAB_TOP, TAB_HEIGHT);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        ItemStack ghost = AllItems.FILTER.asStack();
        for (int i = 0; i < MagneticBackpackMenu.FILTER_SLOTS; i++) {
            Slot slot = menu.slots.get(MagneticBackpackMenu.STORAGE_SLOTS + i);
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
        graphics.drawString(font, titleText, leftPos + 8, topPos + TITLE_Y, DarkGuiStyling.COLOR_TITLE, true);

        Slot playerSlot = menu.slots.get(MagneticBackpackMenu.PLAYER_SLOT_START);
        graphics.drawString(font, playerInventoryTitle, leftPos + playerSlot.x, topPos + playerSlot.y - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
