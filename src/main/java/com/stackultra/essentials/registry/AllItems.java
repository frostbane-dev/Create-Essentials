package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import com.stackultra.essentials.content.backpack.NetheriteBackpackItem;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(CreateEssentials.MOD_ID);

    public static final DeferredItem<RotationalMagnetItem> ROTATIONAL_MAGNET =
            ITEMS.register("rotational_magnet", () -> new RotationalMagnetItem(
                    MagnetTier.TIER_1,
                    AllBlocks.ROTATIONAL_MAGNET.get(),
                    new Item.Properties()
                            .stacksTo(1)
                            .component(AllDataComponents.CHARGE, MagnetTier.TIER_1.maxCharge(0))));

    public static final DeferredItem<RotationalMagnetItem> PRECISION_MAGNET =
            ITEMS.register("precision_magnet", () -> new RotationalMagnetItem(
                    MagnetTier.TIER_2,
                    AllBlocks.PRECISION_MAGNET.get(),
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
                            .component(AllDataComponents.CHARGE, MagnetTier.TIER_2.maxCharge(0))));

    public static final DeferredItem<MechanicalBackpackItem> MECHANICAL_BACKPACK =
            ITEMS.register("mechanical_backpack", () -> new MechanicalBackpackItem(
                    AllBlocks.MECHANICAL_BACKPACK.get(),
                    new Item.Properties().stacksTo(1)));

    public static final DeferredItem<NetheriteBackpackItem> NETHERITE_BACKPACK =
            ITEMS.register("netherite_backpack", () -> new NetheriteBackpackItem(
                    AllBlocks.MECHANICAL_BACKPACK.get(),
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
                            .fireResistant()));

    public static final DeferredItem<MagneticBackpackItem> MAGNETIC_BACKPACK =
            ITEMS.register("magnetic_backpack", () -> new MagneticBackpackItem(
                    AllBlocks.MAGNETIC_BACKPACK.get(),
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.UNCOMMON)
                            .fireResistant()
                            .component(AllDataComponents.CHARGE, MagnetTier.TIER_2.maxCharge(0))));

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
