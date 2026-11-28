package com.stackultra.essentials.content.backpack;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.stackultra.essentials.client.AllPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

import java.util.function.Consumer;

public class MagneticBackpackVisual extends KineticBlockEntityVisual<MagneticBackpackBlockEntity> {

    protected final RotatingInstance shaft;

    public MagneticBackpackVisual(VisualizationContext context, MagneticBackpackBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        this.shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.MAGNETIC_BACKPACK_SHAFT))
                .createInstance();

        this.shaft.setup(blockEntity)
                .setPosition(getVisualPosition())
                .setChanged();
    }

    @Override
    public void update(float pt) {
        shaft.setup(blockEntity).setChanged();
    }

    @Override
    public void updateLight(float partialTick) {
        relight(shaft);
    }

    @Override
    protected void _delete() {
        shaft.delete();
    }

    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
        consumer.accept(shaft);
    }
}
