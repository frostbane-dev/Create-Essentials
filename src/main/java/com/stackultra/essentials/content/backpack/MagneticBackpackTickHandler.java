package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.BacktankChargeController;
import com.stackultra.essentials.content.BacktankSupport;
import com.stackultra.essentials.content.ItemPullHelper;
import com.stackultra.essentials.content.MagnetFilterHelper;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.List;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class MagneticBackpackTickHandler {

    private MagneticBackpackTickHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.isSpectator() || !player.isAlive())
            return;

        List<ItemStack> backpacks = MagneticBackpackEquipment.getCarriedMagneticBackpacks(player);
        if (backpacks.isEmpty())
            return;

        List<ItemStack> backtanks = BacktankSupport.getBacktanks(player);
        int availableAir = BacktankSupport.totalAir(backtanks);
        int referenceMaxAir = BacktankSupport.referenceMaxAir(backtanks);

        for (ItemStack backpack : backpacks) {
            if (backpack.isEmpty() || !(backpack.getItem() instanceof MagneticBackpackItem item))
                continue;

            int charge = item.getCharge(backpack);
            int max = item.getMaxCharge(backpack);
            boolean enabled = backpack.getOrDefault(AllDataComponents.ENABLED, true);
            boolean changed = false;

            BacktankChargeController.StepResult step = BacktankChargeController.step(
                    backpack,
                    charge,
                    max,
                    enabled,
                    availableAir,
                    referenceMaxAir,
                    consume -> BacktankSupport.consumeAir(player, backtanks, consume));
            availableAir = Math.max(0, availableAir - step.airConsumed());

            if (step.activePower()) {
                double radius = MagnetTier.TIER_2.pullRadius();
                List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                        player.getBoundingBox().inflate(radius), ItemPullHelper::canPullOrPickup);

                if (!items.isEmpty()) {
                    List<ItemStack> filters = MagneticBackpackItem.getFilters(backpack);

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
                        if (!ItemPullHelper.canPullOrPickup(itemEntity))
                            continue;
                        if (!MagnetFilterHelper.matchesFilter(player.level(), filters, itemEntity.getItem(), backpack))
                            continue;

                        ItemStack remaining = itemEntity.getItem().copy();

                        // Priority 1: matching toolbox compartments (populated or empty ghost-reserved)
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
                }
            }

            if (backpack.getOrDefault(AllDataComponents.CHARGE, 0) != step.nextCharge()
                    || backpack.getOrDefault(AllDataComponents.CHARGING, false) != step.charging()) {
                changed = true;
            }

            if (changed) {
                backpack.set(AllDataComponents.CHARGE, step.nextCharge());
                backpack.set(AllDataComponents.CHARGING, step.charging());
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
            }
        }
    }

    private static ItemStack insertIntoMainInventory(Player player, ItemStack stack) {
        if (player.getInventory().add(stack))
            return ItemStack.EMPTY;
        return stack;
    }
}
