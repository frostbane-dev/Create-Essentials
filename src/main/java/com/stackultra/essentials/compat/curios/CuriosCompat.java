package com.stackultra.essentials.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.function.Consumer;

/**
 * Zero-Curios-import soft-compatibility facade.
 * <p>
 * Every common, menu, equipment, and event class in Create: Essentials calls this facade instead of
 * referencing {@code top.theillusivec4.curios.*} directly, guaranteeing the JVM never loads or verifies
 * any Curios API class when Curios is not installed.
 */
public final class CuriosCompat {

    public static final String CURIOS_MOD_ID = "curios";

    private CuriosCompat() {
    }

    public static boolean isLoaded() {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(CURIOS_MOD_ID);
    }

    public static ItemStack getBackBackpack(Player player) {
        if (!isLoaded() || player == null)
            return ItemStack.EMPTY;
        try {
            return CuriosBackpackIntegration.getBackBackpack(player);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack getBackMagneticBackpack(Player player) {
        if (!isLoaded() || player == null)
            return ItemStack.EMPTY;
        try {
            return CuriosBackpackIntegration.getBackMagneticBackpack(player);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static void collectBackSlots(Player player,
                                        List<ItemStack> candidates,
                                        List<Consumer<ItemStack>> writers) {
        if (!isLoaded() || player == null)
            return;
        try {
            CuriosBackpackIntegration.collectBackSlots(player, candidates, writers);
        } catch (Throwable ignored) {
        }
    }

    public static List<ItemStack> getCuriosMagnets(Player player) {
        if (!isLoaded() || player == null)
            return List.of();
        try {
            return CuriosBackpackIntegration.getCuriosMagnets(player);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static void collectCuriosMagnetSlots(Player player,
                                                List<ItemStack> candidates,
                                                List<Consumer<ItemStack>> writers) {
        if (!isLoaded() || player == null)
            return;
        try {
            CuriosBackpackIntegration.collectCuriosMagnetSlots(player, candidates, writers);
        } catch (Throwable ignored) {
        }
    }

    public static List<ItemStack> getCuriosAirSources(LivingEntity entity) {
        if (!isLoaded() || entity == null)
            return List.of();
        try {
            return CuriosBackpackIntegration.getCuriosAirSources(entity);
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    public static boolean isStackInCurios(Player player, ItemStack target) {
        if (!isLoaded() || player == null || target == null || target.isEmpty())
            return false;
        try {
            return CuriosBackpackIntegration.isStackInCurios(player, target);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
