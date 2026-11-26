package com.stackultra.essentials.content;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public final class BacktankSupport {

    public static final int AIR_PER_ITEM = 1;

    private static boolean registered = false;

    private BacktankSupport() {
    }

    public static void init() {
        if (registered)
            return;
        registered = true;

        BacktankUtil.addBacktankSupplier(entity -> {
            List<ItemStack> stacks = new ArrayList<>();
            if (entity instanceof Player player) {
                for (ItemStack stack : player.getInventory().items) {
                    if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(stack))
                        stacks.add(stack);
                }
                ItemStack offhand = player.getOffhandItem();
                if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(offhand))
                    stacks.add(offhand);
            }
            if (ModList.get().isLoaded("curios")) {
                try {
                    stacks.addAll(getCuriosAirSources(entity));
                } catch (Throwable ignored) {
                }
            }
            return stacks;
        });
    }

    private static List<ItemStack> getCuriosAirSources(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    handler.getCurios().values().forEach(stacksHandler -> {
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                            if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(stack))
                                stacks.add(stack);
                        }
                    });
                    return stacks;
                })
                .orElseGet(ArrayList::new);
    }

    public static List<ItemStack> getBacktanks(LivingEntity entity) {
        return BacktankUtil.getAllWithAir(entity);
    }

    public static boolean hasBacktankAir(LivingEntity entity) {
        return !BacktankUtil.getAllWithAir(entity).isEmpty();
    }

    public static int totalAir(List<ItemStack> backtanks) {
        int total = 0;
        for (ItemStack backtank : backtanks)
            total += BacktankUtil.getAir(backtank);
        return total;
    }

    public static int totalMaxAir(List<ItemStack> backtanks) {
        int total = 0;
        for (ItemStack backtank : backtanks)
            total += BacktankUtil.maxAir(backtank);
        return total;
    }

    public static void consumeAir(LivingEntity entity, List<ItemStack> backtanks, int amount) {
        int remaining = amount;
        for (ItemStack backtank : backtanks) {
            if (remaining <= 0)
                break;
            int air = BacktankUtil.getAir(backtank);
            if (air <= 0)
                continue;
            int take = Math.min(air, remaining);
            BacktankUtil.consumeAir(entity, backtank, take);
            remaining -= take;
        }
    }
}
