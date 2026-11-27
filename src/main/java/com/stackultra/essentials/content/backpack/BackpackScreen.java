package com.stackultra.essentials.content.backpack;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkIconButton;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.network.BackpackActionPayload;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
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
public class BackpackScreen extends AbstractSimiContainerScreen<BackpackMenu> {

    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 213;

    private static final int GRID_LEFT = 7;
    private static final int GRID_RIGHT = 169;
    private static final int GRID_WIDTH = GRID_RIGHT - GRID_LEFT;

    private static final int UTILITY_BTN_SIZE = 12;
    private static final int UTILITY_BTN_GAP = 1;
    private static final int UTILITY_BTN_Y = 4;
    private static final int UTILITY_START_X = GRID_RIGHT - (UTILITY_BTN_SIZE * 4 + UTILITY_BTN_GAP * 3);

    private static final int DIVIDER_STORAGE_Y = 74;
    private static final int TOOLBOX_TITLE_X = 8;
    private static final int TOOLBOX_TITLE_Y = 81;
    private static final int RESTOCK_BTN_W = 74;
    private static final int RESTOCK_BTN_H = 14;
    private static final int RESTOCK_BTN_X = GRID_RIGHT - RESTOCK_BTN_W;
    private static final int RESTOCK_BTN_Y = 78;
    private static final int DIVIDER_PLAYER_Y = 115;

    private static final float GHOST_PREVIEW_ALPHA = 0.40F;

    private final List<DarkIconButton> utilityButtons = new ArrayList<>(4);
    private DarkTextButton restockButton;

    public BackpackScreen(BackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
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

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        DarkGuiStyling.drawDivider(graphics, leftPos + GRID_LEFT, topPos + DIVIDER_STORAGE_Y, GRID_WIDTH);
        DarkGuiStyling.drawDivider(graphics, leftPos + GRID_LEFT, topPos + DIVIDER_PLAYER_Y, GRID_WIDTH);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (menu.isToolboxSlot(slot.index)) {
            int compartment = slot.index - BackpackMenu.TOOLBOX_SLOT_START;
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
        String titleText = title.getString();
        int maxTitle = UTILITY_START_X - 8 - 4;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, DarkGuiStyling.COLOR_TITLE, true);

        graphics.drawString(font, Component.translatable("createessentials.gui.toolbox"),
                leftPos + TOOLBOX_TITLE_X, topPos + TOOLBOX_TITLE_Y, DarkGuiStyling.COLOR_TITLE, true);

        int playerY = menu.slots.get(BackpackMenu.PLAYER_SLOT_START).y;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerY - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && menu.isToolboxSlot(hoveredSlot.index)) {
            int compartment = hoveredSlot.index - BackpackMenu.TOOLBOX_SLOT_START;
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
