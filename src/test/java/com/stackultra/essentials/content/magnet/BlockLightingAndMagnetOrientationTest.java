package com.stackultra.essentials.content.magnet;

import com.stackultra.essentials.registry.AllBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies against the real registered mod blocks:
 * 1. Magnets always orient their kinetic/charging shaft input upward (+Y) regardless of FACING state.
 * 2. Magnets and backpacks expose non-full VoxelShapes and non-occluding light properties so they
 *    never cast dark shadows when placed adjacent to neighboring blocks.
 */
class BlockLightingAndMagnetOrientationTest {

    @Test
    void magnetsAlwaysExposeUpwardKineticInputAxisAndShaft() {
        RotationalMagnetBlock[] magnets = new RotationalMagnetBlock[]{
                AllBlocks.ROTATIONAL_MAGNET.get(),
                AllBlocks.PRECISION_MAGNET.get()
        };

        for (RotationalMagnetBlock block : magnets) {
            assertEquals(Direction.DOWN, block.defaultBlockState().getValue(RotationalMagnetBlock.FACING),
                    "Default FACING should be DOWN so the rear charging shaft points UP (+Y)");

            for (Direction stateFacing : Direction.values()) {
                BlockState state = block.defaultBlockState().setValue(RotationalMagnetBlock.FACING, stateFacing);
                assertEquals(Direction.Axis.Y, block.getRotationAxis(state),
                        "Magnet kinetic axis must stay Y regardless of FACING=" + stateFacing);
                for (Direction face : Direction.values()) {
                    assertEquals(face == Direction.UP, block.hasShaftTowards(null, BlockPos.ZERO, state, face),
                            "Magnet shaft must only connect on Direction.UP (checked face=" + face + ")");
                }
            }
        }
    }

    @Test
    void magnetsAndBackpacksAreNonFullAndDoNotCastOcclusionShadows() {
        Block[] blocks = new Block[]{
                AllBlocks.ROTATIONAL_MAGNET.get(),
                AllBlocks.PRECISION_MAGNET.get(),
                AllBlocks.MECHANICAL_BACKPACK.get(),
                AllBlocks.MAGNETIC_BACKPACK.get()
        };

        for (Block block : blocks) {
            BlockState state = block.defaultBlockState();
            VoxelShape shape = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
            assertNotEquals(Shapes.block(), shape, block.getClass().getSimpleName() + " must use a non-full VoxelShape");
            assertFalse(state.canOcclude(), block.getClass().getSimpleName() + " must not occlude neighbor faces");
            assertFalse(state.isViewBlocking(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    block.getClass().getSimpleName() + " must not be view-blocking");
            assertFalse(state.isSuffocating(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    block.getClass().getSimpleName() + " must not be suffocating");
            assertEquals(1.0F, state.getShadeBrightness(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), 1e-5F,
                    block.getClass().getSimpleName() + " shade brightness must be 1.0F");
            assertTrue(state.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    block.getClass().getSimpleName() + " must propagate skylight down");
            assertEquals(0, state.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO),
                    block.getClass().getSimpleName() + " must not attenuate block light");
        }
    }
}
