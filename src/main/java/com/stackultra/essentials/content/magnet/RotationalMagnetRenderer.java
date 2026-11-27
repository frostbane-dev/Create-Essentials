package com.stackultra.essentials.content.magnet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.stackultra.essentials.client.AllPartialModels;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

public class RotationalMagnetRenderer extends KineticBlockEntityRenderer<RotationalMagnetBlockEntity> {

    public RotationalMagnetRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            RotationalMagnetBlockEntity be,
            float partialTicks,
            PoseStack ms,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        BlockState state = be.getBlockState();
        SuperByteBuffer rotatingAssembly = CachedBuffers.partialFacing(
                AllPartialModels.forMagnet(state), state, Direction.UP);

        int packedLight = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : light;

        standardKineticRotationTransform(rotatingAssembly, be, packedLight)
                .renderInto(ms, bufferSource.getBuffer(RenderType.cutoutMipped()));
    }
}
