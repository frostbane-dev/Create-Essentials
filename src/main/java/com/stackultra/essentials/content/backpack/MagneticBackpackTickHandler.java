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

        List<ItemStack> backpacks = MagneticBackpackEquipment.getCarriedMagneticBackpacks(player);
        if (backpacks.isEmpty())
            return;

        List<ItemStack> backtanks = null;
        int availableAir = 0;
        int backtankMaxAir = 0;

        for (ItemStack backpack : backpacks) {
            if (backpack.isEmpty() || !(backpack.getItem() instanceof MagneticBackpackItem item))
                continue;

            int charge = item.getCharge(backpack);
            int max = item.getMaxCharge(backpack);
            boolean enabled = backpack.getOrDefault(AllDataComponents.ENABLED, true);
            boolean charging = false;
            boolean changed = false;

            if (charge < max) {
                if (backtanks == null) {
                    backtanks = BacktankSupport.getBacktanks(player);
                    availableAir = BacktankSupport.totalAir(backtanks);
                    backtankMaxAir = BacktankSupport.totalMaxAir(backtanks);
                }
                if (availableAir > 0) {
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
                    changed = true;
                    charging = true;
                }
            }

            if (enabled && charge > 0) {
                double radius = MagnetTier.TIER_2.pullRadius();
                List<ItemStack> filters = MagneticBackpackItem.getFilters(backpack);

                List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(radius), ItemEntity::isAlive);

                MagneticBackpackMenu openMenu = (player.containerMenu instanceof MagneticBackpackMenu menu
                        && menu.isBackingStack(backpack)) ? menu : null;
                BackpackInventory inventory = openMenu != null && openMenu.getBackpackInventory() != null
                        ? openMenu.getBackpackInventory()
                        : BackpackInventory.forStack(backpack);
                PortableToolboxInventory toolbox = openMenu != null && openMenu.getToolboxInventory() != null
                        ? openMenu.getToolboxInventory()
                        : PortableToolboxInventory.forStack(backpack);

                boolean pulledIntoToolbox = false;
                boolean pulledIntoBackpack = false;
                boolean pulledIntoPlayer = false;

                for (ItemEntity itemEntity : items) {
                    if (!itemEntity.isAlive())
                        continue;
                    if (!matchesFilter(player, filters, itemEntity.getItem(), backpack))
                        continue;

                    ItemStack remaining = itemEntity.getItem().copy();

                    // Priority 1: matching physically populated 8-slot portable toolbox compartments
                    int beforeToolbox = remaining.getCount();
                    remaining = toolbox.insertIntoMatchingCompartment(remaining, false);
                    if (remaining.getCount() != beforeToolbox)
                        pulledIntoToolbox = true;

                    // Priority 2: 27 main backpack storage slots
                    if (!remaining.isEmpty()) {
                        int beforeBackpack = remaining.getCount();
                        for (int i = 0; i < inventory.getSlots() && !remaining.isEmpty(); i++) {
                            remaining = inventory.insertItem(i, remaining, false);
                        }
                        if (remaining.getCount() != beforeBackpack)
                            pulledIntoBackpack = true;
                    }

                    // Priority 3: player main inventory fallback
                    if (!remaining.isEmpty()) {
                        int beforePlayer = remaining.getCount();
                        remaining = insertIntoMainInventory(player, remaining);
                        if (remaining.getCount() != beforePlayer)
                            pulledIntoPlayer = true;
                    }

                    if (remaining.isEmpty()) {
                        itemEntity.discard();
                    } else {
                        itemEntity.setItem(remaining);
                        ItemPullHelper.pullToFeet(itemEntity, player);
                    }
                }

                if (pulledIntoToolbox || pulledIntoBackpack)
                    changed = true;

                if (pulledIntoToolbox || pulledIntoBackpack || pulledIntoPlayer) {
                    player.containerMenu.broadcastChanges();
                }

                if (pulledIntoToolbox && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    PortableToolboxHandler.onToolboxModified(serverPlayer, backpack, toolbox);
                }

                if (!charging) {
                    charge--;
                    changed = true;
                }
            }

            if (backpack.getOrDefault(AllDataComponents.CHARGING, false) != charging)
                changed = true;

            if (changed) {
                backpack.set(AllDataComponents.CHARGE, Math.clamp(charge, 0, max));
                backpack.set(AllDataComponents.CHARGING, charging);
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
            }
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
