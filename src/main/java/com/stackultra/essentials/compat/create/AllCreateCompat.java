package com.stackultra.essentials.compat.create;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.registry.AllBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Registers all native Create 6.0+ integrations for Create: Essentials:
 * <ul>
 *   <li>{@link MountedItemStorageType} for placed backpacks on Contraptions (Trains, Bearings, Gantries, Minecarts).</li>
 *   <li>{@link DisplaySource} entries for direct Display Link attachments (Fill Level, Item Count, Magnetic Charge).</li>
 *   <li>{@link ArmInteractionPointType} enabling direct Mechanical Arm interaction with placed backpacks.</li>
 * </ul>
 */
public final class AllCreateCompat {

    public static final DeferredRegister<MountedItemStorageType<?>> MOUNTED_ITEM_STORAGE_TYPES =
            DeferredRegister.create(CreateRegistries.MOUNTED_ITEM_STORAGE_TYPE, CreateEssentials.MOD_ID);

    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
            DeferredRegister.create(CreateRegistries.DISPLAY_SOURCE, CreateEssentials.MOD_ID);

    public static final DeferredRegister<ArmInteractionPointType> ARM_INTERACTION_POINT_TYPES =
            DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, CreateEssentials.MOD_ID);

    public static final DeferredHolder<MountedItemStorageType<?>, BackpackMountedStorageType> BACKPACK_STORAGE =
            MOUNTED_ITEM_STORAGE_TYPES.register("backpack", BackpackMountedStorageType::new);

    public static final DeferredHolder<DisplaySource, BackpackDisplaySources.FillLevel> BACKPACK_FILL_LEVEL =
            DISPLAY_SOURCES.register("backpack_fill_level", BackpackDisplaySources.FillLevel::new);

    public static final DeferredHolder<DisplaySource, BackpackDisplaySources.ItemCount> BACKPACK_ITEM_COUNT =
            DISPLAY_SOURCES.register("backpack_item_count", BackpackDisplaySources.ItemCount::new);

    public static final DeferredHolder<DisplaySource, BackpackDisplaySources.ChargeLevel> BACKPACK_CHARGE =
            DISPLAY_SOURCES.register("backpack_charge", BackpackDisplaySources.ChargeLevel::new);

    public static final DeferredHolder<ArmInteractionPointType, BackpackArmInteractionPointType> BACKPACK_ARM_POINT =
            ARM_INTERACTION_POINT_TYPES.register("backpack", BackpackArmInteractionPointType::new);

    private AllCreateCompat() {
    }

    public static void register(IEventBus modBus) {
        MOUNTED_ITEM_STORAGE_TYPES.register(modBus);
        DISPLAY_SOURCES.register(modBus);
        ARM_INTERACTION_POINT_TYPES.register(modBus);
        modBus.addListener(AllCreateCompat::onCommonSetup);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Contraption MountedItemStorage associations
            MountedItemStorageType.REGISTRY.register(AllBlocks.MECHANICAL_BACKPACK.get(), BACKPACK_STORAGE.get());
            MountedItemStorageType.REGISTRY.register(AllBlocks.MAGNETIC_BACKPACK.get(), BACKPACK_STORAGE.get());

            // Direct Display Link source associations
            DisplaySource.BY_BLOCK.add(AllBlocks.MECHANICAL_BACKPACK.get(), BACKPACK_FILL_LEVEL.get());
            DisplaySource.BY_BLOCK.add(AllBlocks.MECHANICAL_BACKPACK.get(), BACKPACK_ITEM_COUNT.get());

            DisplaySource.BY_BLOCK.add(AllBlocks.MAGNETIC_BACKPACK.get(), BACKPACK_FILL_LEVEL.get());
            DisplaySource.BY_BLOCK.add(AllBlocks.MAGNETIC_BACKPACK.get(), BACKPACK_ITEM_COUNT.get());
            DisplaySource.BY_BLOCK.add(AllBlocks.MAGNETIC_BACKPACK.get(), BACKPACK_CHARGE.get());
        });
    }
}
