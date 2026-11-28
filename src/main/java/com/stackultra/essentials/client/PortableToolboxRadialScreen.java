package com.stackultra.essentials.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.backpack.BackpackHelper;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Visual clone of Create 6.0.9's {@code ToolboxScreen} ({@link AllGuiTextures#TOOLBOX}
 * golden header bar, dark brown patterned interior, 8 full-size circular slots, and central
 * toolbox return button) paired with a dark-styled Player Inventory & Hotbar panel beneath it,
 * backed by our server-authoritative portable backpack toolbox sync ({@link PortableToolboxOverlay},
 * {@link BackpackToolboxPayload}, and Curios/inventory lookup).
 */
@OnlyIn(Dist.CLIENT)
public class PortableToolboxRadialScreen extends AbstractSimiScreen {

    private static final int COMPARTMENTS = PortableToolboxInventory.COMPARTMENTS;
    private static final int WORLD_TOOLBOXES = -7;
    private static final int UNEQUIP = -5;

    // Exact Create 6.0.9 AllGuiTextures.TOOLBOX panel slice (golden header + dark brown body + full 4-sided border)
    private static final int PANEL_WIDTH = 180;
    private static final int PANEL_HEIGHT = 142;

    // Exact Create ToolboxScreen header text offset & ink color (5841956 == 0x592424)
    private static final int TITLE_X = 15;
    private static final int TITLE_Y = 4;
    private static final int TITLE_MAX_WIDTH = 150;
    private static final int TITLE_COLOR = 0x592424;

    // Exact Create ToolboxMenu circular 16x16 item slot offsets relative to AllGuiTextures.TOOLBOX (0, 0)
    // Each slot sits dead-center inside a 24x24 recessed grey well at (SLOT_X[i] - 4, SLOT_Y[i] - 4).
    private static final int[] SLOT_X = { 49, 82, 115, 121, 115, 82, 49, 43 };
    private static final int[] SLOT_Y = { 37, 31,  37,  70, 103, 109, 103, 70 };
    private static final int SLOT_WELL_OFFSET = 4;
    private static final int SLOT_WELL_SIZE = 24;

    // Exact Create ToolboxScreen central IconButton (AllIcons.I_TOOLBOX) offset relative to AllGuiTextures.TOOLBOX
    private static final int CENTER_BTN_X = 81;
    private static final int CENTER_BTN_Y = 69;
    private static final int CENTER_BTN_SIZE = 18;
    private static final int CIRCLE_CENTER_X = CENTER_BTN_X + CENTER_BTN_SIZE / 2; // 90
    private static final int CIRCLE_CENTER_Y = CENTER_BTN_Y + CENTER_BTN_SIZE / 2; // 78

    // Right-side Create world-toolbox switcher button aligned cleanly outside the panel's right border
    private static final int SWITCHER_X = PANEL_WIDTH + 6;
    private static final int SWITCHER_Y = CENTER_BTN_Y;
    private static final int SWITCHER_SIZE = 18;

    // Lower dark-styled Player Inventory (3x9) & Hotbar (1x9) panel matching DarkGuiStyling
    private static final int INV_GAP = 3;
    private static final int INV_WIDTH = 176;
    private static final int INV_HEIGHT = 96;
    private static final int INV_SLOTS_X = 8;
    private static final int INV_MAIN_Y = 16;
    private static final int INV_HOTBAR_Y = 74;
    private static final int TOTAL_HEIGHT = PANEL_HEIGHT + INV_GAP + INV_HEIGHT;

    private static final int COLOR_LINKED_ON = 0xFF56C2C2;
    private static final int COLOR_LINKED_ON_FILL = 0x2A56C2C2;
    private static final int COLOR_LINKED_OFF = 0xFFA68048;
    private static final int COLOR_LINKED_OFF_FILL = 0x28A68048;

    private final Component headerTitle;
    private final List<ToolboxBlockEntity> nearbyWorldToolboxes;
    private final PortableToolboxInventory fallbackSnapshot;

    private int ticksOpen;
    private int hoveredSlot = UNEQUIP;
    private boolean scrollMode;
    private int scrollSlot = 0;
    private boolean actionCommitted = false;
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    public PortableToolboxRadialScreen(ItemStack backpackStack, List<ToolboxBlockEntity> nearbyWorldToolboxes) {
        super(backpackStack.getHoverName());
        this.headerTitle = backpackStack.getHoverName();
        this.nearbyWorldToolboxes = nearbyWorldToolboxes;
        this.fallbackSnapshot = PortableToolboxInventory.forStack(backpackStack);
    }

    @Override
    protected void init() {
        super.init();
        // Immediately request fresh authoritative state from server in case Curios/inventory changed
        PacketDistributor.sendToServer(BackpackToolboxPayload.requestSync());
    }

    private int panelX() {
        return (width - PANEL_WIDTH) / 2;
    }

    private int panelY() {
        return Math.max(12, (height - TOTAL_HEIGHT) / 2);
    }

    private int invPanelX(int px) {
        return px + (PANEL_WIDTH - INV_WIDTH) / 2;
    }

    private int invPanelY(int py) {
        return py + PANEL_HEIGHT + INV_GAP;
    }

    private void switchToWorldToolboxes() {
        actionCommitted = true;
        Minecraft mc = Minecraft.getInstance();
        if (nearbyWorldToolboxes.size() == 1) {
            mc.setScreen(new RadialToolboxMenu(nearbyWorldToolboxes,
                    RadialToolboxMenu.State.SELECT_ITEM, nearbyWorldToolboxes.get(0)));
        } else {
            mc.setScreen(new RadialToolboxMenu(nearbyWorldToolboxes,
                    RadialToolboxMenu.State.SELECT_BOX, null));
        }
    }

    private ItemStack getLiveDisplayItem(int compartment) {
        if (PortableToolboxOverlay.hasSyncedData() && PortableToolboxOverlay.hasSyncedBackpack()) {
            return PortableToolboxOverlay.getSyncedDisplayItem(compartment);
        }
        return fallbackSnapshot.getDisplayTemplate(compartment);
    }

    private int getLiveCount(int compartment) {
        if (PortableToolboxOverlay.hasSyncedData() && PortableToolboxOverlay.hasSyncedBackpack()) {
            return PortableToolboxOverlay.getSyncedCount(compartment);
        }
        return fallbackSnapshot.getTotalCount(compartment);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Color color = BACKGROUND_COLOR
                .scaleAlpha(Math.min(1, (ticksOpen + AnimationTickHolder.getPartialTicks()) / 20f));
        graphics.fillGradient(0, 0, width, height, color.getRGB(), color.getRGB());
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        float fade = Mth.clamp((ticksOpen + AnimationTickHolder.getPartialTicks()) / 10f, 1 / 512f, 1);

        int px = panelX();
        int py = panelY();
        int ix = invPanelX(px);
        int iy = invPanelY(py);

        if (!Double.isNaN(lastMouseX) && (Math.abs(mouseX - lastMouseX) > 2.0 || Math.abs(mouseY - lastMouseY) > 2.0)) {
            scrollMode = false;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (!scrollMode) {
            hoveredSlot = resolveHover(mouseX, mouseY, px, py, ix, iy);
        }
        int active = activeSlot();

        // 1. Render Create's exact AllGuiTextures.TOOLBOX panel (golden header + dark brown patterned interior)
        graphics.blit(AllGuiTextures.TOOLBOX.location, px, py, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        // 2. Render backpack name in Create ToolboxScreen's exact header style & position (truncated if needed)
        String truncatedTitle = BackpackHelper.truncateTitle(
                headerTitle.getString(), TITLE_MAX_WIDTH, font::width, font::plainSubstrByWidth);
        graphics.drawString(font, truncatedTitle, px + TITLE_X, py + TITLE_Y, TITLE_COLOR, false);

        Component tip = null;

        // 3. Render all 8 full-size circular Create Toolbox slots with crisp translucent hover highlight
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            int itemX = px + SLOT_X[slot];
            int itemY = py + SLOT_Y[slot];
            int wellX = itemX - SLOT_WELL_OFFSET;
            int wellY = itemY - SLOT_WELL_OFFSET;
            boolean selected = active == slot;

            // Translucent hover highlight inside the grey well + crisp light border (never darkens/blackens the slot)
            if (selected) {
                graphics.fill(wellX, wellY, wellX + SLOT_WELL_SIZE, wellY + 1, 0xFFF2C24C);
                graphics.fill(wellX, wellY + SLOT_WELL_SIZE - 1, wellX + SLOT_WELL_SIZE, wellY + SLOT_WELL_SIZE, 0xFFF2C24C);
                graphics.fill(wellX, wellY + 1, wellX + 1, wellY + SLOT_WELL_SIZE - 1, 0xFFF2C24C);
                graphics.fill(wellX + SLOT_WELL_SIZE - 1, wellY + 1, wellX + SLOT_WELL_SIZE, wellY + SLOT_WELL_SIZE - 1, 0xFFF2C24C);
                graphics.fill(wellX + 1, wellY + 1, wellX + SLOT_WELL_SIZE - 1, wellY + SLOT_WELL_SIZE - 1, 0x55FFFFFF);
            }

            ItemStack display = getLiveDisplayItem(slot);
            int count = getLiveCount(slot);

            if (count > 0 && !display.isEmpty()) {
                graphics.renderItem(display, itemX, itemY);
                graphics.renderItemDecorations(font, display, itemX, itemY, "");
                DarkGuiStyling.drawCompactCount(graphics, font, count, itemX, itemY);
            } else if (!display.isEmpty()) {
                DarkGuiStyling.drawGhostItem(graphics, display, itemX, itemY, 0.40F);
            }

            if (selected) {
                if (count > 0 && !display.isEmpty()) {
                    tip = Component.literal(display.getHoverName().getString() + " (" + count + ")")
                            .withStyle(ChatFormatting.WHITE);
                } else if (!display.isEmpty()) {
                    tip = Component.literal(display.getHoverName().getString() + " (0)")
                            .withStyle(ChatFormatting.GRAY);
                } else {
                    tip = Component.translatable("createessentials.tooltip.backpack_empty")
                            .withStyle(ChatFormatting.GRAY);
                }
            }
        }

        // 4. Render Create ToolboxScreen's exact central 18x18 toolbox return button
        int btnX = px + CENTER_BTN_X;
        int btnY = py + CENTER_BTN_Y;
        boolean centerSelected = active == UNEQUIP;
        (centerSelected ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON).render(graphics, btnX, btnY);
        AllIcons.I_TOOLBOX.render(graphics, btnX + 1, btnY + 1);
        if (centerSelected) {
            ItemStack mainHand = minecraft != null && minecraft.player != null
                    ? minecraft.player.getMainHandItem()
                    : ItemStack.EMPTY;
            tip = (!mainHand.isEmpty()
                    ? CreateLang.translateDirect("toolbox.unequip", mainHand.getHoverName())
                    : Component.translatable("createessentials.gui.radial_return_desc"))
                    .withStyle(ChatFormatting.GOLD);
        }

        // 5. Render right-side world-toolbox switcher aligned cleanly outside the panel when nearby
        if (!nearbyWorldToolboxes.isEmpty()) {
            int swX = px + SWITCHER_X;
            int swY = py + SWITCHER_Y;
            boolean swSelected = active == WORLD_TOOLBOXES;
            (swSelected ? AllGuiTextures.BUTTON_HOVER : AllGuiTextures.BUTTON).render(graphics, swX, swY);
            AllIcons.I_TOOLBOX.render(graphics, swX + 1, swY + 1);
            if (swSelected) {
                tip = Component.translatable("createessentials.gui.world_toolboxes", nearbyWorldToolboxes.size())
                        .withStyle(ChatFormatting.GOLD);
            }
        }

        // 6. Render dark-styled Player Inventory (3x9) & Hotbar (1x9) panel directly beneath the toolbox
        ItemStack hoveredInventoryStack = renderPlayerInventoryPanel(graphics, ix, iy, mouseX, mouseY);

        // 7. Render toolbox hover label cleanly above the golden header bar
        if (tip != null && hoveredInventoryStack.isEmpty()) {
            int alpha = Math.min(255, (int) (fade * 255.0F));
            if (alpha > 8) {
                PoseStack ms = graphics.pose();
                ms.pushPose();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int textW = font.width(tip);
                int color = 0xFFFFFF | ((alpha << 24) & 0xFF000000);
                int tipY = Math.max(2, py - 11);
                graphics.drawString(font, tip, px + PANEL_WIDTH / 2 - textW / 2, tipY, color, true);
                RenderSystem.disableBlend();
                ms.popPose();
            }
        }

        // 8. Render standard Minecraft item tooltip when hovering items in the player inventory/hotbar
        if (!hoveredInventoryStack.isEmpty()) {
            graphics.renderTooltip(font, hoveredInventoryStack, mouseX, mouseY);
        }
    }

    private ItemStack renderPlayerInventoryPanel(GuiGraphics graphics, int ix, int iy, int mouseX, int mouseY) {
        DarkGuiStyling.drawPanel(graphics, ix, iy, INV_WIDTH, INV_HEIGHT);
        graphics.drawString(font, Component.translatable("container.inventory"),
                ix + INV_SLOTS_X, iy + 5, DarkGuiStyling.COLOR_TEXT_DIM, false);

        Player player = minecraft != null ? minecraft.player : null;
        Inventory inv = player != null ? player.getInventory() : null;
        int selectedHotbar = inv != null ? inv.selected : -1;
        int lockedMask = PortableToolboxOverlay.getLockedHotbarMask();
        boolean restockActive = PortableToolboxOverlay.isRestockActive();

        ItemStack hoveredStack = ItemStack.EMPTY;

        // Main 27-slot player inventory (rows 0..2, inventory indices 9..35)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotX = ix + INV_SLOTS_X + col * 18;
                int slotY = iy + INV_MAIN_Y + row * 18;
                DarkGuiStyling.drawSlot(graphics, slotX, slotY);

                boolean hovered = mouseX >= slotX - 1 && mouseX < slotX + 17
                        && mouseY >= slotY - 1 && mouseY < slotY + 17;
                if (hovered) {
                    graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x45FFFFFF);
                }

                if (inv != null) {
                    ItemStack stack = inv.getItem(9 + row * 9 + col);
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, slotX, slotY);
                        graphics.renderItemDecorations(font, stack, slotX, slotY);
                        if (hovered) {
                            hoveredStack = stack;
                        }
                    }
                }
            }
        }

        // 9-slot hotbar (inventory indices 0..8) with active-slot & toolbox-linked highlights
        for (int col = 0; col < 9; col++) {
            int slotX = ix + INV_SLOTS_X + col * 18;
            int slotY = iy + INV_HOTBAR_Y;
            DarkGuiStyling.drawSlot(graphics, slotX, slotY);

            boolean isSelected = col == selectedHotbar;
            boolean isLinked = (lockedMask & (1 << col)) != 0;
            boolean hovered = mouseX >= slotX - 1 && mouseX < slotX + 17
                    && mouseY >= slotY - 1 && mouseY < slotY + 17;

            if (isLinked) {
                int linkBorder = restockActive ? COLOR_LINKED_ON : COLOR_LINKED_OFF;
                int linkFill = restockActive ? COLOR_LINKED_ON_FILL : COLOR_LINKED_OFF_FILL;
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, linkFill);
                drawOutline(graphics, slotX - 1, slotY - 1, 18, 18, linkBorder);
            }

            if (isSelected) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x30F2C24C);
                drawOutline(graphics, slotX - 1, slotY - 1, 18, 18, DarkGuiStyling.COLOR_TITLE);
            }

            if (hovered) {
                graphics.fill(slotX, slotY, slotX + 16, slotY + 16, 0x45FFFFFF);
            }

            if (inv != null) {
                ItemStack stack = inv.getItem(col);
                if (!stack.isEmpty()) {
                    graphics.renderItem(stack, slotX, slotY);
                    graphics.renderItemDecorations(font, stack, slotX, slotY);
                    if (hovered) {
                        hoveredStack = stack;
                    }
                }
            }
        }

        return hoveredStack;
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private int resolveHotbarClick(double mouseX, double mouseY, int ix, int iy) {
        for (int col = 0; col < 9; col++) {
            int slotX = ix + INV_SLOTS_X + col * 18;
            int slotY = iy + INV_HOTBAR_Y;
            if (mouseX >= slotX - 1 && mouseX < slotX + 17
                    && mouseY >= slotY - 1 && mouseY < slotY + 17) {
                return col;
            }
        }
        return -1;
    }

    private int resolveHover(int mouseX, int mouseY, int px, int py, int ix, int iy) {
        // When hovering over the lower player inventory panel, never select a toolbox compartment or center button
        if (mouseX >= ix && mouseX < ix + INV_WIDTH && mouseY >= iy && mouseY < iy + INV_HEIGHT) {
            return -1;
        }

        // 1. Direct hit on right-side world-toolbox switcher
        if (!nearbyWorldToolboxes.isEmpty()) {
            int swX = px + SWITCHER_X;
            int swY = py + SWITCHER_Y;
            if (mouseX >= swX - 2 && mouseX <= swX + SWITCHER_SIZE + 2
                    && mouseY >= swY - 2 && mouseY <= swY + SWITCHER_SIZE + 2) {
                return WORLD_TOOLBOXES;
            }
        }

        // 2. Direct hit on central 18x18 toolbox return button
        int btnX = px + CENTER_BTN_X;
        int btnY = py + CENTER_BTN_Y;
        if (mouseX >= btnX && mouseX < btnX + CENTER_BTN_SIZE
                && mouseY >= btnY && mouseY < btnY + CENTER_BTN_SIZE) {
            return UNEQUIP;
        }

        // 3. Direct hit on any of the 8 circular 24x24 slot wells
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            int wellX = px + SLOT_X[slot] - SLOT_WELL_OFFSET;
            int wellY = py + SLOT_Y[slot] - SLOT_WELL_OFFSET;
            if (mouseX >= wellX && mouseX < wellX + SLOT_WELL_SIZE
                    && mouseY >= wellY && mouseY < wellY + SLOT_WELL_SIZE) {
                return slot;
            }
        }

        // 4. Radial direction fallback when cursor is inside the toolbox panel area
        if (mouseX >= px && mouseX < px + PANEL_WIDTH && mouseY >= py && mouseY < py + PANEL_HEIGHT) {
            double cx = px + CIRCLE_CENTER_X;
            double cy = py + CIRCLE_CENTER_Y;
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double distSq = dx * dx + dy * dy;
            if (distSq <= 169.0) {
                return UNEQUIP;
            }
            int bestSlot = -1;
            double bestDistSq = Double.MAX_VALUE;
            for (int slot = 0; slot < COMPARTMENTS; slot++) {
                double sx = px + SLOT_X[slot] + 8.0;
                double sy = py + SLOT_Y[slot] + 8.0;
                double d = (mouseX - sx) * (mouseX - sx) + (mouseY - sy) * (mouseY - sy);
                if (d < bestDistSq) {
                    bestDistSq = d;
                    bestSlot = slot;
                }
            }
            return bestSlot;
        }

        return -1;
    }

    private int activeSlot() {
        return scrollMode ? scrollSlot : hoveredSlot;
    }

    private void commitSelection() {
        if (actionCommitted)
            return;
        actionCommitted = true;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return;

        int selected = activeSlot();
        int hotbarSlot = player.getInventory().selected;
        if (selected == UNEQUIP) {
            PacketDistributor.sendToServer(BackpackToolboxPayload.returnHeld(hotbarSlot));
        } else if (selected >= 0 && selected < COMPARTMENTS) {
            if (!getLiveDisplayItem(selected).isEmpty() && getLiveCount(selected) > 0) {
                PacketDistributor.sendToServer(BackpackToolboxPayload.equip(selected, hotbarSlot));
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        commitSelection();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0)
            return false;
        scrollMode = true;
        scrollSlot = (((int) (scrollSlot - Mth.sign(scrollY))) + COMPARTMENTS) % COMPARTMENTS;
        for (int i = 0; i < COMPARTMENTS; i++) {
            if (!getLiveDisplayItem(scrollSlot).isEmpty() && getLiveCount(scrollSlot) > 0) {
                break;
            }
            scrollSlot = (scrollSlot - Mth.sign(scrollY) + COMPARTMENTS) % COMPARTMENTS;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int px = panelX();
            int py = panelY();
            int ix = invPanelX(px);
            int iy = invPanelY(py);

            // Clicking any of the 9 hotbar slots switches active hotbar selection in-place (synced to server)
            int clickedHotbar = resolveHotbarClick(x, y, ix, iy);
            if (clickedHotbar >= 0 && minecraft != null && minecraft.player != null) {
                minecraft.player.getInventory().selected = clickedHotbar;
                if (minecraft.getConnection() != null) {
                    minecraft.getConnection().send(new ServerboundSetCarriedItemPacket(clickedHotbar));
                }
                return true;
            }

            // Clicking inside the main player inventory area is a safe no-op (never closes or mis-equips)
            if (x >= ix && x < ix + INV_WIDTH && y >= iy && y < iy + INV_HEIGHT) {
                return true;
            }

            int selected = activeSlot();
            if (selected == WORLD_TOOLBOXES && !nearbyWorldToolboxes.isEmpty()) {
                switchToWorldToolboxes();
                return true;
            }
            if (selected == UNEQUIP || selected >= 0) {
                onClose();
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            actionCommitted = true;
            onClose();
            return true;
        }

        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            actionCommitted = true;
            onClose();
            return true;
        }

        if (minecraft != null) {
            KeyMapping[] hotbarKeys = minecraft.options.keyHotbarSlots;
            for (int i = 0; i < hotbarKeys.length && i < COMPARTMENTS; i++) {
                if (hotbarKeys[i].matches(keyCode, scanCode)) {
                    if (getLiveDisplayItem(i).isEmpty() || getLiveCount(i) <= 0)
                        return false;
                    scrollMode = true;
                    scrollSlot = i;
                    mouseClicked(0, 0, GLFW.GLFW_MOUSE_BUTTON_LEFT);
                    return true;
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        InputConstants.Key released = InputConstants.getKey(keyCode, scanCode);
        if (AllKeys.TOOLBELT.getKeybind().isActiveAndMatches(released)
                || AllKeys.TOOLBELT.getKeybind().getKey().equals(released)
                || AllKeys.TOOLBELT.doesModifierAndCodeMatch(keyCode)) {
            if (activeSlot() == WORLD_TOOLBOXES && !nearbyWorldToolboxes.isEmpty()) {
                switchToWorldToolboxes();
            } else {
                onClose();
            }
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        ticksOpen++;
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            actionCommitted = true;
            onClose();
            return;
        }
        ItemStack liveBackpack = PortableToolboxHandler.findPortableToolboxBackpack(mc.player);
        if (liveBackpack.isEmpty()) {
            actionCommitted = true;
            onClose();
            return;
        }
        fallbackSnapshot.load(liveBackpack);
    }
}
