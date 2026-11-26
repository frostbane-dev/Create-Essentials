package com.stackultra.essentials.registry;

import com.mojang.serialization.Codec;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.SortMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ExtraDataComponents {

    private static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, CreateEssentials.MOD_ID);

    public static final DataComponentType<Boolean> BLACKLIST = DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build();

    public static final DataComponentType<SortMode> SORT_MODE = DataComponentType.<SortMode>builder()
            .persistent(SortMode.CODEC)
            .networkSynchronized(SortMode.STREAM_CODEC)
            .build();

    public static final DataComponentType<ItemContainerContents> TOOLBOX_CONTENTS = DataComponentType.<ItemContainerContents>builder()
            .persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
            .build();

    public static final DataComponentType<ItemContainerContents> TOOLBOX_FILTERS = DataComponentType.<ItemContainerContents>builder()
            .persistent(ItemContainerContents.CODEC)
            .networkSynchronized(ItemContainerContents.STREAM_CODEC)
            .build();

    public static final DataComponentType<Boolean> TOOLBOX_RESTOCK = DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build();

    public static void register(IEventBus bus) {
        DATA_COMPONENTS.register("blacklist", () -> BLACKLIST);
        DATA_COMPONENTS.register("sort_mode", () -> SORT_MODE);
        DATA_COMPONENTS.register("toolbox_contents", () -> TOOLBOX_CONTENTS);
        DATA_COMPONENTS.register("toolbox_filters", () -> TOOLBOX_FILTERS);
        DATA_COMPONENTS.register("toolbox_restock", () -> TOOLBOX_RESTOCK);
        DATA_COMPONENTS.register(bus);
    }
}
