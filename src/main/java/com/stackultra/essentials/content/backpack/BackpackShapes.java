package com.stackultra.essentials.content.backpack;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tight compound {@link VoxelShape} hitboxes derived directly from the integer-only
 * backpack block geometry for all four horizontal orientations.
 */
public final class BackpackShapes {

    private static final VoxelShape MECHANICAL_NORTH = buildMechanical(Direction.NORTH);
    private static final VoxelShape MECHANICAL_SOUTH = buildMechanical(Direction.SOUTH);
    private static final VoxelShape MECHANICAL_EAST = buildMechanical(Direction.EAST);
    private static final VoxelShape MECHANICAL_WEST = buildMechanical(Direction.WEST);

    private static final VoxelShape MAGNETIC_NORTH = buildMagnetic(Direction.NORTH);
    private static final VoxelShape MAGNETIC_SOUTH = buildMagnetic(Direction.SOUTH);
    private static final VoxelShape MAGNETIC_EAST = buildMagnetic(Direction.EAST);
    private static final VoxelShape MAGNETIC_WEST = buildMagnetic(Direction.WEST);

    private BackpackShapes() {
    }

    public static VoxelShape mechanical(Direction facing) {
        return switch (facing) {
            case SOUTH -> MECHANICAL_SOUTH;
            case EAST -> MECHANICAL_EAST;
            case WEST -> MECHANICAL_WEST;
            default -> MECHANICAL_NORTH;
        };
    }

    public static VoxelShape magnetic(Direction facing) {
        return switch (facing) {
            case SOUTH -> MAGNETIC_SOUTH;
            case EAST -> MAGNETIC_EAST;
            case WEST -> MAGNETIC_WEST;
            default -> MAGNETIC_NORTH;
        };
    }

    private static VoxelShape buildMechanical(Direction facing) {
        // Top carry handle: [6, 14, 8] -> [10, 16, 9]
        return buildBase(facing, rotatedBox(facing, 6, 14, 8, 10, 16, 9));
    }

    private static VoxelShape buildMagnetic(Direction facing) {
        // Top 4x2x4 Create kinetic shaft: [6, 14, 6] -> [10, 16, 10]
        return buildBase(facing, rotatedBox(facing, 6, 14, 6, 10, 16, 10));
    }

    private static VoxelShape buildBase(Direction facing, VoxelShape topElement) {
        return Shapes.or(
                rotatedBox(facing, 3, 0, 5, 13, 14, 11),   // main_body
                rotatedBox(facing, 3, 10, 4, 13, 14, 5),   // front_flap
                rotatedBox(facing, 2, 1, 6, 3, 10, 10),    // pocket_west
                rotatedBox(facing, 13, 1, 6, 14, 10, 10),  // pocket_east
                rotatedBox(facing, 4, 1, 3, 12, 6, 5),     // toolbox_body
                rotatedBox(facing, 6, 6, 3, 10, 7, 4),     // toolbox_handle
                rotatedBox(facing, 3, 1, 11, 13, 13, 12),  // back straps + rear gear layer
                topElement
        );
    }

    /**
     * Rotates a NORTH-facing model-space box ([0..16] pixel units) clockwise around the
     * block center (8, 8) to match blockstate y-rotations: NORTH=0, EAST=90, SOUTH=180, WEST=270.
     */
    private static VoxelShape rotatedBox(Direction facing,
                                         double x1, double y1, double z1,
                                         double x2, double y2, double z2) {
        return switch (facing) {
            case EAST -> Block.box(16.0 - z2, y1, x1, 16.0 - z1, y2, x2);
            case SOUTH -> Block.box(16.0 - x2, y1, 16.0 - z2, 16.0 - x1, y2, 16.0 - z1);
            case WEST -> Block.box(z1, y1, 16.0 - x2, z2, y2, 16.0 - x1);
            default -> Block.box(x1, y1, z1, x2, y2, z2);
        };
    }
}
