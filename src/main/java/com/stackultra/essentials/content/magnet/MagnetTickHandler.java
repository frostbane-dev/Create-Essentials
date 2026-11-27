package com.stackultra.essentials.content.magnet;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.BacktankChargeController;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.ItemPullHelper;
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
public class MagnetTickHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide())
            return;

        List<ItemStack> magnets = MagnetEquipment.getCarriedMagnets(player);
        if (magnets.isEmpty())
            return;

        List<ItemStack> backtanks = BacktankSupport.getBacktanks(player);
        int availableAir = BacktankSupport.totalAir(backtanks);
        int referenceMaxAir = BacktankSupport.referenceMaxAir(backtanks);

        for (ItemStack magnet : magnets) {
            if (!(magnet.getItem() instanceof RotationalMagnetItem item))
                continue;

            int charge = item.getCharge(magnet);
            int max = item.getMaxCharge(magnet);
            boolean enabled = magnet.getOrDefault(AllDataComponents.ENABLED, true);

            BacktankChargeController.StepResult step = BacktankChargeController.step(
                    magnet,
                    charge,
                    max,
                    enabled,
                    availableAir,
                    referenceMaxAir,
                    consume -> BacktankSupport.consumeAir(player, backtanks, consume));
            availableAir = Math.max(0, availableAir - step.airConsumed());

            if (step.activePower()) {
                double radius = item.getTier().pullRadius();
                List<ItemStack> filters = item.getTier().hasFilterSlots()
                        ? RotationalMagnetItem.getFilters(magnet)
                        : List.of();

                List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(radius), ItemEntity::isAlive);

                for (ItemEntity itemEntity : items) {
                    if (!matchesFilter(player, filters, itemEntity.getItem(), magnet))
                        continue;

                    ItemPullHelper.pullToFeet(itemEntity, player);
                }
            }

            if (magnet.getOrDefault(AllDataComponents.CHARGE, 0) != step.nextCharge()
                    || magnet.getOrDefault(AllDataComponents.CHARGING, false) != step.charging()) {
                magnet.set(AllDataComponents.CHARGE, step.nextCharge());
                magnet.set(AllDataComponents.CHARGING, step.charging());
                MagnetEquipment.syncMagnet(player, magnet);
            }
        }
    }

    private static boolean matchesFilter(Player player, List<ItemStack> filters, ItemStack stack, ItemStack magnet) {
        if (filters.isEmpty())
            return true;
        boolean matches = false;
        for (ItemStack filter : filters) {
            if (FilterItemStack.of(filter).test(player.level(), stack)) {
                matches = true;
                break;
            }
        }
        boolean blacklist = magnet.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        return blacklist ? !matches : matches;
    }
}
