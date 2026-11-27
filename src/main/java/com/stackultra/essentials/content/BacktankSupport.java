package com.stackultra.essentials.content;

import com.simibubi.create.AllTags;
import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.stackultra.essentials.compat.curios.CuriosCompat;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
            stacks.addAll(CuriosCompat.getCuriosAirSources(entity));
            return stacks;
        });
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

    public static int referenceMaxAir(List<ItemStack> backtanks) {
        if (backtanks == null || backtanks.isEmpty())
            return 0;
        return BacktankUtil.maxAir(backtanks.get(0));
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
