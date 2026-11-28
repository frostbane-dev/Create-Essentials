package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllBlocks;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllItems;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackpackColorAndHitboxTest {

    private static boolean containsPixel(VoxelShape shape, double px, double py, double pz) {
        double x = px / 16.0;
        double y = py / 16.0;
        double z = pz / 16.0;
        for (AABB box : shape.toAabbs()) {
            if (x >= box.minX && x <= box.maxX
                    && y >= box.minY && y <= box.maxY
                    && z >= box.minZ && z <= box.maxZ) {
                return true;
            }
        }
        return false;
    }

    @Test
    void mechanicalAndMagneticBackpacksUseTightCompoundVoxelShapes() {
        MechanicalBackpackBlock mech = AllBlocks.MECHANICAL_BACKPACK.get();
        MagneticBackpackBlock mag = AllBlocks.MAGNETIC_BACKPACK.get();

        for (Direction facing : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            VoxelShape mechShape = mech.defaultBlockState()
                    .setValue(MechanicalBackpackBlock.FACING, facing)
                    .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            VoxelShape magShape = mag.defaultBlockState()
                    .setValue(MagneticBackpackBlock.FACING, facing)
                    .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());

            assertTrue(mechShape.toAabbs().size() > 1,
                    "MechanicalBackpackBlock must use a compound VoxelShape for facing=" + facing);
            assertTrue(magShape.toAabbs().size() > 1,
                    "MagneticBackpackBlock must use a compound VoxelShape for facing=" + facing);

            // Outer empty air corners above the sides must NOT be inside the hitbox
            assertFalse(containsPixel(mechShape, 1.0, 15.0, 1.0));
            assertFalse(containsPixel(magShape, 1.0, 15.0, 1.0));
            assertFalse(containsPixel(mechShape, 15.0, 15.0, 15.0));
            assertFalse(containsPixel(magShape, 15.0, 15.0, 15.0));
        }

        // Detailed NORTH-facing silhouette checks
        VoxelShape mechNorth = mech.defaultBlockState()
                .setValue(MechanicalBackpackBlock.FACING, Direction.NORTH)
                .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        VoxelShape magNorth = mag.defaultBlockState()
                .setValue(MagneticBackpackBlock.FACING, Direction.NORTH)
                .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());

        // Core body, top flap overhang, side pockets, front toolbox, rear gear layer included
        assertTrue(containsPixel(mechNorth, 8.0, 7.0, 8.0), "Main body center");
        assertTrue(containsPixel(mechNorth, 8.0, 12.0, 4.5), "Top flap overhang");
        assertTrue(containsPixel(mechNorth, 2.5, 5.0, 8.0), "West side pocket");
        assertTrue(containsPixel(mechNorth, 13.5, 5.0, 8.0), "East side pocket");
        assertTrue(containsPixel(mechNorth, 8.0, 3.0, 3.5), "Front toolbox body");
        assertTrue(containsPixel(mechNorth, 8.0, 6.5, 3.5), "Front toolbox handle");
        assertTrue(containsPixel(mechNorth, 8.0, 7.0, 11.5), "Rear gear/strap layer");

        // Empty upper corners & lower-front flanks must be excluded
        assertFalse(containsPixel(mechNorth, 2.5, 12.0, 8.0), "Above west pocket should be empty air");
        assertFalse(containsPixel(mechNorth, 13.5, 12.0, 8.0), "Above east pocket should be empty air");
        assertFalse(containsPixel(mechNorth, 2.5, 3.0, 3.5), "Front-west flank next to toolbox should be empty air");
        assertFalse(containsPixel(mechNorth, 8.0, 8.5, 3.5), "Gap between toolbox and top flap should be empty air");

        // Top element differentiation: narrow carry handle [6..10,14..16,8..9] vs 4x2x4 shaft [6..10,14..16,6..10]
        assertTrue(containsPixel(mechNorth, 8.0, 15.0, 8.5), "Mechanical handle top");
        assertFalse(containsPixel(mechNorth, 8.0, 15.0, 6.5), "In front of mechanical handle should be empty air");
        assertTrue(containsPixel(magNorth, 8.0, 15.0, 6.5), "Magnetic 4x2x4 shaft front half");
        assertTrue(containsPixel(magNorth, 8.0, 15.0, 9.5), "Magnetic 4x2x4 shaft rear half");
        assertFalse(containsPixel(magNorth, 4.5, 15.0, 8.0), "Beside magnetic shaft should be empty air");

        // Rotation check: EAST facing moves front toolbox from -Z (Z=3..5) to +X (X=11..13)
        VoxelShape mechEast = mech.defaultBlockState()
                .setValue(MechanicalBackpackBlock.FACING, Direction.EAST)
                .getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        assertTrue(containsPixel(mechEast, 12.5, 3.0, 8.0), "EAST-facing front toolbox must sit at +X");
        assertFalse(containsPixel(mechEast, 3.5, 3.0, 8.0), "EAST-facing back must not have front toolbox at -X");
    }

    @Test
    void allSixteenDyeColorsResolveCleanModelPredicatesAndDyeRecipePreservesAllData() {
        BackpackDyeRecipe recipe = new BackpackDyeRecipe(CraftingBookCategory.EQUIPMENT);

        Item[] backpackItems = new Item[]{
                AllItems.MECHANICAL_BACKPACK.get(),
                AllItems.NETHERITE_BACKPACK.get(),
                AllItems.MAGNETIC_BACKPACK.get()
        };

        for (Item item : backpackItems) {
            ItemStack unpainted = new ItemStack(item);
            assertNull(BackpackColorHelper.resolveDyeColor(unpainted));
            assertEquals(0.0F, BackpackColorHelper.getModelPropertyValue(unpainted), 1e-6F);

            // Populate storage, toolbox, ghost filter, sort mode, restock, custom name, and magnetic state
            BackpackInventory inv = BackpackInventory.forStack(unpainted);
            inv.setStackInSlot(0, new ItemStack(Items.DIAMOND, 12));
            inv.save(unpainted);

            PortableToolboxInventory tb = PortableToolboxInventory.forStack(unpainted);
            tb.insertIntoCompartment(2, new ItemStack(Items.COBBLESTONE, 64), false);
            tb.save(unpainted);

            unpainted.set(ExtraDataComponents.SORT_MODE, SortMode.COUNT);
            unpainted.set(ExtraDataComponents.TOOLBOX_RESTOCK, false);
            unpainted.set(DataComponents.CUSTOM_NAME, Component.literal("Expedition Pack"));
            if (item instanceof MagneticBackpackItem) {
                unpainted.set(AllDataComponents.CHARGE, 4200);
                unpainted.set(ExtraDataComponents.BLACKLIST, true);
            }

            for (DyeColor color : DyeColor.values()) {
                ItemStack dye = new ItemStack(DyeItem.byColor(color));
                CraftingInput input = CraftingInput.of(2, 1, List.of(unpainted.copy(), dye));
                assertTrue(recipe.matches(input, null), "Recipe must match " + item + " + " + color);

                ItemStack dyed = recipe.assemble(input, RegistryAccess.EMPTY);
                assertFalse(dyed.isEmpty());
                assertEquals(color, BackpackColorHelper.resolveDyeColor(dyed));
                assertEquals((float) (color.getId() + 1), BackpackColorHelper.getModelPropertyValue(dyed), 1e-6F);

                // Verify 100% component preservation
                assertEquals(12, BackpackInventory.forStack(dyed).getStackInSlot(0).getCount());
                assertEquals(64, PortableToolboxInventory.forStack(dyed).getTotalCount(2));
                assertEquals(SortMode.COUNT, dyed.get(ExtraDataComponents.SORT_MODE));
                assertEquals(Boolean.FALSE, dyed.get(ExtraDataComponents.TOOLBOX_RESTOCK));
                assertEquals(Component.literal("Expedition Pack"), dyed.get(DataComponents.CUSTOM_NAME));
                if (item instanceof MagneticBackpackItem) {
                    assertEquals(4200, dyed.getOrDefault(AllDataComponents.CHARGE, 0));
                    assertEquals(Boolean.TRUE, dyed.get(ExtraDataComponents.BLACKLIST));
                }

                // Redundant re-dye to the exact same color is blocked
                CraftingInput sameColor = CraftingInput.of(2, 1, List.of(dyed, dye));
                assertFalse(recipe.matches(sameColor, null), "Re-dyeing to the exact same color must be blocked");
            }
        }
    }

    @Test
    void blockStateColorAndBlockEntityNbtRoundTripAndWrenchPreservation() {
        MechanicalBackpackBlock mechBlock = AllBlocks.MECHANICAL_BACKPACK.get();
        MagneticBackpackBlock magBlock = AllBlocks.MAGNETIC_BACKPACK.get();

        // Mechanical / Netherite block entity round-trip + wrench preservation
        BlockState cyanNetherite = mechBlock.defaultBlockState()
                .setValue(MechanicalBackpackBlock.NETHERITE, true)
                .setValue(MechanicalBackpackBlock.FACING, Direction.NORTH)
                .setValue(MechanicalBackpackBlock.COLOR, DyeColor.CYAN);
        BlockState rotatedMech = mechBlock.getRotatedBlockState(cyanNetherite, Direction.UP);
        assertEquals(DyeColor.CYAN, rotatedMech.getValue(MechanicalBackpackBlock.COLOR));
        assertTrue(rotatedMech.getValue(MechanicalBackpackBlock.NETHERITE));

        ItemStack stack = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        BackpackColorHelper.setDyeColor(stack, DyeColor.CYAN);
        MechanicalBackpackBlockEntity be = new MechanicalBackpackBlockEntity(BlockPos.ZERO, cyanNetherite);
        be.loadFromStack(stack);
        CompoundTag saved = be.saveWithoutMetadata(RegistryAccess.EMPTY);

        MechanicalBackpackBlockEntity reloaded = new MechanicalBackpackBlockEntity(BlockPos.ZERO, cyanNetherite);
        reloaded.loadCustomOnly(saved, RegistryAccess.EMPTY);
        ItemStack dropped = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        reloaded.saveToStack(dropped);
        assertEquals(DyeColor.CYAN, BackpackColorHelper.resolveDyeColor(dropped));

        // Schematic / /setblock state-only color seeding without loadFromStack
        BlockState greenMag = magBlock.defaultBlockState()
                .setValue(MagneticBackpackBlock.FACING, Direction.EAST)
                .setValue(MagneticBackpackBlock.COLOR, DyeColor.GREEN);
        BlockState rotatedMag = magBlock.getRotatedBlockState(greenMag, Direction.UP);
        assertEquals(DyeColor.GREEN, rotatedMag.getValue(MagneticBackpackBlock.COLOR));

        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(BlockPos.ZERO, greenMag);
        ItemStack magDropped = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        magBe.saveToStack(magDropped);
        assertEquals(DyeColor.GREEN, BackpackColorHelper.resolveDyeColor(magDropped));
    }
}
