package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackMenu;
import com.stackultra.essentials.content.backpack.MagneticBackpackMenu;
import com.stackultra.essentials.content.magnet.RotationalMagnetMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, CreateEssentials.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<RotationalMagnetMenu>> ROTATIONAL_MAGNET =
            MENUS.register("rotational_magnet", () ->
                    IMenuTypeExtension.create(RotationalMagnetMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BackpackMenu>> BACKPACK =
            MENUS.register("backpack", () ->
                    IMenuTypeExtension.create(BackpackMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<MagneticBackpackMenu>> MAGNETIC_BACKPACK =
            MENUS.register("magnetic_backpack", () ->
                    IMenuTypeExtension.create(MagneticBackpackMenu::new));

    public static void register(IEventBus bus) {
        MENUS.register(bus);
    }
}
