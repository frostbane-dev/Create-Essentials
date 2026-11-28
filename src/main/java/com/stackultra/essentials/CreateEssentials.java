package com.stackultra.essentials;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;
import com.stackultra.essentials.client.ClientConfig;
import com.stackultra.essentials.client.ClientEvents;
import com.stackultra.essentials.compat.create.AllCreateCompat;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.AllBlocks;
import com.stackultra.essentials.registry.AllCreativeTabs;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import com.stackultra.essentials.registry.AllItems;
import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.AllRecipeSerializers;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.registries.RegisterEvent;

@Mod(CreateEssentials.MOD_ID)
public class CreateEssentials {
    public static final String MOD_ID = "createessentials";

    public static final double MAGNET_STRESS_IMPACT = 8.0D;

    public CreateEssentials(IEventBus modBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientEvents.registerConfigScreen(modContainer);
        }

        AllDataComponents.register(modBus);
        ExtraDataComponents.register(modBus);
        AllBlocks.register(modBus);
        AllItems.register(modBus);
        AllBlockEntities.register(modBus);
        AllMenus.register(modBus);
        AllRecipeSerializers.register(modBus);
        AllCreativeTabs.register(modBus);
        AllCreateCompat.register(modBus);
        modBus.addListener(MechanicalBackpackBlockEntity::registerCapabilities);
        modBus.addListener(MagneticBackpackBlockEntity::registerCapabilities);
        BacktankSupport.init();
        registerStressImpacts();
        modBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(Registries.ITEM))
                registerTooltipModifiers();
        });
    }

    private static void registerTooltipModifiers() {
        registerTooltipModifier(AllItems.ROTATIONAL_MAGNET.get());
        registerTooltipModifier(AllItems.PRECISION_MAGNET.get());
    }

    private static void registerTooltipModifier(Item item) {
        TooltipModifier.REGISTRY.register(item,
                new ItemDescription.Modifier(item, FontHelper.Palette.STANDARD_CREATE));
    }

    private static void registerStressImpacts() {
        BlockStressValues.IMPACTS.registerProvider(block -> {
            if (block == AllBlocks.ROTATIONAL_MAGNET.get()
                    || block == AllBlocks.PRECISION_MAGNET.get()
                    || block == AllBlocks.MAGNETIC_BACKPACK.get())
                return () -> MAGNET_STRESS_IMPACT;
            return null;
        });
    }
}