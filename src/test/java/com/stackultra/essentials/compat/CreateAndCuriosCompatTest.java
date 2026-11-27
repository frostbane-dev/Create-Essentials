package com.stackultra.essentials.compat;

import com.mojang.serialization.DataResult;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorage;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.stackultra.essentials.compat.create.AllCreateCompat;
import com.stackultra.essentials.compat.create.BackpackArmInteractionPointType;
import com.stackultra.essentials.compat.create.BackpackDisplaySources;
import com.stackultra.essentials.compat.create.BackpackMountedStorage;
import com.stackultra.essentials.compat.curios.CuriosClientSetup;
import com.stackultra.essentials.compat.curios.CuriosCompat;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.content.backpack.BackpackEquipment;
import com.stackultra.essentials.content.backpack.BackpackInventory;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlock;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MagneticBackpackEquipment;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlock;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.content.backpack.SortMode;
import com.stackultra.essentials.content.magnet.MagnetEquipment;
import com.stackultra.essentials.registry.AllBlocks;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllItems;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests verifying optional Curios classloading isolation and full native Create integration:
 * IItemHandler anti-nesting, Comparator output, Display Link sources, Mechanical Arm point,
 * Wrench horizontal rotation, and Contraption MountedItemStorage codec + unmount round-trips.
 */
class CreateAndCuriosCompatTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void placedBackpacksExposeItemHandlerAndRejectNestedContainers() {
        MechanicalBackpackBlockEntity mechBe = new MechanicalBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState());
        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());

        for (IItemHandler handler : List.of(mechBe.getItemHandler(), magBe.getItemHandler())) {
            assertEquals(BackpackInventory.SLOTS, handler.getSlots());

            ItemStack iron = new ItemStack(Items.IRON_INGOT, 32);
            assertTrue(handler.isItemValid(0, iron));
            assertTrue(handler.insertItem(0, iron, false).isEmpty());
            assertEquals(32, handler.getStackInSlot(0).getCount());

            // Nested containers must be rejected by both isItemValid and insertItem
            List<ItemStack> forbidden = List.of(
                    new ItemStack(AllItems.MECHANICAL_BACKPACK.get()),
                    new ItemStack(AllItems.NETHERITE_BACKPACK.get()),
                    new ItemStack(AllItems.MAGNETIC_BACKPACK.get()),
                    new ItemStack(Items.SHULKER_BOX),
                    new ItemStack(Items.BUNDLE)
            );
            for (ItemStack nested : forbidden) {
                assertFalse(handler.isItemValid(1, nested), "Must reject nested container: " + nested.getItem());
                ItemStack remainder = handler.insertItem(1, nested.copy(), false);
                assertEquals(nested.getCount(), remainder.getCount(), "insertItem must return nested container untouched");
                assertTrue(handler.getStackInSlot(1).isEmpty());
            }
        }
    }

    @Test
    void comparatorSignalScalesFromZeroWhenEmptyToFifteenWhenFull() {
        BlockState mechState = AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState();
        BlockState magState = AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState();

        assertTrue(mechState.hasAnalogOutputSignal());
        assertTrue(magState.hasAnalogOutputSignal());

        MechanicalBackpackBlockEntity mechBe = new MechanicalBackpackBlockEntity(BlockPos.ZERO, mechState);
        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(BlockPos.ZERO, magState);

        for (IItemHandler handler : List.of(mechBe.getItemHandler(), magBe.getItemHandler())) {
            assertEquals(0, ItemHandlerHelper.calcRedstoneFromInventory(handler), "Empty backpack must emit 0");

            handler.insertItem(0, new ItemStack(Items.COBBLESTONE, 1), false);
            int oneItemSignal = ItemHandlerHelper.calcRedstoneFromInventory(handler);
            assertEquals(1, oneItemSignal, "Non-empty backpack must emit at least 1");

            // Fill all 27 slots completely
            for (int i = 0; i < handler.getSlots(); i++) {
                handler.insertItem(i, new ItemStack(Items.COBBLESTONE, 64), false);
            }
            assertEquals(15, ItemHandlerHelper.calcRedstoneFromInventory(handler), "Full backpack must emit 15");
        }
    }

    @Test
    void displaySourcesAndArmPointTypeAreRegisteredAndComputeAccurateMetrics() {
        BackpackInventory inv = new BackpackInventory();
        assertEquals(0.0F, BackpackDisplaySources.computeFillRatio(inv), 1e-5F);
        assertEquals(0, BackpackDisplaySources.computeTotalItemCount(inv));

        // 1 full stack + 1 non-stackable item = 2 full slots out of 27
        inv.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inv.setStackInSlot(1, new ItemStack(Items.DIAMOND_PICKAXE));
        assertEquals(2.0F / 27.0F, BackpackDisplaySources.computeFillRatio(inv), 1e-4F);
        assertEquals(65, BackpackDisplaySources.computeTotalItemCount(inv));

        // Charge ratio helper
        assertEquals(0.0F, BackpackDisplaySources.computeChargeRatio(0, 6000), 1e-5F);
        assertEquals(0.5F, BackpackDisplaySources.computeChargeRatio(3000, 6000), 1e-5F);
        assertEquals(1.0F, BackpackDisplaySources.computeChargeRatio(6000, 6000), 1e-5F);
        assertEquals(0.0F, BackpackDisplaySources.computeChargeRatio(100, 0), 1e-5F);

        // DisplaySource.BY_BLOCK registrations
        List<DisplaySource> mechSources = DisplaySource.BY_BLOCK.get(AllBlocks.MECHANICAL_BACKPACK.get());
        assertTrue(mechSources.contains(AllCreateCompat.BACKPACK_FILL_LEVEL.get()));
        assertTrue(mechSources.contains(AllCreateCompat.BACKPACK_ITEM_COUNT.get()));
        assertFalse(mechSources.contains(AllCreateCompat.BACKPACK_CHARGE.get()));

        List<DisplaySource> magSources = DisplaySource.BY_BLOCK.get(AllBlocks.MAGNETIC_BACKPACK.get());
        assertTrue(magSources.contains(AllCreateCompat.BACKPACK_FILL_LEVEL.get()));
        assertTrue(magSources.contains(AllCreateCompat.BACKPACK_ITEM_COUNT.get()));
        assertTrue(magSources.contains(AllCreateCompat.BACKPACK_CHARGE.get()));

        // Arm interaction point matching
        BackpackArmInteractionPointType armType = AllCreateCompat.BACKPACK_ARM_POINT.get();
        assertTrue(armType.canCreatePoint(null, BlockPos.ZERO, AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState()));
        assertTrue(armType.canCreatePoint(null, BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState()));
        assertFalse(armType.canCreatePoint(null, BlockPos.ZERO, Blocks.CHEST.defaultBlockState()));
    }

    @Test
    void mountedStorageRoundTripsCodecEnforcesAntiNestingAndPreservesAllBackpackData() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        RegistryOps<Tag> nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries);

        // Verify MountedItemStorageType.REGISTRY bindings
        assertSame(AllCreateCompat.BACKPACK_STORAGE.get(),
                MountedItemStorageType.REGISTRY.get(AllBlocks.MECHANICAL_BACKPACK.get()));
        assertSame(AllCreateCompat.BACKPACK_STORAGE.get(),
                MountedItemStorageType.REGISTRY.get(AllBlocks.MAGNETIC_BACKPACK.get()));

        // 1. Mechanical (Netherite) Backpack full assembly -> codec -> mutate -> disassembly round-trip
        BlockState netheriteState = AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState()
                .setValue(MechanicalBackpackBlock.NETHERITE, true)
                .setValue(MechanicalBackpackBlock.FACING, Direction.EAST);
        ItemStack placedMech = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        placedMech.set(DataComponents.CUSTOM_NAME, Component.literal("Train Cargo"));
        placedMech.set(DataComponents.DYED_COLOR, new DyedItemColor(0x2288CC, true));
        placedMech.set(ExtraDataComponents.SORT_MODE, SortMode.COUNT);
        placedMech.set(ExtraDataComponents.TOOLBOX_RESTOCK, false);

        MechanicalBackpackBlockEntity mechBe = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        mechBe.loadFromStack(placedMech);
        mechBe.getInventory().setStackInSlot(0, new ItemStack(Items.COPPER_INGOT, 40));
        mechBe.getToolboxInventory().insertIntoCompartment(0, new ItemStack(Items.TORCH, 32), false);
        // Reserve compartment 1 as an empty ghost filter
        mechBe.getToolboxInventory().insertIntoCompartment(1, new ItemStack(Items.RAIL, 1), false);
        mechBe.getToolboxInventory().extractFromCompartment(1, 1, false);

        CompoundTag savedMechNbt = mechBe.saveWithFullMetadata(registries);
        BackpackMountedStorage mountedMech = AllCreateCompat.BACKPACK_STORAGE.get()
                .mount(null, netheriteState, BlockPos.ZERO, mechBe);
        assertNotNull(mountedMech);
        assertEquals(BackpackInventory.SLOTS, mountedMech.getSlots());
        assertEquals(40, mountedMech.getStackInSlot(0).getCount());

        // Anti-nesting on mounted storage (Harvesters / Deployers / Portable Storage Interfaces)
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        assertFalse(mountedMech.isItemValid(1, shulker));
        assertEquals(1, mountedMech.insertItem(1, shulker, false).getCount());
        assertTrue(mountedMech.getStackInSlot(1).isEmpty());

        // Simulate Contraption harvesting items into mounted 27-slot storage AND live GUI edits to the 8-slot toolbox
        assertTrue(mountedMech.insertItem(1, new ItemStack(Items.WHEAT, 64), false).isEmpty());
        // Insert 200 items (> 64 multi-stack) into toolbox compartment 3 and a ghost filter into compartment 4
        assertTrue(mountedMech.getToolboxInventory().insertIntoCompartment(3, new ItemStack(Items.OAK_PLANKS, 64), false).isEmpty());
        assertTrue(mountedMech.getToolboxInventory().insertIntoCompartment(3, new ItemStack(Items.OAK_PLANKS, 64), false).isEmpty());
        assertTrue(mountedMech.getToolboxInventory().insertIntoCompartment(3, new ItemStack(Items.OAK_PLANKS, 64), false).isEmpty());
        assertTrue(mountedMech.getToolboxInventory().insertIntoCompartment(3, new ItemStack(Items.OAK_PLANKS, 8), false).isEmpty());
        assertEquals(200, mountedMech.getToolboxInventory().getTotalCount(3));
        assertTrue(mountedMech.getToolboxInventory().insertIntoCompartment(4, new ItemStack(Items.REDSTONE_TORCH, 1), false).isEmpty());
        assertEquals(1, mountedMech.getToolboxInventory().extractFromCompartment(4, 1, false).getCount());
        assertTrue(mountedMech.getToolboxInventory().isReservedEmpty(4));
        // Mounted toolbox must also reject nested containers
        assertFalse(mountedMech.getToolboxInventory().canAccept(5, new ItemStack(AllItems.MECHANICAL_BACKPACK.get())));
        assertFalse(mountedMech.getToolboxInventory().canAccept(5, new ItemStack(Items.SHULKER_BOX)));
        mountedMech.setSortMode(SortMode.MOD);
        mountedMech.setRestockEnabled(true);

        // Codec encode -> decode round-trip via MountedItemStorage.CODEC (persists 27 slots + 8 toolbox compartments + ghost filters)
        DataResult<Tag> encoded = MountedItemStorage.CODEC.encodeStart(nbtOps, mountedMech);
        assertTrue(encoded.result().isPresent(), "Mounted storage must encode cleanly: " + encoded.error());
        DataResult<MountedItemStorage> decoded = MountedItemStorage.CODEC.parse(nbtOps, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "Mounted storage must decode cleanly: " + decoded.error());
        BackpackMountedStorage decodedMech = assertInstanceOf(BackpackMountedStorage.class, decoded.result().get());
        assertEquals(200, decodedMech.getToolboxInventory().getTotalCount(3), "Codec must preserve multi-stack (>64) toolbox compartments");
        assertTrue(decodedMech.getToolboxInventory().isReservedEmpty(1), "Codec must preserve pre-existing ghost filter");
        assertTrue(decodedMech.getToolboxInventory().isReservedEmpty(4), "Codec must preserve newly reserved ghost filter");

        // Simulate Contraption disassembly: restore BE from StructureBlockInfo NBT, then unmount storage
        MechanicalBackpackBlockEntity restoredMech = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        restoredMech.loadWithComponents(savedMechNbt, registries);
        decodedMech.unmount(null, netheriteState, BlockPos.ZERO, restoredMech);

        assertEquals(40, restoredMech.getInventory().getStackInSlot(0).getCount());
        assertEquals(64, restoredMech.getInventory().getStackInSlot(1).getCount());
        assertTrue(restoredMech.isNetherite());
        assertEquals("Train Cargo", restoredMech.getDisplayName().getString());
        assertEquals(SortMode.MOD, restoredMech.getSortMode());
        assertTrue(restoredMech.isRestockEnabled());
        assertEquals(32, restoredMech.getToolboxInventory().getTotalCount(0));
        assertTrue(restoredMech.getToolboxInventory().isReservedEmpty(1), "Toolbox ghost filter must survive contraption");
        assertEquals(200, restoredMech.getToolboxInventory().getTotalCount(3), "Unmounted toolbox items must persist with zero loss");
        assertTrue(restoredMech.getToolboxInventory().isReservedEmpty(4), "Mounted-created ghost filter must survive unmount");

        ItemStack droppedMech = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        restoredMech.saveToStack(droppedMech);
        assertEquals(new DyedItemColor(0x2288CC, true), droppedMech.get(DataComponents.DYED_COLOR));

        // 2. Magnetic Backpack preserves charge, filters, enabled/blacklist, updated 8-slot toolbox, and updated storage
        BlockState magState = AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState();
        ItemStack placedMag = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        placedMag.set(AllDataComponents.CHARGE, 4321);
        placedMag.set(AllDataComponents.ENABLED, false);
        placedMag.set(ExtraDataComponents.BLACKLIST, true);
        placedMag.set(DataComponents.CUSTOM_NAME, Component.literal("Drill Vacuum"));

        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(BlockPos.ZERO, magState);
        magBe.loadFromStack(placedMag);
        magBe.setFilters(List.of(new ItemStack(Items.COBBLESTONE), ItemStack.EMPTY, ItemStack.EMPTY));
        magBe.getToolboxInventory().insertIntoCompartment(2, new ItemStack(Items.SCAFFOLDING, 16), false);

        CompoundTag savedMagNbt = magBe.saveWithFullMetadata(registries);
        BackpackMountedStorage mountedMag = AllCreateCompat.BACKPACK_STORAGE.get()
                .mount(null, magState, BlockPos.ZERO, magBe);
        assertNotNull(mountedMag);
        assertTrue(mountedMag.insertItem(0, new ItemStack(Items.RAW_IRON, 19), false).isEmpty());
        assertTrue(mountedMag.getToolboxInventory().insertIntoCompartment(2, new ItemStack(Items.SCAFFOLDING, 48), false).isEmpty());

        MagneticBackpackBlockEntity restoredMag = new MagneticBackpackBlockEntity(BlockPos.ZERO, magState);
        restoredMag.loadWithComponents(savedMagNbt, registries);
        mountedMag.unmount(null, magState, BlockPos.ZERO, restoredMag);

        assertEquals(19, restoredMag.getInventory().getStackInSlot(0).getCount());
        assertEquals(4321, restoredMag.getCharge());
        assertFalse(restoredMag.isEnabled());
        assertTrue(restoredMag.isBlacklist());
        assertEquals("Drill Vacuum", restoredMag.getDisplayName().getString());
        assertEquals(Items.COBBLESTONE, restoredMag.getFilterInventory().getStackInSlot(0).getItem());
        assertEquals(64, restoredMag.getToolboxInventory().getTotalCount(2));
    }

    @Test
    void backpackBlocksMatchCreateToolboxSoundTypeAndTitleTruncationPreventsOverflow() {
        // Exact Create Toolbox SoundType (SoundType.WOOD) on both Mechanical/Netherite and Magnetic Backpack blocks
        assertEquals(net.minecraft.world.level.block.SoundType.WOOD,
                AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState().getSoundType(null, BlockPos.ZERO, null));
        assertEquals(net.minecraft.world.level.block.SoundType.WOOD,
                AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState().getSoundType(null, BlockPos.ZERO, null));

        // Monospace 6px/char font simulation matching standard Minecraft glyph widths (maxTitleWidth = 106px)
        java.util.function.ToIntFunction<String> widthFn = s -> s.length() * 6;
        java.util.function.BiFunction<String, Integer, String> substrFn =
                (s, maxPx) -> s.substring(0, Math.min(s.length(), Math.max(0, maxPx / 6)));

        String longTitle = "Moving Netherite Mechanical Backpack";
        String truncated = com.stackultra.essentials.content.backpack.BackpackHelper
                .truncateTitle(longTitle, 106, widthFn, substrFn);
        assertTrue(widthFn.applyAsInt(truncated) <= 106, "Truncated title must never exceed maxWidth");
        assertTrue(truncated.endsWith("..."), "Overflowing title must end with ellipsis");
        assertFalse(truncated.endsWith(" ..."), "Truncated title must trim trailing space before ellipsis");

        String shortTitle = "Backpack";
        assertEquals(shortTitle, com.stackultra.essentials.content.backpack.BackpackHelper
                .truncateTitle(shortTitle, 106, widthFn, substrFn));
    }

    @Test
    void wrenchRotationCyclesHorizontallyAndPreservesPropertiesWithoutTiltingMagneticBackpack() {
        MechanicalBackpackBlock mechBlock = AllBlocks.MECHANICAL_BACKPACK.get();
        MagneticBackpackBlock magBlock = AllBlocks.MAGNETIC_BACKPACK.get();

        assertInstanceOf(IWrenchable.class, mechBlock);
        assertInstanceOf(IWrenchable.class, magBlock);

        BlockState mech = mechBlock.defaultBlockState()
                .setValue(MechanicalBackpackBlock.NETHERITE, true)
                .setValue(MechanicalBackpackBlock.FACING, Direction.NORTH);
        mech = mechBlock.getRotatedBlockState(mech, Direction.UP);
        assertEquals(Direction.EAST, mech.getValue(MechanicalBackpackBlock.FACING));
        assertTrue(mech.getValue(MechanicalBackpackBlock.NETHERITE), "Wrench rotation must keep NETHERITE=true");
        mech = mechBlock.getRotatedBlockState(mech, Direction.DOWN);
        assertEquals(Direction.NORTH, mech.getValue(MechanicalBackpackBlock.FACING));

        // Magnetic Backpack must rotate strictly horizontally even when clicking side faces, preserving +Y shaft
        BlockState mag = magBlock.defaultBlockState().setValue(MagneticBackpackBlock.FACING, Direction.NORTH);
        for (Direction face : Direction.values()) {
            mag = magBlock.getRotatedBlockState(mag, face);
            assertTrue(mag.getValue(MagneticBackpackBlock.FACING).getAxis().isHorizontal(),
                    "Magnetic Backpack must never tilt off horizontal facing on wrench click: " + face);
            assertTrue(magBlock.hasShaftTowards(null, BlockPos.ZERO, mag, Direction.UP));
            assertEquals(Direction.Axis.Y, magBlock.getRotationAxis(mag));
        }
    }

    @Test
    void curiosSoftCompatSafelyFallsBackWhenCuriosIsAbsent() {
        // Curios is compileOnly and absent from the test runtime classpath
        assertFalse(CuriosCompat.isLoaded(), "Curios must not be required at runtime");

        assertTrue(CuriosCompat.getBackBackpack(null).isEmpty());
        assertTrue(CuriosCompat.getBackMagneticBackpack(null).isEmpty());
        assertTrue(CuriosCompat.getCuriosMagnets(null).isEmpty());
        assertTrue(CuriosCompat.getCuriosAirSources(null).isEmpty());
        assertFalse(CuriosCompat.isStackInCurios(null, new ItemStack(AllItems.MECHANICAL_BACKPACK.get())));

        List<ItemStack> candidates = new ArrayList<>();
        assertDoesNotThrow(() -> CuriosCompat.collectBackSlots(null, candidates, new ArrayList<>()));
        assertDoesNotThrow(() -> CuriosCompat.collectCuriosMagnetSlots(null, candidates, new ArrayList<>()));

        // Verify all common/client subscriber classes load cleanly with zero NoClassDefFoundError when Curios is absent
        assertDoesNotThrow(() -> {
            Class.forName(CuriosCompat.class.getName());
            Class.forName(CuriosClientSetup.class.getName());
            Class.forName(BackpackEquipment.class.getName());
            Class.forName(MagneticBackpackEquipment.class.getName());
            Class.forName(MagnetEquipment.class.getName());
            Class.forName(BacktankSupport.class.getName());
            Class.forName(MenuValidationHelper.class.getName());
            Class.forName(PortableToolboxHandler.class.getName());
        });
    }
}
