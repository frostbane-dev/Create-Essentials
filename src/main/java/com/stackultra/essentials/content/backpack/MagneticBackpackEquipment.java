package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.compat.curios.CuriosBackpackIntegration;
import com.stackultra.essentials.content.StackSyncHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

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

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.getItem() instanceof MagneticBackpackItem)
            addUnique(backpacks, chest);

        if (ModList.get().isLoaded("curios")) {
            try {
                ItemStack back = CuriosBackpackIntegration.getBackMagneticBackpack(player);
                if (back.getItem() instanceof MagneticBackpackItem)
                    addUnique(backpacks, back);
            } catch (Throwable ignored) {
            }
        }

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
        if (backpack == null || backpack.isEmpty())
            return;

        // Fast path: exact reference identity on live inventory / armor slots
        if (player.getItemBySlot(EquipmentSlot.CHEST) == backpack) {
            player.setItemSlot(EquipmentSlot.CHEST, backpack);
            broadcastIfServer(player);
            return;
        }

        if (player.getMainHandItem() == backpack) {
            player.setItemInHand(InteractionHand.MAIN_HAND, backpack);
            broadcastIfServer(player);
            return;
        }

        if (player.getOffhandItem() == backpack) {
            player.setItemInHand(InteractionHand.OFF_HAND, backpack);
            broadcastIfServer(player);
            return;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == backpack) {
                inventory.setItem(i, backpack);
                broadcastIfServer(player);
                return;
            }
        }

        // Unified fallback across every unique physical backpack slot (inventory.items already includes main hand)
        List<ItemStack> candidates = new ArrayList<>();
        List<Consumer<ItemStack>> writers = new ArrayList<>();

        candidates.add(player.getItemBySlot(EquipmentSlot.CHEST));
        writers.add(stack -> player.setItemSlot(EquipmentSlot.CHEST, stack));

        candidates.add(player.getOffhandItem());
        writers.add(stack -> player.setItemInHand(InteractionHand.OFF_HAND, stack));

        for (int i = 0; i < inventory.items.size(); i++) {
            final int slotIndex = i;
            candidates.add(inventory.items.get(i));
            writers.add(stack -> inventory.setItem(slotIndex, stack));
        }

        if (ModList.get().isLoaded("curios")) {
            try {
                CuriosBackpackIntegration.collectBackSlots(player, candidates, writers);
            } catch (Throwable ignored) {
            }
        }

        int matchIndex = StackSyncHelper.findMatchingIndex(candidates, backpack);
        if (matchIndex >= 0) {
            writers.get(matchIndex).accept(backpack);
        }
        broadcastIfServer(player);
    }

    private static void broadcastIfServer(Player player) {
        if (player.level().isClientSide())
            return;
        player.containerMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu)
            player.inventoryMenu.broadcastChanges();
    }
}
