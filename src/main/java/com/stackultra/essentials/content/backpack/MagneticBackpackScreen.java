package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.stackultra.essentials.client.DarkGuiStyling;
import com.stackultra.essentials.client.DarkTextButton;
import com.stackultra.essentials.content.ChargeDisplay;
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

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackScreen extends AbstractBackpackScreen<MagneticBackpackMenu> {

    private static final int WINDOW_HEIGHT = 246;

    // Sleek control row aligned flush with the 9-slot column edges [7..86) and [90..169)
    private static final int BUTTON_Y = 19;
    private static final int BUTTON_W = 79;
    private static final int BUTTON_H = 14;
    private static final int BUTTON_LEFT_X = GRID_LEFT;
    private static final int BUTTON_RIGHT_X = GRID_RIGHT - BUTTON_W;

    // Compact live charge / remaining-time readout directly below the magnet control row
    private static final int CHARGE_TEXT_Y = 36;
    private static final int CHARGE_BAR_Y = 46;
    private static final int CHARGE_BAR_H = 2;

    // Left 3-slot filter panel aligned flush with the top-left corner of the main window
    private static final int LEFT_TAB_WIDTH = 25;
    private static final int LEFT_TAB_TOP = 0;
    private static final int LEFT_TAB_HEIGHT = 62;

    // Toolbox section directly beneath storage [50..104) and above player inventory [153..239)
    private static final int DIVIDER_STORAGE_Y = 107;
    private static final int TOOLBOX_TITLE_Y = 114;
    private static final int RESTOCK_BTN_Y = 111;
    private static final int DIVIDER_PLAYER_Y = 148;

    private static final int COLOR_BLACKLIST = 0xFFD66BFF;
    private static final int COLOR_WHITELIST = 0xFF78B8FF;
    private static final int COLOR_CHARGE_LOW = 0xFFF2C24C;
    private static final int COLOR_CHARGE_CRITICAL = 0xFFE05A5A;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getWindowHeight() {
        return WINDOW_HEIGHT;
    }

    @Override
    protected int getStorageDividerY() {
        return DIVIDER_STORAGE_Y;
    }

    @Override
    protected int getToolboxTitleY() {
        return TOOLBOX_TITLE_Y;
    }

    @Override
    protected int getRestockButtonY() {
        return RESTOCK_BTN_Y;
    }

    @Override
    protected int getPlayerDividerY() {
        return DIVIDER_PLAYER_Y;
    }

    @Override
    protected void initExtraWidgets() {
        DarkTextButton toggle = new DarkTextButton(leftPos + BUTTON_LEFT_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
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

        DarkTextButton blacklist = new DarkTextButton(leftPos + BUTTON_RIGHT_X, topPos + BUTTON_Y, BUTTON_W, BUTTON_H,
                () -> Component.translatable(menu.isBlacklist()
                        ? "createessentials.gui.blacklist" : "createessentials.gui.whitelist"),
                () -> menu.isBlacklist() ? COLOR_BLACKLIST : COLOR_WHITELIST,
                () -> {
                    menu.setBlacklistClient(!menu.isBlacklist());
                    PacketDistributor.sendToServer(blacklistPayload());
                });

        addRenderableWidget(toggle);
        addRenderableWidget(blacklist);
    }

    private RotationalMagnetTogglePayload togglePayload() {
        MagneticBackpackBlockEntity be = menu.getBlockEntity();
        return be != null
                ? RotationalMagnetTogglePayload.forBlockEntity(be.getBlockPos())
                : RotationalMagnetTogglePayload.forOpenItemMenu();
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
        return !inLeftTab && super.hasClickedOutside(mouseX, mouseY, guiLeft, guiTop, mouseButton);
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of(
                new Rect2i(leftPos - LEFT_TAB_WIDTH, topPos + LEFT_TAB_TOP, LEFT_TAB_WIDTH, LEFT_TAB_HEIGHT));
    }

    @Override
    protected void renderPanelBackground(GuiGraphics graphics) {
        DarkGuiStyling.drawPanelWithLeftTab(graphics, leftPos, topPos, imageWidth, imageHeight,
                LEFT_TAB_WIDTH, LEFT_TAB_TOP, LEFT_TAB_HEIGHT);
    }

    @Override
    protected void renderExtraBgBeforeSlots(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int charge = Math.max(0, menu.getCharge());
        int maxCharge = Math.max(1, menu.getMaxCharge());
        float ratio = Math.clamp((float) charge / (float) maxCharge, 0.0F, 1.0F);
        int barColor = chargeBarColor(ratio);
        DarkGuiStyling.drawHorizontalBar(graphics, leftPos + GRID_LEFT, topPos + CHARGE_BAR_Y,
                GRID_WIDTH, CHARGE_BAR_H, ratio, barColor);
    }

    @Override
    protected void renderExtraBgAfterSlots(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
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
    protected void renderExtraForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        int charge = Math.max(0, menu.getCharge());
        int seconds = charge / 20;
        String remaining = String.format("%02dm %02ds", seconds / 60, seconds % 60);
        Component timeText = Component.translatable("createessentials.gui.time_remaining", remaining);
        int textColor = menu.isCharging()
                ? DarkGuiStyling.COLOR_BAR
                : (charge <= 0 ? COLOR_CHARGE_CRITICAL : DarkGuiStyling.COLOR_TEXT);
        graphics.drawCenteredString(font, timeText, leftPos + imageWidth / 2, topPos + CHARGE_TEXT_Y, textColor);
    }

    @Override
    protected boolean renderExtraTooltip(GuiGraphics graphics, int x, int y) {
        if (!isHoveringChargeBar(x, y))
            return false;
        List<Component> lines = new ArrayList<>(2);
        lines.add(ChargeDisplay.charge(Math.max(0, menu.getCharge()), Math.max(1, menu.getMaxCharge())));
        if (menu.isCharging())
            lines.add(ChargeDisplay.chargingLine());
        graphics.renderComponentTooltip(font, lines, x, y);
        return true;
    }

    private boolean isHoveringChargeBar(int mouseX, int mouseY) {
        return mouseX >= leftPos + GRID_LEFT && mouseX < leftPos + GRID_RIGHT
                && mouseY >= topPos + CHARGE_TEXT_Y - 1 && mouseY <= topPos + CHARGE_BAR_Y + CHARGE_BAR_H + 1;
    }

    private static int chargeBarColor(float ratio) {
        if (ratio <= 0.05F)
            return COLOR_CHARGE_CRITICAL;
        if (ratio <= 0.20F)
            return COLOR_CHARGE_LOW;
        return DarkGuiStyling.COLOR_BAR;
    }
}
