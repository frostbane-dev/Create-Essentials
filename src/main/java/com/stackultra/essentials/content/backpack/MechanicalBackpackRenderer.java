package com.stackultra.essentials.content.backpack;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders the continuously spinning meshing rear cogwheels on placed Mechanical
 * and Netherite Mechanical Backpacks.
 */
@OnlyIn(Dist.CLIENT)
public class MechanicalBackpackRenderer implements BlockEntityRenderer<MechanicalBackpackBlockEntity> {

    public MechanicalBackpackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MechanicalBackpackBlockEntity be,
                       float partialTick,
                       PoseStack ms,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       int packedOverlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(MechanicalBackpackBlock.FACING)
                ? state.getValue(MechanicalBackpackBlock.FACING)
                : Direction.NORTH;
        int light = be.getLevel() != null
                ? LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos())
                : packedLight;
        BackpackGearRenderer.renderRearGears(state, facing, be.getLevel(), light, ms, bufferSource);
    }
}
