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

@OnlyIn(Dist.CLIENT)
public class BackpackScreen extends AbstractSimiContainerScreen<BackpackMenu> {

    private static final int WINDOW_WIDTH = 176;
    private static final int WINDOW_HEIGHT = 167;

    private static final int UTILITY_BTN_SIZE = 12;
    private static final int UTILITY_BTN_GAP = 1;
    private static final int UTILITY_BTN_Y = 4;
    private static final int UTILITY_START_X = 169 - (UTILITY_BTN_SIZE * 4 + UTILITY_BTN_GAP * 3);

    private static final int RIGHT_TAB_WIDTH = 44;
    private static final int RIGHT_TAB_TOP = 16;
    private static final int RIGHT_TAB_HEIGHT = 96;

    private static final int RESTOCK_BTN_X = 179;
    private static final int RESTOCK_BTN_Y = 20;
    private static final int RESTOCK_BTN_W = 36;
    private static final int RESTOCK_BTN_H = 14;

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
                () -> Component.translatable(menu.isRestockEnabled()
                        ? "createessentials.gui.restock_short_on"
                        : "createessentials.gui.restock_short_off"),
                () -> menu.isRestockEnabled() ? 0xFF55FF55 : 0xFFFF5555,
                () -> PacketDistributor.sendToServer(BackpackToolboxPayload.toggleRestock()));
        addRenderableWidget(restockButton);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int guiLeft, int guiTop, int mouseButton) {
        boolean inRightTab = mouseX >= guiLeft + imageWidth && mouseX < guiLeft + imageWidth + RIGHT_TAB_WIDTH
                && mouseY >= guiTop + RIGHT_TAB_TOP && mouseY < guiTop + RIGHT_TAB_TOP + RIGHT_TAB_HEIGHT;
        return !inRightTab && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanelWithRightTab(graphics, leftPos, topPos, imageWidth, imageHeight,
                RIGHT_TAB_WIDTH, RIGHT_TAB_TOP, RIGHT_TAB_HEIGHT);

        for (Slot slot : menu.slots)
            DarkGuiStyling.drawSlot(graphics, leftPos + slot.x, topPos + slot.y);

        // Render ghost reserved filter icons for empty toolbox compartments
        for (int c = 0; c < BackpackMenu.TOOLBOX_SLOTS; c++) {
            Slot slot = menu.slots.get(BackpackMenu.TOOLBOX_SLOT_START + c);
            if (menu.getToolboxCount(c) > 0)
                continue;
            ItemStack filter = menu.getToolboxFilter(c);
            if (!filter.isEmpty()) {
                DarkGuiStyling.drawGhostItem(graphics, filter, leftPos + slot.x, topPos + slot.y, 0.35F);
                DarkGuiStyling.drawReservedMarker(graphics, leftPos + slot.x, topPos + slot.y);
            }
        }
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
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, DarkGuiStyling.COLOR_TITLE, true);

        int playerY = menu.slots.get(BackpackMenu.PLAYER_SLOT_START).y;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerY - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && menu.isToolboxSlot(hoveredSlot.index)) {
            int c = hoveredSlot.index - BackpackMenu.TOOLBOX_SLOT_START;
            int total = menu.getToolboxCount(c);
            ItemStack filter = menu.getToolboxFilter(c);
            if (total == 0 && !filter.isEmpty()) {
                graphics.renderComponentTooltip(font, List.of(
                        filter.getHoverName().copy().withStyle(ChatFormatting.YELLOW),
                        Component.translatable("createessentials.gui.toolbox_reserved_empty")
                                .withStyle(ChatFormatting.GRAY),
                        Component.translatable("createessentials.gui.toolbox_clear_hint")
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
