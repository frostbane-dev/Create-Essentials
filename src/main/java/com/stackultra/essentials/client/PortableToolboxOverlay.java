package com.stackultra.essentials.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Arrays;
import java.util.List;

/**
 * Renders Create's native locked-hotbar-slot highlight over any hotbar slot (0-8)
 * currently bound to a portable backpack toolbox compartment, and caches the latest
 * server-authoritative 8-compartment toolbox snapshot for real-time radial wheel reads.
 */
@OnlyIn(Dist.CLIENT)
public final class PortableToolboxOverlay {

    public static final LayeredDraw.Layer OVERLAY = PortableToolboxOverlay::renderOverlay;

    private static final int COMPARTMENTS = PortableToolboxInventory.COMPARTMENTS;

    private static int lockedHotbarMask = 0;
    private static boolean restockActive = true;
    private static boolean hasSyncedData = false;
    private static boolean hasSyncedBackpack = false;
    private static final ItemStack[] syncedDisplayItems = new ItemStack[COMPARTMENTS];
    private static final int[] syncedCounts = new int[COMPARTMENTS];

    static {
        Arrays.fill(syncedDisplayItems, ItemStack.EMPTY);
    }

    private PortableToolboxOverlay() {
    }

    public static void updateState(int mask,
                                   boolean restock,
                                   boolean hasBackpack,
                                   List<ItemStack> displayItems,
                                   List<Integer> counts) {
        lockedHotbarMask = mask;
        restockActive = restock;
        hasSyncedData = true;
        hasSyncedBackpack = hasBackpack;
        for (int c = 0; c < COMPARTMENTS; c++) {
            ItemStack item = (displayItems != null && c < displayItems.size() && displayItems.get(c) != null)
                    ? displayItems.get(c).copy()
                    : ItemStack.EMPTY;
            int count = (counts != null && c < counts.size() && counts.get(c) != null)
                    ? Math.max(0, counts.get(c))
                    : 0;
            syncedDisplayItems[c] = item;
            syncedCounts[c] = count;
        }
    }

    public static boolean hasSyncedData() {
        return hasSyncedData;
    }

    public static boolean hasSyncedBackpack() {
        return hasSyncedBackpack;
    }

    public static ItemStack getSyncedDisplayItem(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return ItemStack.EMPTY;
        return syncedDisplayItems[compartment];
    }

    public static int getSyncedCount(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return 0;
        return syncedCounts[compartment];
    }

    public static void clear() {
        lockedHotbarMask = 0;
        restockActive = true;
        hasSyncedData = false;
        hasSyncedBackpack = false;
        Arrays.fill(syncedDisplayItems, ItemStack.EMPTY);
        Arrays.fill(syncedCounts, 0);
    }

    public static void renderOverlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (lockedHotbarMask == 0)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
            return;

        Player player = mc.player;
        if (player == null)
            return;

        // Avoid double-drawing if Create's placed Toolbox overlay already renders on the same slot
        CompoundTag createToolboxData = player.getPersistentData().contains("CreateToolboxData")
                ? player.getPersistentData().getCompound("CreateToolboxData")
                : null;

        int x = graphics.guiWidth() / 2 - 90;
        int y = graphics.guiHeight() - 23;

        RenderSystem.enableDepthTest();
        graphics.pose().pushPose();

        for (int slot = 0; slot < 9; slot++) {
            if ((lockedHotbarMask & (1 << slot)) == 0)
                continue;
            if (createToolboxData != null && createToolboxData.contains(String.valueOf(slot)))
                continue;

            boolean selected = player.getInventory().selected == slot;
            int offset = selected ? 1 : 0;

            AllGuiTextures texture = restockActive
                    ? (selected ? AllGuiTextures.TOOLBELT_SELECTED_ON : AllGuiTextures.TOOLBELT_HOTBAR_ON)
                    : (selected ? AllGuiTextures.TOOLBELT_SELECTED_OFF : AllGuiTextures.TOOLBELT_HOTBAR_OFF);

            texture.render(graphics, x + 20 * slot - offset, y + offset);
        }

        graphics.pose().popPose();
    }
}
