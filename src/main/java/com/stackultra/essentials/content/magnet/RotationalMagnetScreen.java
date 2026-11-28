package com.stackultra.essentials.content.magnet;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.network.BlacklistTogglePayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
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

    private static final int GRID_LEFT = 7;
    private static final int GRID_RIGHT = 169;
    private static final int GRID_WIDTH = GRID_RIGHT - GRID_LEFT;

    private static final int TITLE_Y = 7;
    private static final int BUTTON_Y = 21;
    private static final int BUTTON_W = 79;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_LEFT_X = GRID_LEFT;
    private static final int BUTTON_RIGHT_X = GRID_RIGHT - BUTTON_W;
    private static final int FILTER_LABEL_Y = 44;
    private static final int TIMER_Y = 80;
    private static final int DIVIDER_Y = 94;

    public RotationalMagnetScreen(RotationalMagnetMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        super.init();

        DarkTextButton toggle = new DarkTextButton(leftPos + BUTTON_LEFT_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> {
                    boolean enabled = menu.isEnabled();
                    return Component.translatable("createessentials.gui.magnet_status",
                            Component.translatable(enabled ? "createessentials.gui.on" : "createessentials.gui.off")
                                    .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED));
                },
                () -> DarkGuiStyling.activePalette().buttonText(),
                () -> {
                    menu.setEnabledClient(!menu.isEnabled());
                    PacketDistributor.sendToServer(togglePayload());
                });

        DarkTextButton blacklist = new DarkTextButton(leftPos + BUTTON_RIGHT_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> Component.translatable(menu.isBlacklist()
                        ? "createessentials.gui.blacklist" : "createessentials.gui.whitelist"),
                () -> {
                    DarkGuiStyling.Palette p = DarkGuiStyling.activePalette();
                    return menu.isBlacklist() ? p.blacklistText() : p.whitelistText();
                },
                () -> {
                    menu.setBlacklistClient(!menu.isBlacklist());
                    PacketDistributor.sendToServer(blacklistPayload());
                });

        addRenderableWidget(toggle);
        addRenderableWidget(blacklist);
    }

    private RotationalMagnetTogglePayload togglePayload() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        return be != null
                ? RotationalMagnetTogglePayload.forBlockEntity(be.getBlockPos())
                : RotationalMagnetTogglePayload.forOpenItemMenu();
    }

    private BlacklistTogglePayload blacklistPayload() {
        RotationalMagnetBlockEntity be = menu.getBlockEntity();
        return be != null
                ? new BlacklistTogglePayload(be.getBlockPos(), false)
                : new BlacklistTogglePayload(BlockPos.ZERO, true);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, leftPos, topPos, imageWidth, imageHeight);
        DarkGuiStyling.drawDivider(graphics, leftPos + GRID_LEFT, topPos + DIVIDER_Y, GRID_WIDTH);

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
        DarkGuiStyling.Palette p = DarkGuiStyling.activePalette();
        String titleText = title.getString();
        int maxTitle = imageWidth - 16;
        if (font.width(titleText) > maxTitle)
            titleText = font.plainSubstrByWidth(titleText, maxTitle - font.width("...")) + "...";
        DarkGuiStyling.drawCenteredText(graphics, font, titleText, leftPos + imageWidth / 2, topPos + TITLE_Y,
                p.title(), p.textShadow());

        Component filterHeader = Component.translatable("createessentials.gui.item_filter");
        DarkGuiStyling.drawCenteredText(graphics, font, filterHeader, leftPos + imageWidth / 2,
                topPos + FILTER_LABEL_Y, p.title(), p.textShadow());

        int seconds = Math.max(0, menu.getCharge()) / 20;
        String remaining = String.format("%02dm %02ds", seconds / 60, seconds % 60);
        Component timeText = Component.translatable("createessentials.gui.time_remaining", remaining);
        DarkGuiStyling.drawCenteredText(graphics, font, timeText, leftPos + imageWidth / 2, topPos + TIMER_Y,
                p.text(), p.textShadow());

        int playerStart = menu.slots.size() - 36;
        int playerLabelY = menu.slots.get(playerStart).y - 11;
        graphics.drawString(font, playerInventoryTitle, leftPos + 8, topPos + playerLabelY,
                p.textDim(), p.textShadow());

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
