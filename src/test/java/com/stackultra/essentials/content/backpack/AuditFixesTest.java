package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.BlockItemComponentHelper;
import com.stackultra.essentials.content.ItemPullHelper;
import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import com.stackultra.essentials.registry.AllBlocks;
import com.stackultra.essentials.registry.AllItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering Critical #1 (container validity & locked open-item slots),
 * High #3 (pickupDelay guard), High #4 (DataComponent-aware restock matching),
 * and High #5 (place -> break DataComponent preservation & Schematicannon NETHERITE state).
 */
class AuditFixesTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blockEntityValidationRejectsRemovedOrDetachedBlockEntities() {
        MechanicalBackpackBlockEntity be = new MechanicalBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState());
        assertFalse(MenuValidationHelper.isBlockEntityValid(null, be));
        be.setRemoved();
        assertTrue(be.isRemoved());
        assertFalse(MenuValidationHelper.isBlockEntityValid(null, be));
        assertFalse(MenuValidationHelper.isPlayerCarryingStack(null, new ItemStack(AllItems.MECHANICAL_BACKPACK.get())));
        assertFalse(MenuValidationHelper.isPlayerCarryingStack(null, ItemStack.EMPTY));
    }

    @Test
    void lockedOpenStackClickBlocksPickupMoveSwapThrowButAllowsQuickCraftAndOtherSlots() {
        ItemStack openBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        ItemStack otherItem = new ItemStack(Items.COBBLESTONE, 16);
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, openBackpack);
        container.setItem(1, otherItem);

        AbstractContainerMenu dummyMenu = new AbstractContainerMenu(null, 0) {
            {
                addSlot(new Slot(container, 0, 0, 0));
                addSlot(new Slot(container, 1, 18, 0));
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

        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.PICKUP, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.QUICK_MOVE, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.THROW, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.PICKUP_ALL, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 1, ClickType.SWAP, null));

        // Drag-split termination (QUICK_CRAFT) and clicks on unrelated slots must stay allowed
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 2, ClickType.QUICK_CRAFT, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 1, 0, ClickType.PICKUP, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, ItemStack.EMPTY, 0, 0, ClickType.PICKUP, null));
    }

    @Test
    void pickupGuardRejectsDelayedDeadAndEmptyItems() {
        assertFalse(ItemPullHelper.canPullOrPickup(null));
        assertTrue(ItemPullHelper.isPickable(true, false, false));
        assertFalse(ItemPullHelper.isPickable(true, true, false), "Items with active pickupDelay or setNeverPickUp must be skipped");
        assertFalse(ItemPullHelper.isPickable(false, false, false), "Dead entities must be skipped");
        assertFalse(ItemPullHelper.isPickable(true, false, true), "Empty stacks must be skipped");
    }

    @Test
    void restockMatchesExactDataComponentsWithoutPullingRenamedBlocks() {
        BackpackInventory inv = new BackpackInventory();

        ItemStack renamedStone = new ItemStack(Items.STONE, 12);
        renamedStone.set(DataComponents.CUSTOM_NAME, Component.literal("Special Stone"));
        ItemStack plainStone = new ItemStack(Items.STONE, 24);

        inv.setStackInSlot(0, renamedStone);
        inv.setStackInSlot(1, plainStone);

        // Restocking a plain Stone must skip the renamed stack in slot 0 and pull slot 1
        ItemStack extractedPlain = BackpackRestockHandler.extractMatchingRestock(inv, new ItemStack(Items.STONE));
        assertEquals(24, extractedPlain.getCount());
        assertFalse(extractedPlain.has(DataComponents.CUSTOM_NAME));
        assertEquals(12, inv.getStackInSlot(0).getCount(), "Renamed stone in slot 0 must stay untouched");
        assertTrue(inv.getStackInSlot(1).isEmpty());

        // Restocking with a non-matching custom name returns EMPTY
        ItemStack otherNamed = new ItemStack(Items.STONE);
        otherNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Different Name"));
        assertTrue(BackpackRestockHandler.extractMatchingRestock(inv, otherNamed).isEmpty());

        // Restocking with the exact matching renamed template pulls slot 0
        ItemStack matchingNamed = new ItemStack(Items.STONE);
        matchingNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special Stone"));
        ItemStack extractedNamed = BackpackRestockHandler.extractMatchingRestock(inv, matchingNamed);
        assertEquals(12, extractedNamed.getCount());
        assertEquals("Special Stone", extractedNamed.getHoverName().getString());
    }

    @Test
    void mechanicalBackpackBlockEntityInitializesNetheriteFromBlockStateAndPreservesComponents() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        // Schematicannon / /setblock placement with NETHERITE=true without loadFromStack
        BlockState netheriteState = AllBlocks.MECHANICAL_BACKPACK.get()
                .defaultBlockState()
                .setValue(MechanicalBackpackBlock.NETHERITE, true);
        MechanicalBackpackBlockEntity schematicBe = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        assertTrue(schematicBe.isNetherite(), "NETHERITE=true blockstate must initialize isNetherite() to true");

        // Place -> NBT save -> NBT load -> break round-trip preserves CUSTOM_NAME and DYED_COLOR
        ItemStack placed = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        placed.set(DataComponents.CUSTOM_NAME, Component.literal("Builder Pack"));
        placed.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3A7BD5, true));

        MechanicalBackpackBlockEntity be = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        be.loadFromStack(placed);
        assertEquals("Builder Pack", be.getDisplayName().getString());

        CompoundTag saved = new CompoundTag();
        be.saveAdditional(saved, registries);

        MechanicalBackpackBlockEntity reloaded = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        reloaded.loadAdditional(saved, registries);

        ItemStack dropped = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        reloaded.saveToStack(dropped);

        assertEquals("Builder Pack", dropped.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(new DyedItemColor(0x3A7BD5, true), dropped.get(DataComponents.DYED_COLOR));
    }

    @Test
    void magneticBackpackAndRotationalMagnetPreserveCustomNameAndDyeAcrossPlaceBreak() {
        // Magnetic Backpack
        ItemStack placedBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        placedBackpack.set(DataComponents.CUSTOM_NAME, Component.literal("Void Vacuum"));
        placedBackpack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xD95959, true));

        MagneticBackpackBlockEntity backpackBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());
        backpackBe.loadFromStack(placedBackpack);
        assertEquals("Void Vacuum", backpackBe.getDisplayName().getString());

        ItemStack droppedBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        backpackBe.saveToStack(droppedBackpack);
        assertEquals("Void Vacuum", droppedBackpack.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(new DyedItemColor(0xD95959, true), droppedBackpack.get(DataComponents.DYED_COLOR));

        // Rotational / Precision Magnet
        ItemStack placedMagnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        placedMagnet.set(DataComponents.CUSTOM_NAME, Component.literal("Ore Magnet"));

        RotationalMagnetBlockEntity magnetBe = new RotationalMagnetBlockEntity(
                BlockPos.ZERO, AllBlocks.PRECISION_MAGNET.get().defaultBlockState());
        magnetBe.loadFromStack(placedMagnet);
        assertEquals("Ore Magnet", magnetBe.getDisplayName().getString());

        ItemStack droppedMagnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        magnetBe.saveToStack(droppedMagnet);
        assertEquals("Ore Magnet", droppedMagnet.get(DataComponents.CUSTOM_NAME).getString());

        // Direct PreservedComponents NBT round-trip check
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        BlockItemComponentHelper.PreservedComponents helper = new BlockItemComponentHelper.PreservedComponents();
        helper.loadFromStack(placedBackpack);
        CompoundTag tag = new CompoundTag();
        helper.writeToTag(tag, registries);

        BlockItemComponentHelper.PreservedComponents restored = new BlockItemComponentHelper.PreservedComponents();
        restored.readFromTag(tag, registries);
        assertEquals("Void Vacuum", restored.getCustomName().getString());
        assertEquals(new DyedItemColor(0xD95959, true), restored.getDyedColor());
    }
}
