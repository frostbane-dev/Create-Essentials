package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.client.PortableToolboxOverlay;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying Task 2 (Create-style reserved ghost slot safety, strict 1/64/256 stack caps,
 * zero item loss/duplication) and Task 5 (slot-move synchronization, link re-binding, and live overlay cache).
 */
class PortableToolboxSafetyAndSyncTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void enforcesStrictStackCaps1_64_256WithZeroLoss() {
        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();

        // 64-stackable item -> strict cap 256 (4 x 64)
        for (int i = 0; i < 4; i++) {
            ItemStack rem = toolbox.insertIntoCompartment(0, new ItemStack(Items.COBBLESTONE, 64), false);
            assertTrue(rem.isEmpty(), "Expected full stack insert #" + i);
        }
        assertEquals(256, toolbox.getTotalCount(0));
        ItemStack overflow64 = toolbox.insertIntoCompartment(0, new ItemStack(Items.COBBLESTONE, 17), false);
        assertEquals(17, overflow64.getCount());
        assertEquals(256, toolbox.getTotalCount(0));

        // 16-stackable item -> strict cap 64 (4 x 16)
        for (int i = 0; i < 4; i++) {
            ItemStack rem = toolbox.insertIntoCompartment(1, new ItemStack(Items.ENDER_PEARL, 16), false);
            assertTrue(rem.isEmpty(), "Expected 16-stack insert #" + i);
        }
        assertEquals(64, toolbox.getTotalCount(1));
        ItemStack overflow16 = toolbox.insertIntoCompartment(1, new ItemStack(Items.ENDER_PEARL, 5), false);
        assertEquals(5, overflow16.getCount());
        assertEquals(64, toolbox.getTotalCount(1));

        // Unstackable item -> strict cap 1
        ItemStack remPick1 = toolbox.insertIntoCompartment(2, new ItemStack(Items.DIAMOND_PICKAXE, 1), false);
        assertTrue(remPick1.isEmpty());
        assertEquals(1, toolbox.getTotalCount(2));
        ItemStack remPick2 = toolbox.insertIntoCompartment(2, new ItemStack(Items.DIAMOND_PICKAXE, 1), false);
        assertEquals(1, remPick2.getCount());
        assertEquals(1, toolbox.getTotalCount(2));
    }

    @Test
    void extractingLastItemPreservesGhostFilterAndBlocksUnsafeClear() {
        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();
        toolbox.insertIntoCompartment(0, new ItemStack(Items.STONE, 10), false);

        // Cannot clear filter while items still exist
        assertFalse(toolbox.clearFilter(0));
        assertEquals(10, toolbox.getTotalCount(0));

        // Extract last 10 items -> count reaches 0, ghost filter preserved
        ItemStack extracted = toolbox.extractFromCompartment(0, 64, false);
        assertEquals(10, extracted.getCount());
        assertEquals(Items.STONE, extracted.getItem());
        assertEquals(0, toolbox.getTotalCount(0));
        assertTrue(toolbox.isReservedEmpty(0));
        assertEquals(Items.STONE, toolbox.getFilter(0).getItem());

        // IItemHandler reports empty, while menu display view exposes 1-count ghost stack
        assertTrue(toolbox.getStackInSlot(0).isEmpty());
        ItemStack menuView = toolbox.asMenuDisplayHandler().getStackInSlot(0);
        assertFalse(menuView.isEmpty());
        assertEquals(Items.STONE, menuView.getItem());
        assertEquals(1, menuView.getCount());

        // Explicit clear on empty reserved slot succeeds cleanly
        assertTrue(toolbox.clearFilter(0));
        assertFalse(toolbox.isReservedEmpty(0));
        assertTrue(toolbox.getFilter(0).isEmpty());
        assertTrue(toolbox.asMenuDisplayHandler().getStackInSlot(0).isEmpty());
    }

    @Test
    void ghostFilterSupportsPriorityRoutingAndSafeReplacement() {
        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();

        // Populate then empty compartment 2 (stackable) and compartment 3 (unstackable)
        toolbox.insertIntoCompartment(2, new ItemStack(Items.OAK_PLANKS, 5), false);
        toolbox.extractFromCompartment(2, 5, false);
        assertTrue(toolbox.isReservedEmpty(2));

        toolbox.insertIntoCompartment(3, new ItemStack(Items.DIAMOND_SHOVEL, 1), false);
        toolbox.extractFromCompartment(3, 1, false);
        assertTrue(toolbox.isReservedEmpty(3));

        // Priority routing routes matching items back into empty ghost-reserved slots (never unreserved slots)
        assertTrue(toolbox.insertIntoMatchingCompartment(new ItemStack(Items.OAK_PLANKS, 32), false).isEmpty());
        assertEquals(32, toolbox.getTotalCount(2));
        assertEquals(0, toolbox.getTotalCount(0));

        assertTrue(toolbox.insertIntoMatchingCompartment(new ItemStack(Items.DIAMOND_SHOVEL, 1), false).isEmpty());
        assertEquals(1, toolbox.getTotalCount(3));
        // Second shovel is rejected because strict cap is 1
        assertEquals(1, toolbox.insertIntoMatchingCompartment(new ItemStack(Items.DIAMOND_SHOVEL, 1), false).getCount());

        // Unmatched item is NOT inserted into unreserved compartments
        ItemStack unmatched = toolbox.insertIntoMatchingCompartment(new ItemStack(Items.DIRT, 16), false);
        assertEquals(16, unmatched.getCount());

        // Safe replacement on empty reserved slot replaces ghost filter cleanly
        toolbox.insertIntoCompartment(5, new ItemStack(Items.GLASS, 4), false);
        toolbox.extractFromCompartment(5, 4, false);
        assertTrue(toolbox.isReservedEmpty(5));

        ItemStack rem = toolbox.replaceEmptyReservedAndInsert(5, new ItemStack(Items.SAND, 20));
        assertTrue(rem.isEmpty());
        assertEquals(20, toolbox.getTotalCount(5));
        assertEquals(Items.SAND, toolbox.getFilter(5).getItem());

        // replaceEmptyReservedAndInsert refuses to overwrite a populated slot
        ItemStack refused = toolbox.replaceEmptyReservedAndInsert(5, new ItemStack(Items.GRAVEL, 12));
        assertEquals(12, refused.getCount());
        assertEquals(20, toolbox.getTotalCount(5));
        assertEquals(Items.SAND, toolbox.getTemplate(5).getItem());
    }

    @Test
    void nbtRoundTripPreservesFull256CountsAndEmptyGhostFilters() {
        PortableToolboxInventory source = PortableToolboxInventory.empty();
        for (int i = 0; i < 4; i++) {
            source.insertIntoCompartment(4, new ItemStack(Items.COBBLESTONE, 64), false);
        }
        source.insertIntoCompartment(1, new ItemStack(Items.TORCH, 8), false);
        source.extractFromCompartment(1, 8, false);

        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        CompoundTag serialized = source.serializeNBT(registries);

        PortableToolboxInventory loaded = PortableToolboxInventory.empty();
        loaded.deserializeNBT(registries, serialized);

        assertEquals(256, loaded.getTotalCount(4));
        assertEquals(Items.COBBLESTONE, loaded.getTemplate(4).getItem());
        assertEquals(0, loaded.getTotalCount(1));
        assertTrue(loaded.isReservedEmpty(1));
        assertEquals(Items.TORCH, loaded.getFilter(1).getItem());
    }

    @Test
    void sortingPreservesTotalCountsAndRetainsDistinctGhostFilters() {
        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();
        toolbox.insertIntoCompartment(6, new ItemStack(Items.COBBLESTONE, 40), false);
        toolbox.insertIntoCompartment(2, new ItemStack(Items.COBBLESTONE, 50), false);
        toolbox.insertIntoCompartment(5, new ItemStack(Items.APPLE, 12), false);
        toolbox.insertIntoCompartment(7, new ItemStack(Items.TORCH, 3), false);
        toolbox.extractFromCompartment(7, 3, false); // Ghost reserved torch

        assertTrue(toolbox.sortCompartments(SortMode.COUNT));

        int totalCobble = 0;
        int totalApple = 0;
        boolean hasTorchGhost = false;
        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
            ItemStack t = toolbox.getTemplate(c);
            if (t.is(Items.COBBLESTONE))
                totalCobble += toolbox.getTotalCount(c);
            else if (t.is(Items.APPLE))
                totalApple += toolbox.getTotalCount(c);
            else if (toolbox.isReservedEmpty(c) && toolbox.getFilter(c).is(Items.TORCH))
                hasTorchGhost = true;
        }
        assertEquals(90, totalCobble);
        assertEquals(12, totalApple);
        assertTrue(hasTorchGhost, "Sorting must preserve non-redundant empty ghost reservations");
    }

    @Test
    void moving256ItemsFromSlot1ToSlot5RebindsLinksAndUpdatesLiveOverlayCache() {
        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();
        for (int i = 0; i < 4; i++) {
            toolbox.insertIntoCompartment(0, new ItemStack(Items.COBBLESTONE, 64), false);
        }

        // Simulate hotbar slot 2 linked to compartment 0
        Map<Integer, PortableToolboxHandler.ToolboxLink> links = new HashMap<>();
        links.put(2, new PortableToolboxHandler.ToolboxLink(0, new ItemStack(Items.COBBLESTONE, 1)));

        // Move all 256 cobblestone from compartment 0 -> compartment 4
        for (int i = 0; i < 4; i++) {
            ItemStack moved = toolbox.extractFromCompartment(0, 64, false);
            assertEquals(64, moved.getCount());
            assertTrue(toolbox.insertIntoCompartment(4, moved, false).isEmpty());
        }
        assertEquals(0, toolbox.getTotalCount(0));
        assertTrue(toolbox.isReservedEmpty(0));
        assertEquals(256, toolbox.getTotalCount(4));

        // Re-binding follows moved items from compartment 0 -> compartment 4 even while slot 0 retains ghost filter
        PortableToolboxHandler.rebindMovedLinks(links, toolbox);
        assertNotNull(links.get(2));
        assertEquals(4, links.get(2).compartment());

        // Clear ghost filter on slot 0 and verify link stays firmly bound to slot 4
        assertTrue(toolbox.clearFilter(0));
        PortableToolboxHandler.rebindMovedLinks(links, toolbox);
        assertEquals(4, links.get(2).compartment());

        // Verify live client overlay cache reflects slot 0 empty and slot 4 = 256 immediately
        List<ItemStack> displayItems = new ArrayList<>(PortableToolboxInventory.COMPARTMENTS);
        List<Integer> counts = new ArrayList<>(PortableToolboxInventory.COMPARTMENTS);
        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
            displayItems.add(toolbox.getDisplayTemplate(c));
            counts.add(toolbox.getTotalCount(c));
        }

        PortableToolboxOverlay.clear();
        PortableToolboxOverlay.updateState(1 << 2, true, true, displayItems, counts);
        assertTrue(PortableToolboxOverlay.hasSyncedData());
        assertTrue(PortableToolboxOverlay.hasSyncedBackpack());
        assertEquals(0, PortableToolboxOverlay.getSyncedCount(0));
        assertTrue(PortableToolboxOverlay.getSyncedDisplayItem(0).isEmpty());
        assertEquals(256, PortableToolboxOverlay.getSyncedCount(4));
        assertEquals(Items.COBBLESTONE, PortableToolboxOverlay.getSyncedDisplayItem(4).getItem());
        PortableToolboxOverlay.clear();
    }

    @Test
    void dataComponentRoundTripOnItemStackPreservesMovedSlotsAndGhostFilters() {
        ItemStack dummyStack = new ItemStack(Items.CHEST);
        PortableToolboxInventory writer = PortableToolboxInventory.forStack(dummyStack);

        for (int i = 0; i < 4; i++) {
            assertTrue(writer.insertIntoCompartment(0, new ItemStack(Items.COBBLESTONE, 64), false).isEmpty());
        }
        // Move all 256 from slot 0 -> slot 4
        for (int i = 0; i < 4; i++) {
            ItemStack chunk = writer.extractFromCompartment(0, 64, false);
            assertTrue(writer.insertIntoCompartment(4, chunk, false).isEmpty());
        }

        // Reload fresh from DataComponents (as client/server would)
        PortableToolboxInventory reader1 = PortableToolboxInventory.forStack(dummyStack);
        assertEquals(0, reader1.getTotalCount(0));
        assertTrue(reader1.isReservedEmpty(0));
        assertEquals(Items.COBBLESTONE, reader1.getFilter(0).getItem());
        assertEquals(256, reader1.getTotalCount(4));
        assertEquals(Items.COBBLESTONE, reader1.getTemplate(4).getItem());

        // Clear ghost on slot 0 and verify fresh reload sees slot 0 empty and slot 4 intact
        assertTrue(reader1.clearFilter(0));
        PortableToolboxInventory reader2 = PortableToolboxInventory.forStack(dummyStack);
        assertFalse(reader2.isReservedEmpty(0));
        assertTrue(reader2.getFilter(0).isEmpty());
        assertEquals(256, reader2.getTotalCount(4));
    }

    @Test
    void handleToolboxSlotClickEnforcesZeroLossAcrossPickupGhostClearReplaceAndSwap() {
        net.minecraft.world.SimpleContainer playerInv = new net.minecraft.world.SimpleContainer(36);
        net.minecraft.world.inventory.AbstractContainerMenu menu =
                new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
                    {
                        for (int i = 0; i < 36; i++) {
                            addSlot(new net.minecraft.world.inventory.Slot(playerInv, i, 0, 0));
                        }
                    }

                    @Override
                    public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int index) {
                        return ItemStack.EMPTY;
                    }

                    @Override
                    public boolean stillValid(net.minecraft.world.entity.player.Player player) {
                        return true;
                    }
                };

        PortableToolboxInventory toolbox = PortableToolboxInventory.empty();

        // 1. LMB insert 64 cobble into empty slot 0
        menu.setCarried(new ItemStack(Items.COBBLESTONE, 64));
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(64, toolbox.getTotalCount(0));

        // 2. RMB extract half (32)
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 1, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertEquals(32, menu.getCarried().getCount());
        assertEquals(32, toolbox.getTotalCount(0));

        // 3. Empty cursor + LMB extract last 32 -> preserves ghost filter
        menu.setCarried(ItemStack.EMPTY);
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertEquals(32, menu.getCarried().getCount());
        assertEquals(0, toolbox.getTotalCount(0));
        assertTrue(toolbox.isReservedEmpty(0));
        assertEquals(Items.COBBLESTONE, toolbox.getFilter(0).getItem());

        // 4. LMB different item (16 dirt) onto empty ghost-reserved slot -> safely replaces ghost
        menu.setCarried(new ItemStack(Items.DIRT, 16));
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertTrue(menu.getCarried().isEmpty());
        assertEquals(16, toolbox.getTotalCount(0));
        assertEquals(Items.DIRT, toolbox.getTemplate(0).getItem());

        // 5. LMB swap with 20 stone onto 16 dirt (<= 64) -> atomic lossless swap
        menu.setCarried(new ItemStack(Items.STONE, 20));
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertEquals(Items.DIRT, menu.getCarried().getItem());
        assertEquals(16, menu.getCarried().getCount());
        assertEquals(Items.STONE, toolbox.getTemplate(0).getItem());
        assertEquals(20, toolbox.getTotalCount(0));

        // 6. Fill slot 0 to 128 stone -> LMB swap with 10 gravel must be REFUSED (128 > maxStackSize)
        toolbox.insertIntoCompartment(0, new ItemStack(Items.STONE, 64), false);
        toolbox.insertIntoCompartment(0, new ItemStack(Items.STONE, 44), false);
        assertEquals(128, toolbox.getTotalCount(0));
        menu.setCarried(new ItemStack(Items.GRAVEL, 10));
        assertFalse(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.PICKUP, null, 0, 36, null));
        assertEquals(Items.GRAVEL, menu.getCarried().getItem());
        assertEquals(10, menu.getCarried().getCount());
        assertEquals(128, toolbox.getTotalCount(0));

        // 7. Shift-click (QUICK_MOVE) drains into player inventory and preserves ghost when reaching 0;
        //    next QUICK_MOVE on empty reserved slot clears the ghost safely
        menu.setCarried(ItemStack.EMPTY);
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, null, 0, 36, null));
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, null, 0, 36, null));
        assertEquals(0, toolbox.getTotalCount(0));
        assertTrue(toolbox.isReservedEmpty(0));

        int totalPlayerStone = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = playerInv.getItem(i);
            if (s.is(Items.STONE))
                totalPlayerStone += s.getCount();
        }
        assertEquals(128, totalPlayerStone);

        // Explicit ghost clear on empty reserved slot
        assertTrue(BackpackInventoryOps.handleToolboxSlotClick(
                menu, toolbox, 0, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE, null, 0, 36, null));
        assertFalse(toolbox.isReservedEmpty(0));
        assertTrue(toolbox.getFilter(0).isEmpty());
    }

    @Test
    void portableToolboxMenuEnforcesCreateLayoutAntiNestingAndOpenSlotLocking() {
        net.minecraft.world.entity.player.Inventory playerInv =
                new net.minecraft.world.entity.player.Inventory(null);
        ItemStack activeBackpack = new ItemStack(com.stackultra.essentials.registry.AllItems.MECHANICAL_BACKPACK.get());
        ItemStack nestedShulker = new ItemStack(Items.SHULKER_BOX);
        ItemStack nestedBackpack = new ItemStack(com.stackultra.essentials.registry.AllItems.NETHERITE_BACKPACK.get());

        // Place active backpack in hotbar slot 2, nested containers in main inventory slots 9 & 10
        playerInv.setItem(2, activeBackpack);
        playerInv.setItem(9, nestedShulker);
        playerInv.setItem(10, nestedBackpack);
        playerInv.setItem(11, new ItemStack(Items.OAK_PLANKS, 64));

        PortableToolboxMenu menu = PortableToolboxMenu.create(1, playerInv, activeBackpack);

        // 1. Exact 44-slot Create ToolboxMenu layout (8 circular toolbox + 27 main + 9 hotbar)
        assertEquals(44, menu.slots.size());
        assertEquals(79, menu.slots.get(0).x);
        assertEquals(37, menu.slots.get(0).y);
        assertEquals(112, menu.slots.get(1).x);
        assertEquals(31, menu.slots.get(1).y);
        assertEquals(8, menu.slots.get(8).x);
        assertEquals(165, menu.slots.get(8).y);
        assertEquals(8, menu.slots.get(35).x);
        assertEquals(223, menu.slots.get(35).y);

        // 2. Open-slot locking on hotbar slot 2 (menu slot index 35 + 2 = 37)
        net.minecraft.world.inventory.Slot lockedSlot = menu.slots.get(37);
        assertSame(activeBackpack, lockedSlot.getItem());
        assertFalse(lockedSlot.mayPickup(null), "Active backpack slot must reject mayPickup");
        assertFalse(menu.canDragTo(lockedSlot), "Active backpack slot must reject drag-split");
        assertFalse(menu.canTakeItemForPickAll(activeBackpack, lockedSlot), "Active backpack slot must reject pick-all");
        assertTrue(menu.quickMoveStack(null, 37).isEmpty(), "Shift-click on active backpack must be blocked");
        assertSame(activeBackpack, playerInv.getItem(2));

        // 3. Anti-nesting on toolbox compartments & Shift-click (shulker & nested backpack rejected)
        assertTrue(menu.quickMoveStack(null, 8).isEmpty(), "Shift-click of shulker box must be blocked");
        assertTrue(menu.quickMoveStack(null, 9).isEmpty(), "Shift-click of nested backpack must be blocked");
        assertEquals(Items.SHULKER_BOX, playerInv.getItem(9).getItem());
        assertEquals(com.stackultra.essentials.registry.AllItems.NETHERITE_BACKPACK.get(), playerInv.getItem(10).getItem());

        // 4. Toolbox slots reject drag-split & pick-all
        for (int c = 0; c < PortableToolboxMenu.TOOLBOX_SLOTS; c++) {
            assertFalse(menu.canDragTo(menu.slots.get(c)));
            assertFalse(menu.canTakeItemForPickAll(new ItemStack(Items.OAK_PLANKS), menu.slots.get(c)));
        }
    }
}
