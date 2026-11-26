package com.stackultra.essentials.client;

import com.simibubi.create.AllKeys;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Create-style 8-slot radial toolbox menu for the portable backpack toolbox.
 * Supports hold-and-release Alt (AllKeys.TOOLBELT), click selection, scroll-wheel cycling,
 * center Return action, and clean handoff to nearby placed Create Toolboxes.
 */
@OnlyIn(Dist.CLIENT)
public class PortableToolboxRadialScreen extends Screen {

    private static final int COMPARTMENTS = PortableToolboxInventory.COMPARTMENTS;
    private static final int RETURN_SELECTION = -1;
    private static final int NO_SELECTION = -2;

    private static final int WHEEL_RADIUS = 54;
    private static final int CENTER_DEADZONE = 21;
    private static final int SLOT_BOX_SIZE = 24;

    private final List<ToolboxBlockEntity> nearbyWorldToolboxes;
    private final ItemStack backpackStack;
    private final PortableToolboxInventory snapshot;
    private final BackpackTooltipPalette palette;

    private int hoveredSelection = RETURN_SELECTION;
    private boolean scrollMode = false;
    private int scrollSlot = 0;
    private boolean actionCommitted = false;

    public PortableToolboxRadialScreen(ItemStack backpackStack, List<ToolboxBlockEntity> nearbyWorldToolboxes) {
        super(backpackStack.getHoverName());
        this.backpackStack = backpackStack;
        this.nearbyWorldToolboxes = nearbyWorldToolboxes;
        this.snapshot = PortableToolboxInventory.forStack(backpackStack);
        BackpackTooltipPalette resolved = BackpackTooltipPalette.resolveForStack(backpackStack);
        this.palette = resolved != null ? resolved : BackpackTooltipPalette.resolve(
                com.stackultra.essentials.content.backpack.BackpackTooltipData.Tier.MECHANICAL, null, -1);
    }

    @Override
    protected void init() {
        super.init();
        if (!nearbyWorldToolboxes.isEmpty()) {
            int btnW = 108;
            int btnH = 16;
            int bx = (width - btnW) / 2;
            int by = height / 2 + WHEEL_RADIUS + 26;
            addRenderableWidget(new DarkTextButton(bx, by, btnW, btnH,
                    () -> Component.translatable("createessentials.gui.world_toolboxes", nearbyWorldToolboxes.size()),
                    () -> DarkGuiStyling.COLOR_TITLE,
                    this::switchToWorldToolboxes));
        }
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Keep world clearly visible behind the radial wheel (matching Create's RadialToolboxMenu)
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;

        if (!scrollMode) {
            hoveredSelection = resolveHover(mouseX, mouseY, cx, cy);
        } else {
            hoveredSelection = scrollSlot;
        }

        // Subtle dark backdrop behind wheel
        graphics.fill(cx - WHEEL_RADIUS - 26, cy - WHEEL_RADIUS - 26,
                cx + WHEEL_RADIUS + 26, cy + WHEEL_RADIUS + 26, 0x55121216);

        // Title above wheel
        graphics.drawCenteredString(font, title, cx, cy - WHEEL_RADIUS - 36, DarkGuiStyling.COLOR_TITLE);

        // Render 8 radial compartment nodes
        for (int c = 0; c < COMPARTMENTS; c++) {
            double angle = Math.toRadians(c * 45.0 - 90.0);
            int nx = cx + (int) Math.round(Math.cos(angle) * WHEEL_RADIUS);
            int ny = cy + (int) Math.round(Math.sin(angle) * WHEEL_RADIUS);
            boolean selected = hoveredSelection == c;

            drawRadialNode(graphics, nx, ny, selected, c);
        }

        // Center Return hub
        boolean returnSelected = hoveredSelection == RETURN_SELECTION;
        drawCenterReturnNode(graphics, cx, cy, returnSelected);

        // Active selection label below wheel
        Component selectionLabel = getSelectionLabel(hoveredSelection);
        if (!selectionLabel.getString().isEmpty()) {
            graphics.drawCenteredString(font, selectionLabel, cx, cy + WHEEL_RADIUS + 14, DarkGuiStyling.COLOR_TEXT);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRadialNode(GuiGraphics g, int nx, int ny, boolean selected, int compartment) {
        int half = SLOT_BOX_SIZE / 2;
        int x = nx - half;
        int y = ny - half;

        int border = selected ? DarkGuiStyling.COLOR_TITLE : palette.panelOutline();
        int fill = selected ? 0xFF5E5E5E : palette.panelBg();
        g.fill(x - 1, y - 1, x + SLOT_BOX_SIZE + 1, y + SLOT_BOX_SIZE + 1, border);
        g.fill(x, y, x + SLOT_BOX_SIZE, y + SLOT_BOX_SIZE, fill);

        int itemX = nx - 8;
        int itemY = ny - 8;
        DarkGuiStyling.drawSlot(g, itemX, itemY);

        int count = snapshot.getTotalCount(compartment);
        ItemStack filter = snapshot.getFilter(compartment);
        if (count > 0 && !filter.isEmpty()) {
            g.renderItem(filter, itemX, itemY);
            g.renderItemDecorations(font, filter, itemX, itemY, "");
            DarkGuiStyling.drawCompactCount(g, font, count, itemX, itemY);
        } else if (!filter.isEmpty()) {
            DarkGuiStyling.drawGhostItem(g, filter, itemX, itemY, 0.35F);
            DarkGuiStyling.drawReservedMarker(g, itemX, itemY);
        }
    }

    private void drawCenterReturnNode(GuiGraphics g, int cx, int cy, boolean selected) {
        int r = 16;
        int border = selected ? 0xFFFF6666 : palette.panelOutline();
        int fill = selected ? 0xFF5A3838 : palette.panelBg();
        g.fill(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1, border);
        g.fill(cx - r, cy - r, cx + r, cy + r, fill);
        g.fill(cx - r + 1, cy - r + 1, cx + r - 1, cy - r + 2, palette.panelBevelLight());
        g.fill(cx - r + 1, cy + r - 2, cx + r - 1, cy + r - 1, palette.panelBevelDark());

        Component label = Component.translatable("createessentials.gui.radial_return");
        g.drawCenteredString(font, label, cx, cy - 4, selected ? 0xFFFF7777 : DarkGuiStyling.COLOR_TEXT_DIM);
    }

    private Component getSelectionLabel(int selection) {
        if (selection == RETURN_SELECTION) {
            return Component.translatable("createessentials.gui.radial_return_desc")
                    .withStyle(ChatFormatting.GRAY);
        }
        if (selection >= 0 && selection < COMPARTMENTS) {
            ItemStack filter = snapshot.getFilter(selection);
            int count = snapshot.getTotalCount(selection);
            if (!filter.isEmpty()) {
                return Component.literal(filter.getHoverName().getString() + " (" + count + ")")
                        .withStyle(count > 0 ? ChatFormatting.WHITE : ChatFormatting.YELLOW);
            }
            return Component.translatable("createessentials.tooltip.backpack_empty")
                    .withStyle(ChatFormatting.DARK_GRAY);
        }
        return Component.empty();
    }

    private int resolveHover(int mouseX, int mouseY, int cx, int cy) {
        // Ignore hover when hovering the World Toolboxes button below the wheel
        if (!nearbyWorldToolboxes.isEmpty() && mouseY > cy + WHEEL_RADIUS + 20)
            return NO_SELECTION;

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.hypot(dx, dy);
        if (dist < CENTER_DEADZONE)
            return RETURN_SELECTION;

        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        if (angle < 0)
            angle += 360.0;
        return ((int) Math.round(angle / 45.0)) % COMPARTMENTS;
    }

    private void commitSelection() {
        if (actionCommitted)
            return;
        actionCommitted = true;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null)
            return;

        int hotbarSlot = player.getInventory().selected;
        if (hoveredSelection == RETURN_SELECTION) {
            PacketDistributor.sendToServer(BackpackToolboxPayload.returnHeld(hotbarSlot));
        } else if (hoveredSelection >= 0 && hoveredSelection < COMPARTMENTS) {
            if (!snapshot.getFilter(hoveredSelection).isEmpty()) {
                PacketDistributor.sendToServer(BackpackToolboxPayload.equip(hoveredSelection, hotbarSlot));
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button))
            return true;

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            actionCommitted = true;
            onClose();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            commitSelection();
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0)
            return false;
        scrollMode = true;
        int dir = scrollY > 0 ? -1 : 1;
        scrollSlot = (scrollSlot + dir + COMPARTMENTS) % COMPARTMENTS;
        return true;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        scrollMode = false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            actionCommitted = true;
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (AllKeys.TOOLBELT.doesModifierAndCodeMatch(keyCode)) {
            commitSelection();
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || PortableToolboxHandler.findPortableToolboxBackpack(mc.player).isEmpty()) {
            actionCommitted = true;
            onClose();
        }
    }
}
