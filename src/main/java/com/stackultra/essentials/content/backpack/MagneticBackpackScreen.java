package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkIconButton;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.network.BackpackActionPayload;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import com.stackultra.essentials.network.BlacklistTogglePayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackScreen extends AbstractSimiContainerScreen<MagneticBackpackMenu> {

    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 191;
    private static final int BOTTOM_TAB_HEIGHT = 42;

    private static final int TITLE_Y = 6;
    private static final int UTILITY_BTN_SIZE = 12;
    private static final int UTILITY_BTN_GAP = 1;
    private static final int UTILITY_BTN_Y = 4;
    private static final int UTILITY_START_X = 169 - (UTILITY_BTN_SIZE * 4 + UTILITY_BTN_GAP * 3);

    private static final int BUTTON_Y = 19;
    private static final int BUTTON_W = 78;
    private static final int BUTTON_H = 18;

    // Left 3-slot filter panel aligned flush with the top-left corner of the main window
    private static final int LEFT_TAB_WIDTH = 25;
    private static final int LEFT_TAB_TOP = 0;
    private static final int LEFT_TAB_HEIGHT = 62;

    private static final int TOOLBOX_TITLE_X = 8;
    private static final int TOOLBOX_TITLE_Y = WINDOW_HEIGHT + 5;
    private static final int RESTOCK_BTN_X = 92;
    private static final int RESTOCK_BTN_Y = WINDOW_HEIGHT + 2;
    private static final int RESTOCK_BTN_W = 76;
    private static final int RESTOCK_BTN_H = 14;

    private static final int COLOR_BLACKLIST = 0xFFD66BFF;
    private static final int COLOR_WHITELIST = 0xFF78B8FF;

    private final List<DarkIconButton> utilityButtons = new ArrayList<>(4);
    private DarkTextButton restockButton;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();
        // Center main window + bottom toolbox bar cleanly on screen
        this.topPos = Math.max(4, (this.height - (WINDOW_HEIGHT + BOTTOM_TAB_HEIGHT)) / 2);

        utilityButtons.clear();
        int step = UTILITY_BTN_SIZE + UTILITY_BTN_GAP;
        int bx = leftPos + UTILITY_START_X;
        int by = topPos + UTILITY_BTN_Y;

        DarkIconButton toStorage = new DarkIconButton(bx, by, UTILITY_BTN_SIZE,
                DarkIconButton.Icon.TRANSFER_TO_STORAGE,
                () -> "",
                () -> List.of(Component.translatable("createessentials.gui.transfer_to_storage")
                        .withStyle(ChatFormatting.GREEN)),
                () -> PacketDistributor.sendToServer(BackpackActionPayload.transferToStorage()));

        DarkIconButton toInventory = new DarkIconButton(bx + step, by, UTILITY_BTN_SIZE,
                DarkIconButton.Icon.TRANSFER_TO_INVENTORY,
                () -> "",
                () -> List.of(Component.translatable("createessentials.gui.transfer_to_inventory")
                        .withStyle(ChatFormatting.RED)),
                () -> PacketDistributor.sendToServer(BackpackActionPayload.transferToInventory()));

        DarkIconButton sortBtn = new DarkIconButton(bx + step * 2, by, UTILITY_BTN_SIZE,
                DarkIconButton.Icon.SORT,
                () -> "",
                () -> List.of(
                        Component.translatable("createessentials.gui.sort").withStyle(ChatFormatting.GOLD),
                        Component.translatable("createessentials.gui.sort_by",
                                Component.translatable(menu.getSortMode().translationKey()))
                                .withStyle(ChatFormatting.GRAY)),
                () -> PacketDistributor.sendToServer(BackpackActionPayload.sort()));

        DarkIconButton sortModeBtn = new DarkIconButton(bx + step * 3, by, UTILITY_BTN_SIZE,
                DarkIconButton.Icon.TEXT_BADGE,
                () -> menu.getSortMode().shortBadge(),
                () -> List.of(
                        Component.translatable("createessentials.gui.sort_by",
                                Component.translatable(menu.getSortMode().translationKey())
                                        .withStyle(ChatFormatting.GREEN)),
                        Component.translatable("createessentials.gui.sort_mode_hint")
                                .withStyle(ChatFormatting.GRAY)),
                () -> PacketDistributor.sendToServer(BackpackActionPayload.setSortMode(menu.getSortMode().next())));

        utilityButtons.add(toStorage);
        utilityButtons.add(toInventory);
        utilityButtons.add(sortBtn);
        utilityButtons.add(sortModeBtn);
        for (DarkIconButton btn : utilityButtons)
            addRenderableWidget(btn);

        DarkTextButton toggle = new DarkTextButton(leftPos + 8, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    boolean enabled = menu.isEnabled();
                    return Component.translatable("createessentials.gui.magnet_status",
                            Component.translatable(enabled ? "createessentials.gui.on" : "createessentials.gui.off")
                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
                },
                () -> DarkGuiStyling.COLOR_TEXT,
                () -> {
                    menu.setEnabledClient(!menu.isEnabled());
                    PacketDistributor.sendToServer(togglePayload());
                });

        DarkTextButton blacklist = new DarkTextButton(leftPos + 90, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> Component.translatable(menu.isBlacklist()
                        ? "createessentials.gui.blacklist" : "createessentials.gui.whitelist"),
                () -> menu.isBlacklist() ? COLOR_BLACKLIST : COLOR_WHITELIST,
                () -> {
                    menu.setBlacklistClient(!menu.isBlacklist());
                    PacketDistributor.sendToServer(blacklistPayload());
                });

        addRenderableWidget(toggle);
        addRenderableWidget(blacklist);

        restockButton = new DarkTextButton(
                leftPos + RESTOCK_BTN_X, topPos + RESTOCK_BTN_Y, RESTOCK_BTN_W, RESTOCK_BTN_H,
                () -> {
                    boolean on = menu.isRestockEnabled();
                    return Component.translatable("createessentials.gui.restock_btn",
                            Component.translatable(on ? "createessentials.gui.on" : "createessentials.gui.off")
                                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
                },
                () -> DarkGuiStyling.COLOR_TEXT,
                () -> PacketDistributor.sendToServer(BackpackToolboxPayload.toggleRestock()));
        addRenderableWidget(restockButton);
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

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean inLeftTab = mouseX >= guiLeft - LEFT_TAB_WIDTH && mouseX < guiLeft
                && mouseY >= guiTop + LEFT_TAB_TOP && mouseY < guiTop + LEFT_TAB_TOP + LEFT_TAB_HEIGHT;
        boolean inBottomTab = mouseX >= guiLeft && mouseX < guiLeft + imageWidth
                && mouseY >= guiTop + imageHeight && mouseY < guiTop + imageHeight + BOTTOM_TAB_HEIGHT;
        return !inLeftTab && !inBottomTab && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(
                new Rect2i(leftPos - LEFT_TAB_WIDTH, topPos + LEFT_TAB_TOP, LEFT_TAB_WIDTH, LEFT_TAB_HEIGHT),
                new Rect2i(leftPos, topPos + imageHeight, imageWidth, BOTTOM_TAB_HEIGHT));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanelWithLeftAndBottomTabs(graphics, leftPos, topPos, imageWidth, imageHeight,
                LEFT_TAB_WIDTH, LEFT_TAB_TOP, LEFT_TAB_HEIGHT, BOTTOM_TAB_HEIGHT);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        // Left-side magnet filter ghost hints
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
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (menu.isToolboxSlot(slot.index)) {
            int compartment = slot.index - MagneticBackpackMenu.TOOLBOX_SLOT_START;
            int total = menu.getToolboxCount(compartment);
            if (!stack.isEmpty() && total > 0) {
                graphics.renderItem(stack, slot.x, slot.y);
                graphics.renderItemDecorations(font, stack, slot.x, slot.y, "");
                DarkGuiStyling.drawCompactCount(graphics, font, total, slot.x, slot.y);
            }
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        int maxTitle = UTILITY_START_X - 8 - 3;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + TITLE_Y, DarkGuiStyling.COLOR_TITLE, true);

        Slot playerSlot = menu.slots.get(MagneticBackpackMenu.PLAYER_SLOT_START);
        graphics.drawString(font, playerInventoryTitle, leftPos + playerSlot.x, topPos + playerSlot.y - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        graphics.drawString(font, Component.translatable("createessentials.gui.toolbox"),
                leftPos + TOOLBOX_TITLE_X, topPos + TOOLBOX_TITLE_Y, DarkGuiStyling.COLOR_TITLE, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        super.renderTooltip(graphics, x, y);
        if (!menu.getCarried().isEmpty())
            return;

        if (restockButton != null && restockButton.isHovered()) {
            boolean on = menu.isRestockEnabled();
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("createessentials.gui.restock_status",
                            Component.translatable(on ? "createessentials.gui.on" : "createessentials.gui.off")
                                    .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED)),
                    Component.translatable("createessentials.gui.restock_hint")
                            .withStyle(ChatFormatting.GRAY)), x, y);
            return;
        }

        for (DarkIconButton btn : utilityButtons) {
            if (btn.isHovered()) {
                graphics.renderComponentTooltip(font, btn.getTooltipLines(), x, y);
                break;
            }
        }
    }
}
