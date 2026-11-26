package com.stackultra.essentials.content.backpack;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.ItemPullHelper;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class MagneticBackpackTickHandler {

    private static final int BACKTANK_RECHARGE_PER_TICK = 15;
    private static double airRemainder = 0D;

    private MagneticBackpackTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide())
            return;

        ItemStack backpack = MagneticBackpackEquipment.findCarriedMagneticBackpack(player);
        if (backpack.isEmpty() || !(backpack.getItem() instanceof MagneticBackpackItem item))
            return;

        int charge = item.getCharge(backpack);
        int max = item.getMaxCharge(backpack);
        boolean enabled = backpack.getOrDefault(AllDataComponents.ENABLED, true);
        boolean charging = false;
        boolean changed = false;

        if (charge < max) {
            List<ItemStack> backtanks = BacktankSupport.getBacktanks(player);
            int air = BacktankSupport.totalAir(backtanks);
            int backtankMaxAir = BacktankSupport.totalMaxAir(backtanks);
            if (air > 0) {
                int before = charge;
                charge = Math.min(max, charge + BACKTANK_RECHARGE_PER_TICK);
                int chargeDelta = charge - before;
                airRemainder += (chargeDelta / (double) max) * backtankMaxAir;
                int consume = Math.min((int) airRemainder, air);
                if (consume > 0) {
                    BacktankSupport.consumeAir(player, backtanks, consume);
                    airRemainder -= consume;
                }
                changed = true;
                charging = true;
            }
        }

        if (enabled && charge > 0) {
            double radius = MagnetTier.TIER_2.pullRadius();
            List<ItemStack> filters = MagneticBackpackItem.getFilters(backpack);

            List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(radius), ItemEntity::isAlive);

            BackpackInventory inventory = BackpackInventory.forStack(backpack);

            for (ItemEntity itemEntity : items) {
                if (!matchesFilter(player, filters, itemEntity.getItem(), backpack))
                    continue;

                ItemStack remaining = itemEntity.getItem().copy();

                for (int i = 0; i < inventory.getSlots() && !remaining.isEmpty(); i++) {
                    remaining = inventory.insertItem(i, remaining, false);
                }

                if (!remaining.isEmpty()) {
                    remaining = insertIntoMainInventory(player, remaining);
                }

                if (remaining.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(remaining);
                    ItemPullHelper.pullToFeet(itemEntity, player);
                }
            }

            if (!charging) {
                charge--;
                changed = true;
            }
        }

        if (changed) {
            backpack.set(AllDataComponents.CHARGE, Math.clamp(charge, 0, max));
            backpack.set(AllDataComponents.CHARGING, charging);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
        }
    }

    private static boolean matchesFilter(Player player, List<ItemStack> filters, ItemStack stack, ItemStack backpack) {
        if (filters.isEmpty())
            return true;
        boolean matches = false;
        for (ItemStack filter : filters) {
            if (FilterItemStack.of(filter).test(player.level(), stack)) {
                matches = true;
                break;
            }
        }
        boolean blacklist = backpack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        return blacklist ? !matches : matches;
    }

    private static ItemStack insertIntoMainInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack))
            return ItemStack.EMPTY;
        return stack;
    }
}
