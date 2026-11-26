package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreateEssentials.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RotationalMagnetBlockEntity>> ROTATIONAL_MAGNET =
            BLOCK_ENTITIES.register("rotational_magnet", () ->
                    BlockEntityType.Builder.of(RotationalMagnetBlockEntity::new,
                            AllBlocks.ROTATIONAL_MAGNET.get(),
                            AllBlocks.PRECISION_MAGNET.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalBackpackBlockEntity>> MECHANICAL_BACKPACK =
            BLOCK_ENTITIES.register("mechanical_backpack", () ->
                    BlockEntityType.Builder.of(MechanicalBackpackBlockEntity::new,
                            AllBlocks.MECHANICAL_BACKPACK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MagneticBackpackBlockEntity>> MAGNETIC_BACKPACK =
            BLOCK_ENTITIES.register("magnetic_backpack", () ->
                    BlockEntityType.Builder.of(MagneticBackpackBlockEntity::new,
                            AllBlocks.MAGNETIC_BACKPACK.get()).build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
