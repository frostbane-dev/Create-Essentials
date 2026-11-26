package com.stackultra.essentials.registry;

import com.mojang.serialization.Codec;
import com.stackultra.essentials.CreateEssentials;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.UnaryOperator;

public class AllDataComponents {

    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateEssentials.MOD_ID);

    public static final DataComponentType<Integer> CHARGE = register("charge",
            builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DataComponentType<Boolean> ENABLED = register("enabled",
            builder -> builder.persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DataComponentType<Boolean> CHARGING = register("charging",
            builder -> builder.persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    public static final DataComponentType<ItemContainerContents> BACKPACK_FILTERS = register("backpack_filters",
            builder -> builder.persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    public static final DataComponentType<ItemContainerContents> MAGNET_FILTERS = register("magnet_filters",
            builder -> builder.persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    public static final DataComponentType<ItemContainerContents> BACKPACK_INVENTORY = register("backpack_inventory",
            builder -> builder.persistent(ItemContainerContents.CODEC)
                    .networkSynchronized(ItemContainerContents.STREAM_CODEC));

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        DataComponentType<T> type = builder.apply(DataComponentType.builder()).build();
        DATA_COMPONENTS.register(name, () -> type);
        return type;
    }

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register(bus);
    }
}
