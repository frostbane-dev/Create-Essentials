package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.compat.curios.CuriosBackpackIntegration;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

public final class MagneticBackpackEquipment {

    private MagneticBackpackEquipment() {
    }

    public static ItemStack findEquippedMagneticBackpack(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MagneticBackpackItem)
            return chest;

        if (ModList.get().isLoaded("curios")) {
            try {
                ItemStack back = CuriosBackpackIntegration.getBackMagneticBackpack(player);
                if (!back.isEmpty())
                    return back;
            } catch (Throwable ignored) {
            }
        }

        return ItemStack.EMPTY;
    }

    public static ItemStack findCarriedMagneticBackpack(Player player) {
        ItemStack equipped = findEquippedMagneticBackpack(player);
        if (!equipped.isEmpty())
            return equipped;

        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MagneticBackpackItem)
            return main;

        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MagneticBackpackItem)
            return off;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MagneticBackpackItem)
                return stack;
        }

        return ItemStack.EMPTY;
    }

    public static void syncMagneticBackpack(Player player, ItemStack backpack) {
        if (player.getItemBySlot(EquipmentSlot.CHEST) == backpack) {
            player.setItemSlot(EquipmentSlot.CHEST, backpack);
            return;
        }

        if (player.getMainHandItem() == backpack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, backpack);
            return;
        }

        if (player.getOffhandItem() == backpack) {
            player.setItemInHand(InteractionHand.OFF_HAND, backpack);
            return;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == backpack) {
                inventory.setItem(i, backpack);
                return;
            }
        }

        if (ModList.get().isLoaded("curios")) {
            try {
                CuriosBackpackIntegration.syncBackMagneticBackpack(player, backpack);
            } catch (Throwable ignored) {
            }
        }
    }
}
