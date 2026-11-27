package com.stackultra.essentials.content.magnet;

import com.simibubi.create.content.kinetics.base.KineticBlockEntityVisual;
import com.simibubi.create.content.kinetics.base.RotatingInstance;
import com.simibubi.create.foundation.render.AllInstanceTypes;
import com.stackultra.essentials.client.AllPartialModels;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;
import net.minecraft.core.Direction;

import java.util.function.Consumer;

public class RotationalMagnetVisual extends KineticBlockEntityVisual<RotationalMagnetBlockEntity> {

    protected final RotatingInstance shaft;
    private final Direction rear;

    public RotationalMagnetVisual(VisualizationContext context, RotationalMagnetBlockEntity blockEntity, float partialTick) {
        super(context, blockEntity, partialTick);

        this.rear = blockState.getValue(RotationalMagnetBlock.FACING).getOpposite();

        this.shaft = instancerProvider()
                .instancer(AllInstanceTypes.ROTATING, Models.partial(AllPartialModels.forMagnet(blockState)))
                .createInstance();

        this.shaft.setup(blockEntity)
                .setPosition(getVisualPosition())
                .rotateToFace(Direction.SOUTH, rear)
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
