package com.stackultra.essentials.content.backpack;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkIconButton;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.network.BackpackActionPayload;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
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

/**
 * Shared dark-styled GUI screen base for Mechanical, Netherite, and Magnetic Backpacks.
 * Consolidates the 4 top-right utility buttons, Restock toggle button, 8-compartment toolbox
 * slot rendering (compact counts & translucent ghosts), section dividers, and hover tooltips
 * while preserving exact per-screen dimensions and pixel coordinates.
 */
@OnlyIn(Dist.CLIENT)
public abstract class AbstractBackpackScreen<T extends AbstractBackpackMenu> extends AbstractSimiContainerScreen<T> {

    protected static final int WINDOW_WIDTH = 176;
    protected static final int GRID_LEFT = 7;
    protected static final int GRID_RIGHT = 169;
    protected static final int GRID_WIDTH = GRID_RIGHT - GRID_LEFT;

    protected static final int TITLE_Y = 6;
    protected static final int UTILITY_BTN_SIZE = 12;
    protected static final int UTILITY_BTN_GAP = 1;
    protected static final int UTILITY_BTN_Y = 4;
    protected static final int UTILITY_START_X = GRID_RIGHT - (UTILITY_BTN_SIZE * 4 + UTILITY_BTN_GAP * 3);

    protected static final int TOOLBOX_TITLE_X = 8;
    protected static final int RESTOCK_BTN_W = 74;
    protected static final int RESTOCK_BTN_H = 14;
    protected static final int RESTOCK_BTN_X = GRID_RIGHT - RESTOCK_BTN_W;

    protected static final float GHOST_PREVIEW_ALPHA = 0.40F;

    protected final List<DarkIconButton> utilityButtons = new ArrayList<>(4);
    protected DarkTextButton restockButton;

    protected AbstractBackpackScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    protected abstract int getWindowHeight();

    protected abstract int getStorageDividerY();

    protected abstract int getToolboxTitleY();

    protected abstract int getRestockButtonY();

    protected abstract int getPlayerDividerY();

    protected void initExtraWidgets() {
    }

    protected void renderPanelBackground(GuiGraphics graphics) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
    }

    protected void renderExtraBgBeforeSlots(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
    }

    protected void renderExtraBgAfterSlots(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
    }

    protected void renderExtraForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
    }

    protected boolean renderExtraTooltip(GuiGraphics graphics, int x, int y) {
        return false;
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, getWindowHeight());
        super.init();

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

        initExtraWidgets();

        restockButton = new DarkTextButton(
                leftPos + RESTOCK_BTN_X, topPos + getRestockButtonY(), RESTOCK_BTN_W, RESTOCK_BTN_H,
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

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        renderPanelBackground(graphics);
        DarkGuiStyling.drawDivider(graphics, leftPos + GRID_LEFT, topPos + getStorageDividerY(), GRID_WIDTH);
        DarkGuiStyling.drawDivider(graphics, leftPos + GRID_LEFT, topPos + getPlayerDividerY(), GRID_WIDTH);

        renderExtraBgBeforeSlots(graphics, partialTicks, mouseX, mouseY);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        renderExtraBgAfterSlots(graphics, partialTicks, mouseX, mouseY);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (menu.isToolboxSlot(slot.index)) {
            int compartment = slot.index - menu.getToolboxSlotStart();
            int total = menu.getToolboxCount(compartment);
            if (!stack.isEmpty() && total > 0) {
                graphics.renderItem(stack, slot.x, slot.y);
                graphics.renderItemDecorations(font, stack, slot.x, slot.y, "");
                DarkGuiStyling.drawCompactCount(graphics, font, total, slot.x, slot.y);
            } else if (!stack.isEmpty()) {
                DarkGuiStyling.drawGhostItem(graphics, stack, slot.x, slot.y, GHOST_PREVIEW_ALPHA);
            }
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int maxTitleWidth = utilityButtons.isEmpty()
                ? (imageWidth - 16)
                : (UTILITY_START_X - 8 - 4);
        String titleText = BackpackHelper.truncateTitle(
                title.getString(), maxTitleWidth, font::width, font::plainSubstrByWidth);
        graphics.drawString(font, titleText, leftPos + 8, topPos + TITLE_Y, DarkGuiStyling.COLOR_TITLE, true);

        renderExtraForeground(graphics, mouseX, mouseY, partialTicks);

        graphics.drawString(font, Component.translatable("createessentials.gui.toolbox"),
                leftPos + TOOLBOX_TITLE_X, topPos + getToolboxTitleY(), DarkGuiStyling.COLOR_TITLE, true);

        Slot playerSlot = menu.slots.get(menu.getPlayerSlotStart());
        graphics.drawString(font, playerInventoryTitle, leftPos + playerSlot.x, topPos + playerSlot.y - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && menu.isToolboxSlot(hoveredSlot.index)) {
            int compartment = hoveredSlot.index - menu.getToolboxSlotStart();
            if (menu.getToolboxCount(compartment) == 0 && hoveredSlot.hasItem()) {
                ItemStack ghost = hoveredSlot.getItem();
                graphics.renderComponentTooltip(font, List.of(
                        ghost.getHoverName().copy().withStyle(ChatFormatting.GRAY),
                        Component.translatable("createessentials.gui.reserved_hint")
                                .withStyle(ChatFormatting.DARK_GRAY)), x, y);
                return;
            }
        }

        super.renderTooltip(graphics, x, y);
        if (!menu.getCarried().isEmpty())
            return;

        if (renderExtraTooltip(graphics, x, y))
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
