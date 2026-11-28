package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.compat.curios.CuriosCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MagneticBackpackEquipment {

    private MagneticBackpackEquipment() {
    }

    public static ItemStack findEquippedMagneticBackpack(Player player) {
        ItemStack back = CuriosCompat.getBackMagneticBackpack(player);
        if (!back.isEmpty())
            return back;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MagneticBackpackItem)
            return chest;

        return ItemStack.EMPTY;
    }

    public static ItemStack findCarriedMagneticBackpack(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MagneticBackpackItem)
            return main;

        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MagneticBackpackItem)
            return off;

        ItemStack equipped = findEquippedMagneticBackpack(player);
        if (!equipped.isEmpty())
            return equipped;

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MagneticBackpackItem)
                return stack;
        }

        return ItemStack.EMPTY;
    }

    public static List<ItemStack> getCarriedMagneticBackpacks(Player player) {
        List<ItemStack> backpacks = new ArrayList<>();

        ItemStack back = CuriosCompat.getBackMagneticBackpack(player);
        if (back.getItem() instanceof MagneticBackpackItem)
            addUnique(backpacks, back);

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MagneticBackpackItem)
            addUnique(backpacks, chest);

        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MagneticBackpackItem)
            addUnique(backpacks, main);

        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MagneticBackpackItem)
            addUnique(backpacks, off);

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MagneticBackpackItem)
                addUnique(backpacks, stack);
        }

        return backpacks;
    }

    private static void addUnique(List<ItemStack> list, ItemStack stack) {
        for (ItemStack existing : list) {
            if (existing == stack)
                return;
        }
        list.add(stack);
    }

    public static void syncMagneticBackpack(Player player, ItemStack backpack) {
        BackpackEquipment.syncBackpack(player, backpack);
    }
}
