package com.stackultra.essentials.content.backpack;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.stackultra.essentials.client.AllPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visual.DynamicVisual;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackVisual extends KineticBlockEntityVisual<MagneticBackpackBlockEntity>
        implements SimpleDynamicVisual {

    protected final RotatingInstance shaft;
    protected final TransformedInstance upperGear;
    protected final TransformedInstance lowerGear;

    public MagneticBackpackVisual(VisualizationContext context, MagneticBackpackBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        this.shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.MAGNETIC_BACKPACK_SHAFT))
                .createInstance();

        this.upperGear = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.BACKPACK_REAR_GEAR_UPPER))
                .createInstance();

        this.lowerGear = instancerProvider()
                .instancer(InstanceTypes.TRANSFORMED, Models.partial(AllPartialModels.BACKPACK_REAR_GEAR_LOWER))
                .createInstance();

        this.shaft.setup(blockEntity)
                .setPosition(getVisualPosition())
                .setChanged();

        animateRearGears();
    }

    @Override
    public void beginFrame(DynamicVisual.Context ctx) {
        animateRearGears();
    }

    private void animateRearGears() {
        net.minecraft.world.level.block.state.BlockState liveState = blockEntity.getBlockState();
        Direction raw = liveState.hasProperty(MagneticBackpackBlock.FACING)
                ? liveState.getValue(MagneticBackpackBlock.FACING)
                : Direction.NORTH;
        Direction facing = raw.getAxis().isHorizontal() ? raw : Direction.NORTH;
        float yRot = BackpackGearRenderer.facingYDegrees(facing);
        float angle = BackpackGearRenderer.computeGearAngleDegrees(blockEntity.getLevel());

        upperGear.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(yRot)
                .uncenter()
                .translate(BackpackGearRenderer.UPPER_PIVOT_X, BackpackGearRenderer.UPPER_PIVOT_Y, BackpackGearRenderer.GEAR_PIVOT_Z)
                .rotateZDegrees(-angle)
                .translate(-BackpackGearRenderer.UPPER_PIVOT_X, -BackpackGearRenderer.UPPER_PIVOT_Y, -BackpackGearRenderer.GEAR_PIVOT_Z)
                .setChanged();

        lowerGear.setIdentityTransform()
                .translate(getVisualPosition())
                .center()
                .rotateYDegrees(yRot)
                .uncenter()
                .translate(BackpackGearRenderer.LOWER_PIVOT_X, BackpackGearRenderer.LOWER_PIVOT_Y, BackpackGearRenderer.GEAR_PIVOT_Z)
                .rotateZDegrees(angle + BackpackGearRenderer.MESH_PHASE_OFFSET_DEGREES)
                .translate(-BackpackGearRenderer.LOWER_PIVOT_X, -BackpackGearRenderer.LOWER_PIVOT_Y, -BackpackGearRenderer.GEAR_PIVOT_Z)
                .setChanged();
    }

    @Override
    public void update(float pt) {
        shaft.setup(blockEntity).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(shaft, upperGear, lowerGear);
    }

    @Override
    protected void _delete() {
        shaft.delete();
        upperGear.delete();
        lowerGear.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
        consumer.accept(upperGear);
        consumer.accept(lowerGear);
    }
}
