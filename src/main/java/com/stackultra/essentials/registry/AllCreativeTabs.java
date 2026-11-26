package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CreateEssentials.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ESSENTIALS =
            CREATIVE_MODE_TABS.register("essentials", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.createessentials.essentials"))
                    .icon(() -> new ItemStack(AllItems.MAGNETIC_BACKPACK.get()))
                    .displayItems((params, output) -> {
                        output.accept(AllItems.ROTATIONAL_MAGNET.get());
                        output.accept(AllItems.PRECISION_MAGNET.get());
                        output.accept(AllItems.MECHANICAL_BACKPACK.get());
                        output.accept(AllItems.NETHERITE_BACKPACK.get());
                        output.accept(AllItems.MAGNETIC_BACKPACK.get());

                        output.accept(emptyVariant(AllItems.ROTATIONAL_MAGNET.get()));
                        output.accept(emptyVariant(AllItems.PRECISION_MAGNET.get()));
                        output.accept(emptyVariant(AllItems.MAGNETIC_BACKPACK.get()));
                    })
                    .build());

    private static ItemStack emptyVariant(Item item) {
        ItemStack stack = new ItemStack(item);
        stack.set(AllDataComponents.CHARGE, 0);
        return stack;
    }

    public static void register(IEventBus bus) {
        CREATIVE_MODE_TABS.register(bus);
    }
}
