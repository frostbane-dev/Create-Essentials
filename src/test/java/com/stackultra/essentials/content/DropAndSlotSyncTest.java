package com.stackultra.essentials.content;

import com.stackultra.essentials.registry.AllItems;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for visual ghost-duplication fixes when dropping magnets or backpacks with Q:
 * <ul>
 *   <li>Authoritative post-drop hotbar slot sync defeating vanilla {@code ServerPlayer.drop}'s
 *       silent {@code setRemoteSlot} race against per-tick slot sync packets.</li>
 *   <li>Client-side locked open-slot click protection using synced {@link Inventory} slot indices
 *       so GUI {@code Q}/PICKUP/SWAP never mutates local slots into ghost copies.</li>
 * </ul>
 */
class DropAndSlotSyncTest {

    private record CapturedSlotPacket(int containerId, int stateId, int slotIndex, ItemStack stack) {
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dropSyncTargetsAllMagnetsAndBackpacksOnly() {
        assertTrue(DropSyncHandler.isDropSyncedItem(new ItemStack(AllItems.ROTATIONAL_MAGNET.get())));
        assertTrue(DropSyncHandler.isDropSyncedItem(new ItemStack(AllItems.PRECISION_MAGNET.get())));
        assertTrue(DropSyncHandler.isDropSyncedItem(new ItemStack(AllItems.MECHANICAL_BACKPACK.get())));
        assertTrue(DropSyncHandler.isDropSyncedItem(new ItemStack(AllItems.NETHERITE_BACKPACK.get())));
        assertTrue(DropSyncHandler.isDropSyncedItem(new ItemStack(AllItems.MAGNETIC_BACKPACK.get())));

        assertFalse(DropSyncHandler.isDropSyncedItem(ItemStack.EMPTY));
        assertFalse(DropSyncHandler.isDropSyncedItem(null));
        assertFalse(DropSyncHandler.isDropSyncedItem(new ItemStack(Items.COBBLESTONE)));
    }

    @Test
    void droppedHotbarSlotSendsAuthoritativeEmptySlotPacketWithExactInventoryMenuMapping() {
        Inventory inventory = new Inventory(null);
        InventoryMenu menu = new InventoryMenu(inventory, true, null);

        for (int hotbarSlot = 0; hotbarSlot < Inventory.getSelectionSize(); hotbarSlot++) {
            ItemStack magnet = new ItemStack(AllItems.ROTATIONAL_MAGNET.get());
            inventory.selected = hotbarSlot;
            inventory.setItem(hotbarSlot, magnet);

            int expectedMenuSlot = InventoryMenu.USE_ROW_SLOT_START + hotbarSlot;
            assertEquals(expectedMenuSlot, DropSyncHandler.resolveHotbarMenuSlot(menu, inventory, hotbarSlot));

            // Simulate vanilla ServerPlayer.drop():
            // 1. In-flight tick packet had synced magnet to remoteSlots
            menu.setRemoteSlot(expectedMenuSlot, magnet);
            // 2. Server removes dropped item and silently sets remoteSlots to EMPTY (no packet sent)
            ItemStack removed = inventory.removeFromSelected(false);
            assertFalse(removed.isEmpty());
            assertTrue(inventory.getItem(hotbarSlot).isEmpty());
            menu.setRemoteSlot(expectedMenuSlot, inventory.getSelected());

            int stateBefore = menu.getStateId();
            List<CapturedSlotPacket> sent = new ArrayList<>();
            boolean synced = DropSyncHandler.syncDroppedHotbarSlot(
                    menu, inventory, hotbarSlot,
                    (cid, sid, idx, stack) -> sent.add(new CapturedSlotPacket(cid, sid, idx, stack)));

            assertTrue(synced);
            assertEquals(1, sent.size());
            CapturedSlotPacket packet = sent.get(0);
            assertEquals(InventoryMenu.CONTAINER_ID, packet.containerId());
            assertEquals((stateBefore + 1) & 32767, packet.stateId());
            assertEquals(expectedMenuSlot, packet.slotIndex());
            assertTrue(packet.stack().isEmpty(), "Authoritative post-drop packet must clear any client ghost copy");
        }

        // Invalid hotbar indices must safely no-op
        assertEquals(-1, DropSyncHandler.resolveHotbarMenuSlot(menu, inventory, -1));
        assertEquals(-1, DropSyncHandler.resolveHotbarMenuSlot(menu, inventory, Inventory.getSelectionSize()));
        assertFalse(DropSyncHandler.syncDroppedHotbarSlot(menu, inventory, -1, (a, b, c, d) -> fail("Must not send")));
    }

    @Test
    void droppedHotbarSyncUsesActiveContainerMenuIdAndSlotIndexWhenOpen() {
        Inventory inventory = new Inventory(null);
        SimpleContainer chest = new SimpleContainer(27);
        AbstractContainerMenu customMenu = new AbstractContainerMenu(null, 7) {
            {
                for (int i = 0; i < 27; i++)
                    addSlot(new Slot(chest, i, 0, 0));
                for (int i = 0; i < 9; i++)
                    addSlot(new Slot(inventory, i, 0, 0));
            }

            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };

        int hotbarSlot = 4;
        inventory.selected = hotbarSlot;
        inventory.setItem(hotbarSlot, new ItemStack(AllItems.MAGNETIC_BACKPACK.get()));
        inventory.removeFromSelected(false);

        List<CapturedSlotPacket> sent = new ArrayList<>();
        assertTrue(DropSyncHandler.syncDroppedHotbarSlot(
                customMenu, inventory, hotbarSlot,
                (cid, sid, idx, stack) -> sent.add(new CapturedSlotPacket(cid, sid, idx, stack))));

        assertEquals(1, sent.size());
        assertEquals(7, sent.get(0).containerId());
        assertEquals(27 + hotbarSlot, sent.get(0).slotIndex());
        assertTrue(sent.get(0).stack().isEmpty());
    }

    @Test
    void clientSideLockedInventorySlotBlocksThrowPickupSwapWithoutServerStackReference() {
        Inventory inventory = new Inventory(null);
        inventory.setItem(2, new ItemStack(AllItems.MECHANICAL_BACKPACK.get()));
        inventory.setItem(5, new ItemStack(Items.IRON_INGOT, 16));
        inventory.setItem(14, new ItemStack(AllItems.PRECISION_MAGNET.get()));

        AbstractContainerMenu clientMenu = new AbstractContainerMenu(null, 3) {
            {
                // Slot 0 -> main inventory slot 14 (Precision Magnet)
                addSlot(new Slot(inventory, 14, 0, 0));
                // Slot 1 -> hotbar slot 2 (Mechanical Backpack)
                addSlot(new Slot(inventory, 2, 18, 0));
                // Slot 2 -> hotbar slot 5 (Iron Ingot)
                addSlot(new Slot(inventory, 5, 36, 0));
            }

            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };

        // On client, openStack is ItemStack.EMPTY; locking relies on synced lockedInventorySlot = 2 (hotbar 2)
        int lockedHotbar = 2;
        assertTrue(MenuValidationHelper.isLockedSlot(clientMenu.getSlot(1), inventory, ItemStack.EMPTY, lockedHotbar));
        assertFalse(MenuValidationHelper.isLockedSlot(clientMenu.getSlot(0), inventory, ItemStack.EMPTY, lockedHotbar));
        assertFalse(MenuValidationHelper.isLockedSlot(clientMenu.getSlot(2), inventory, ItemStack.EMPTY, lockedHotbar));

        // Direct Q (THROW), PICKUP, QUICK_MOVE, SWAP, PICKUP_ALL on locked slot 1 must be blocked client-side
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 0, ClickType.THROW, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 1, ClickType.THROW, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 0, ClickType.PICKUP, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 0, ClickType.QUICK_MOVE, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 5, ClickType.SWAP, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 0, ClickType.PICKUP_ALL, null));

        // Hotbar key '3' (button 2) SWAP from another hovered slot targeting locked hotbar slot 2 must be blocked
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 2, 2, ClickType.SWAP, null));

        // Offhand SWAP (button 40) targeting an open offhand item must be blocked
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(
                clientMenu, ItemStack.EMPTY, MenuValidationHelper.OFFHAND_SLOT, 2, MenuValidationHelper.OFFHAND_SLOT, ClickType.SWAP, null));

        // QUICK_CRAFT drag termination and clicks on unlocked slots stay allowed
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 1, 2, ClickType.QUICK_CRAFT, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 2, 0, ClickType.THROW, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(clientMenu, ItemStack.EMPTY, lockedHotbar, 0, 0, ClickType.PICKUP, null));
    }
}
