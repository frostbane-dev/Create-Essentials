package com.stackultra.essentials.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.AllKeys;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.createmod.catnip.math.AngleHelper;
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
 * Exact visual clone of Create 6.0.9's {@link RadialToolboxMenu} backed by our
 * server-authoritative portable backpack toolbox sync ({@link PortableToolboxOverlay},
 * {@link BackpackToolboxPayload}, and Curios/inventory lookup).
 */
@OnlyIn(Dist.CLIENT)
public class PortableToolboxRadialScreen extends AbstractSimiScreen {

    private static final int COMPARTMENTS = PortableToolboxInventory.COMPARTMENTS;
    private static final int WORLD_TOOLBOXES = -7;
    private static final int UNEQUIP = -5;

    private final List<ToolboxBlockEntity> nearbyWorldToolboxes;
    private final PortableToolboxInventory fallbackSnapshot;

    private int ticksOpen;
    private int hoveredSlot = -1;
    private boolean scrollMode;
    private int scrollSlot = 0;
    private boolean actionCommitted = false;

    public PortableToolboxRadialScreen(ItemStack backpackStack, List<ToolboxBlockEntity> nearbyWorldToolboxes) {
        super();
        this.nearbyWorldToolboxes = nearbyWorldToolboxes;
        this.fallbackSnapshot = PortableToolboxInventory.forStack(backpackStack);
    }

    @Override
    protected void init() {
        super.init();
        // Immediately request fresh authoritative state from server in case Curios/inventory changed
        PacketDistributor.sendToServer(BackpackToolboxPayload.requestSync());
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

        hoveredSlot = -1;
        Window window = getMinecraft().getWindow();
        float hoveredX = mouseX - window.getGuiScaledWidth() / 2f;
        float hoveredY = mouseY - window.getGuiScaledHeight() / 2f;

        float distance = hoveredX * hoveredX + hoveredY * hoveredY;
        if (distance > 25 && distance < 10000) {
            hoveredSlot = (Mth.floor((AngleHelper.deg(Mth.atan2(hoveredY, hoveredX)) + 360 + 180 - 22.5f)) % 360) / 45;
        }
        if (scrollMode && distance > 150) {
            scrollMode = false;
        }
        if (distance <= 150) {
            hoveredSlot = UNEQUIP;
        }

        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(width / 2f, height / 2f, 0);
        Component tip = null;

        // Exact Create right-side action slot (switches to nearby placed Create Toolboxes if present)
        if (!nearbyWorldToolboxes.isEmpty()) {
            if (hoveredX > 60 && hoveredX < 100 && hoveredY > -20 && hoveredY < 20) {
                hoveredSlot = WORLD_TOOLBOXES;
            }
            ms.pushPose();
            ms.translate(80 + -5 * (1 - fade) * (1 - fade), 0, 0);
            AllGuiTextures.TOOLBELT_SLOT.render(graphics, -12, -12);
            ms.translate(-0.5, 0.5, 0);
            AllIcons.I_TOOLBOX.render(graphics, -9, -9);
            ms.translate(0.5, -0.5, 0);
            if (!scrollMode && hoveredSlot == WORLD_TOOLBOXES) {
                AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, -13, -13);
                tip = Component.translatable("createessentials.gui.world_toolboxes", nearbyWorldToolboxes.size())
                        .withStyle(ChatFormatting.GOLD);
            }
            ms.popPose();
        }

        // Exact Create 8-slot radial wheel layout, ease-out animation, textures, and item rendering
        for (int slot = 0; slot < COMPARTMENTS; slot++) {
            ms.pushPose();
            TransformStack.of(ms)
                    .rotateZDegrees(slot * 45 - 45)
                    .translate(0, -40 + 10 * (1 - fade) * (1 - fade), 0)
                    .rotateZDegrees(-slot * 45 + 45);
            ms.translate(-12, -12, 0);

            ItemStack display = getLiveDisplayItem(slot);
            if (!display.isEmpty()) {
                boolean empty = getLiveCount(slot) <= 0;
                (empty ? AllGuiTextures.TOOLBELT_INACTIVE_SLOT : AllGuiTextures.TOOLBELT_SLOT)
                        .render(graphics, 0, 0);
                GuiGameElement.of(display)
                        .at(3, 3)
                        .render(graphics);

                if (slot == (scrollMode ? scrollSlot : hoveredSlot) && !empty) {
                    AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, -1, -1);
                    tip = display.getHoverName();
                }
            } else {
                AllGuiTextures.TOOLBELT_EMPTY_SLOT.render(graphics, 0, 0);
            }

            ms.popPose();
        }

        // Exact Create center unequip / return slot
        ms.pushPose();
        AllGuiTextures.TOOLBELT_SLOT.render(graphics, -12, -12);
        (scrollMode ? AllIcons.I_REFRESH : AllIcons.I_FLIP).render(graphics, -9, -9);
        if (!scrollMode && hoveredSlot == UNEQUIP) {
            AllGuiTextures.TOOLBELT_SLOT_HIGHLIGHT.render(graphics, -13, -13);
            ItemStack mainHand = minecraft != null && minecraft.player != null
                    ? minecraft.player.getMainHandItem()
                    : ItemStack.EMPTY;
            tip = (!mainHand.isEmpty()
                    ? CreateLang.translateDirect("toolbox.unequip", mainHand.getHoverName())
                    : Component.translatable("createessentials.gui.radial_return_desc"))
                    .withStyle(ChatFormatting.GOLD);
        }
        ms.popPose();

        ms.popPose();

        // Exact Create bottom label fade-in
        if (tip != null) {
            int i1 = (int) (fade * 255.0F);
            if (i1 > 255) {
                i1 = 255;
            }
            if (i1 > 8) {
                ms.pushPose();
                ms.translate(width / 2f, height - 68, 0);
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                int k1 = 0xFFFFFF;
                int k = i1 << 24 & 0xFF000000;
                int l = font.width(tip);
                graphics.drawString(font, tip, Math.round(-l / 2f), -4, k1 | k, false);
                RenderSystem.disableBlend();
                ms.popPose();
            }
        }
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
        Window window = getMinecraft().getWindow();
        double hoveredX = mouseX - window.getGuiScaledWidth() / 2.0;
        double hoveredY = mouseY - window.getGuiScaledHeight() / 2.0;
        double distance = hoveredX * hoveredX + hoveredY * hoveredY;
        if (distance <= 150.0) {
            scrollMode = true;
            scrollSlot = (((int) (scrollSlot - scrollY)) + COMPARTMENTS) % COMPARTMENTS;
            for (int i = 0; i < 10; i++) {
                if (!getLiveDisplayItem(scrollSlot).isEmpty() && getLiveCount(scrollSlot) > 0) {
                    break;
                }
                scrollSlot = (scrollSlot - Mth.sign(scrollY) + COMPARTMENTS) % COMPARTMENTS;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
