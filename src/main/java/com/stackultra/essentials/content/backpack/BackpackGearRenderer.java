package com.stackultra.essentials.content.backpack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stackultra.essentials.client.AllPartialModels;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * Shared continuous meshing rear-cogwheel renderer and pivot math for all 3 placed backpacks
 * (Mechanical, Netherite Mechanical, and Magnetic).
 */
@OnlyIn(Dist.CLIENT)
public final class BackpackGearRenderer {

    // Exact geometric axle centers (in block units) for the two 3x3+4-tooth brass rear gears
    public static final float UPPER_PIVOT_X = 7.5F / 16.0F;
    public static final float UPPER_PIVOT_Y = 9.5F / 16.0F;
    public static final float LOWER_PIVOT_X = 8.5F / 16.0F;
    public static final float LOWER_PIVOT_Y = 4.5F / 16.0F;
    public static final float GEAR_PIVOT_Z = 11.5F / 16.0F;

    // Smooth steady Create clockwork cadence (~50 deg/sec) with 45 deg tooth-meshing phase offset
    public static final float DEGREES_PER_TICK = 2.5F;
    public static final float MESH_PHASE_OFFSET_DEGREES = 45.0F;

    private BackpackGearRenderer() {
    }

    /**
     * Converts horizontal block facing to right-handed Y rotation matching blockstate y-rotations
     * (NORTH=0, EAST=-90, SOUTH=180, WEST=90).
     */
    public static float facingYDegrees(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    public static float computeGearAngleDegrees(@Nullable Level level) {
        float time = level != null
                ? AnimationTickHolder.getRenderTime(level)
                : AnimationTickHolder.getRenderTime();
        return (time * DEGREES_PER_TICK) % 360.0F;
    }

    public static void renderRearGears(BlockState state,
                                       Direction facing,
                                       @Nullable Level level,
                                       int packedLight,
                                       PoseStack ms,
                                       MultiBufferSource bufferSource) {
        float yRot = facingYDegrees(facing);
        float angle = computeGearAngleDegrees(level);
        var consumer = bufferSource.getBuffer(RenderType.cutoutMipped());

        // Upper gear: clockwise when viewed from the back (+Z)
        CachedBuffers.partial(AllPartialModels.BACKPACK_REAR_GEAR_UPPER, state)
                .center()
                .rotateYDegrees(yRot)
                .uncenter()
                .translate(UPPER_PIVOT_X, UPPER_PIVOT_Y, GEAR_PIVOT_Z)
                .rotateZDegrees(-angle)
                .translate(-UPPER_PIVOT_X, -UPPER_PIVOT_Y, -GEAR_PIVOT_Z)
                .light(packedLight)
                .renderInto(ms, consumer);

        // Lower gear: counter-clockwise with 45 deg phase offset so teeth mesh cleanly
        CachedBuffers.partial(AllPartialModels.BACKPACK_REAR_GEAR_LOWER, state)
                .center()
                .rotateYDegrees(yRot)
                .uncenter()
                .translate(LOWER_PIVOT_X, LOWER_PIVOT_Y, GEAR_PIVOT_Z)
                .rotateZDegrees(angle + MESH_PHASE_OFFSET_DEGREES)
                .translate(-LOWER_PIVOT_X, -LOWER_PIVOT_Y, -GEAR_PIVOT_Z)
                .light(packedLight)
                .renderInto(ms, consumer);
    }
}
