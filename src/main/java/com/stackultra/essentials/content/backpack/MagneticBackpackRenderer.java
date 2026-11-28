package com.stackultra.essentials.content.backpack;

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
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackRenderer extends KineticBlockEntityRenderer<MagneticBackpackBlockEntity> {

    public MagneticBackpackRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(
            MagneticBackpackBlockEntity be,
            float partialTicks,
            PoseStack ms,
            MultiBufferSource bufferSource,
            int light,
            int overlay
    ) {
        if (VisualizationManager.supportsVisualization(be.getLevel()))
            return;

        BlockState state = be.getBlockState();
        SuperByteBuffer rotatingShaft = CachedBuffers.partial(
                AllPartialModels.MAGNETIC_BACKPACK_SHAFT, state);

        int packedLight = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : light;

        standardKineticRotationTransform(rotatingShaft, be, packedLight)
                .renderInto(ms, bufferSource.getBuffer(RenderType.cutoutMipped()));

        net.minecraft.core.Direction facing = state.hasProperty(MagneticBackpackBlock.FACING)
                && state.getValue(MagneticBackpackBlock.FACING).getAxis().isHorizontal()
                ? state.getValue(MagneticBackpackBlock.FACING)
                : net.minecraft.core.Direction.NORTH;
        BackpackGearRenderer.renderRearGears(state, facing, be.getLevel(), packedLight, ms, bufferSource);
    }
}
