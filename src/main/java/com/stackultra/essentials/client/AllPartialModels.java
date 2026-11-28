package com.stackultra.essentials.client;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlock;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public final class AllPartialModels {

    public static final PartialModel ROTATIONAL_MAGNET_SHAFT = block("rotational_magnet_shaft");
    public static final PartialModel PRECISION_MAGNET_SHAFT = block("precision_magnet_shaft");
    public static final PartialModel MAGNETIC_BACKPACK_SHAFT = block("magnetic_backpack_shaft");
    public static final PartialModel BACKPACK_REAR_GEAR_UPPER = block("backpack_rear_gear_upper");
    public static final PartialModel BACKPACK_REAR_GEAR_LOWER = block("backpack_rear_gear_lower");

    private AllPartialModels() {
    }

    private static PartialModel block(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "block/" + path));
    }

    public static PartialModel forMagnet(BlockState state) {
        if (state.getBlock() instanceof RotationalMagnetBlock block && block.getTier() == MagnetTier.TIER_2)
            return PRECISION_MAGNET_SHAFT;
        return ROTATIONAL_MAGNET_SHAFT;
    }

    public static void init() {
        // Ensures PartialModel.of(...) executes before Flywheel's ModelEvent.RegisterAdditional fires.
    }
}
