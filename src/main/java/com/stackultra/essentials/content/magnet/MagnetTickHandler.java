package com.stackultra.essentials.content.magnet;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.ItemPullHelper;
import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class MagnetTickHandler {

    private static final int BACKTANK_RECHARGE_PER_TICK = 15;
    private static double airRemainder = 0D;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide())
            return;

        List<ItemStack> backtanks = BacktankSupport.getBacktanks(player);
        int availableAir = BacktankSupport.totalAir(backtanks);
        int backtankMaxAir = BacktankSupport.totalMaxAir(backtanks);

        for (ItemStack magnet : MagnetEquipment.getCarriedMagnets(player)) {
            if (!(magnet.getItem() instanceof RotationalMagnetItem item))
                continue;

            int charge = item.getCharge(magnet);
            int max = item.getMaxCharge(magnet);
            boolean enabled = magnet.getOrDefault(AllDataComponents.ENABLED, true);
            boolean charging = false;

            if (charge < max && availableAir > 0) {
                int before = charge;
                charge = Math.min(max, charge + BACKTANK_RECHARGE_PER_TICK);
                int chargeDelta = charge - before;
                airRemainder += (chargeDelta / (double) max) * backtankMaxAir;
                int consume = Math.min((int) airRemainder, availableAir);
                if (consume > 0) {
                    BacktankSupport.consumeAir(player, backtanks, consume);
                    availableAir -= consume;
                    airRemainder -= consume;
                }
                charging = true;
            }

            if (enabled && charge > 0) {
                double radius = item.getTier().pullRadius();
                List<ItemStack> filters = RotationalMagnetItem.getFilters(magnet);

                List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(radius), ItemEntity::isAlive);

                for (ItemEntity itemEntity : items) {
                    if (!matchesFilter(player, filters, itemEntity.getItem()))
                        continue;

                    ItemPullHelper.pullToFeet(itemEntity, player);
                }

                if (!charging)
                    charge--;
            }

            magnet.set(AllDataComponents.CHARGE, Math.clamp(charge, 0, max));
            magnet.set(AllDataComponents.CHARGING, charging);
        }
    }

    private static boolean matchesFilter(Player player, List<ItemStack> filters, ItemStack stack) {
        if (filters.isEmpty())
            return true;
        for (ItemStack filter : filters) {
            if (FilterItemStack.of(filter).test(player.level(), stack))
                return true;
        }
        return false;
    }
}
