package com.stackultra.essentials.content.backpack;

import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkIconButton;
import com.stackultra.essentials.network.BackpackActionPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

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

    private final List<DarkIconButton> utilityButtons = new ArrayList<>(4);

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
        int maxTitle = UTILITY_START_X - 8 - 3;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, DarkGuiStyling.COLOR_TITLE, true);

        int playerStart = menu.slots.size() - 36;
        int playerY = menu.slots.get(playerStart).y;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerY - 11,
                DarkGuiStyling.COLOR_TEXT_DIM, true);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        super.renderTooltip(graphics, x, y);
        if (!menu.getCarried().isEmpty())
            return;
        for (DarkIconButton btn : utilityButtons) {
            if (btn.isHovered()) {
                graphics.renderComponentTooltip(font, btn.getTooltipLines(), x, y);
                break;
            }
        }
    }
}
