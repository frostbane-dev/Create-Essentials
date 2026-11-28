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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Compact {@code Alt} quick-select popup styled directly after Create 6.0.9's
 * {@code ToolboxScreen} ({@link AllGuiTextures#TOOLBOX} 180x142 golden header bar, dark brown
 * patterned interior, 8 full-size circular slots, and central 18x18 return button), backed by
 * server-authoritative portable backpack toolbox sync ({@link PortableToolboxOverlay},
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

    // Clean translucent hover highlight colors drawn behind item icons (never dark/black)
    private static final int HOVER_BORDER_COLOR = 0xFFF2C24C;
    private static final int HOVER_FILL_COLOR = 0x55FFFFFF;
    // Neutral grey veil color matching Create's AllGuiTextures.TOOLBOX recessed slot wells
    private static final int TOOLBOX_WELL_RGB = 0x008B8B8B;

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
        return width / 2 - CIRCLE_CENTER_X;
    }

    private int panelY() {
        return height / 2 - CIRCLE_CENTER_Y;
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

    private static void drawToolboxGhostItem(GuiGraphics graphics, ItemStack stack, int x, int y, float alpha) {
        float clamped = Mth.clamp(alpha, 0.0F, 1.0F);
        graphics.renderItem(stack, x, y);
        int veilAlpha = Math.round((1.0F - clamped) * 255.0F);
        if (veilAlpha > 0) {
            int veilColor = (veilAlpha << 24) | TOOLBOX_WELL_RGB;
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200);
            graphics.fill(x, y, x + 16, y + 16, veilColor);
            graphics.pose().popPose();
        }
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

        if (!Double.isNaN(lastMouseX) && (Math.abs(mouseX - lastMouseX) > 2.0 || Math.abs(mouseY - lastMouseY) > 2.0)) {
            scrollMode = false;
        }
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (!scrollMode) {
            hoveredSlot = resolveHover(mouseX, mouseY, px, py);
        }
        int active = activeSlot();

        // 1. Render Create's exact 180x142 AllGuiTextures.TOOLBOX panel (golden header + dark brown interior)
        graphics.blit(AllGuiTextures.TOOLBOX.location, px, py, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        // 2. Render backpack name in Create ToolboxScreen's exact header style & position (truncated if needed)
        String truncatedTitle = BackpackHelper.truncateTitle(
                headerTitle.getString(), TITLE_MAX_WIDTH, font::width, font::plainSubstrByWidth);
        graphics.drawString(font, truncatedTitle, px + TITLE_X, py + TITLE_Y, TITLE_COLOR, false);

        Component tip = null;

        // 3. Render all 8 full-size circular Create Toolbox slots with clean gold/translucent hover highlight
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            int itemX = px + SLOT_X[slot];
            int itemY = py + SLOT_Y[slot];
            int wellX = itemX - SLOT_WELL_OFFSET;
            int wellY = itemY - SLOT_WELL_OFFSET;
            boolean selected = active == slot;

            // Crisp 1px warm gold border + translucent white fill drawn behind item icon, count, and ghost preview
            if (selected) {
                graphics.fill(wellX, wellY, wellX + SLOT_WELL_SIZE, wellY + 1, HOVER_BORDER_COLOR);
                graphics.fill(wellX, wellY + SLOT_WELL_SIZE - 1, wellX + SLOT_WELL_SIZE, wellY + SLOT_WELL_SIZE, HOVER_BORDER_COLOR);
                graphics.fill(wellX, wellY + 1, wellX + 1, wellY + SLOT_WELL_SIZE - 1, HOVER_BORDER_COLOR);
                graphics.fill(wellX + SLOT_WELL_SIZE - 1, wellY + 1, wellX + SLOT_WELL_SIZE, wellY + SLOT_WELL_SIZE - 1, HOVER_BORDER_COLOR);
                graphics.fill(wellX + 1, wellY + 1, wellX + SLOT_WELL_SIZE - 1, wellY + SLOT_WELL_SIZE - 1, HOVER_FILL_COLOR);
            }

            ItemStack display = getLiveDisplayItem(slot);
            int count = getLiveCount(slot);

            if (count > 0 && !display.isEmpty()) {
                graphics.renderItem(display, itemX, itemY);
                graphics.renderItemDecorations(font, display, itemX, itemY, "");
                DarkGuiStyling.drawCompactCount(graphics, font, count, itemX, itemY);
            } else if (!display.isEmpty()) {
                drawToolboxGhostItem(graphics, display, itemX, itemY, 0.40F);
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

        // 5. Render right-side world-toolbox switcher using Create's light BUTTON / BUTTON_HOVER (never dark/black)
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

        // 6. Clean hover label centered directly below the panel
        if (tip != null) {
            int alpha = Math.min(255, (int) (fade * 255.0F));
            if (alpha > 8) {
                PoseStack ms = graphics.pose();
                ms.pushPose();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int textW = font.width(tip);
                int color = 0xFFFFFF | ((alpha << 24) & 0xFF000000);
                graphics.drawString(font, tip, px + PANEL_WIDTH / 2 - textW / 2, py + PANEL_HEIGHT + 6, color, true);
                RenderSystem.disableBlend();
                ms.popPose();
            }
        }
    }

    private int resolveHover(int mouseX, int mouseY, int px, int py) {
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

        // 4. Radial direction fallback for quick Alt-flicks around the panel center
        double cx = px + CIRCLE_CENTER_X;
        double cy = py + CIRCLE_CENTER_Y;
        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double distSq = dx * dx + dy * dy;
        if (distSq <= 169.0) {
            return UNEQUIP;
        }
        if (distSq < 14400.0) {
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
        int selected = activeSlot();

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
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
