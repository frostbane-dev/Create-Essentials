package com.stackultra.essentials.content.magnet;

import com.stackultra.essentials.compat.curios.CuriosCompat;
import com.stackultra.essentials.content.StackSyncHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MagnetEquipment {

    private MagnetEquipment() {
    }

    public static List<ItemStack> getEquippedMagnets(Player player) {
        List<ItemStack> magnets = new ArrayList<>(2);

        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof RotationalMagnetItem)
            magnets.add(main);

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RotationalMagnetItem && offhand != main)
            magnets.add(offhand);

        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RotationalMagnetItem && !containsRef(magnets, stack))
                magnets.add(stack);
        }

        return magnets;
    }

    public static List<ItemStack> getCarriedMagnets(Player player) {
        List<ItemStack> magnets = new ArrayList<>();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof RotationalMagnetItem)
                magnets.add(stack);
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RotationalMagnetItem)
            magnets.add(offhand);

        magnets.addAll(CuriosCompat.getCuriosMagnets(player));
        return magnets;
    }

    public static ItemStack findCuriosMagnet(Player player) {
        List<ItemStack> curios = CuriosCompat.getCuriosMagnets(player);
        return curios.isEmpty() ? ItemStack.EMPTY : curios.get(0);
    }

    public static ItemStack findEquippedMagnet(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof RotationalMagnetItem)
            return main;

        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof RotationalMagnetItem)
            return off;

        ItemStack curio = findCuriosMagnet(player);
        if (!curio.isEmpty())
            return curio;

        for (ItemStack magnet : getEquippedMagnets(player)) {
            if (!magnet.isEmpty())
                return magnet;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findTargetPrecisionMagnet(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isPrecisionMagnet(main))
            return main;

        ItemStack off = player.getOffhandItem();
        if (isPrecisionMagnet(off))
            return off;

        for (ItemStack stack : CuriosCompat.getCuriosMagnets(player)) {
            if (isPrecisionMagnet(stack))
                return stack;
        }

        for (ItemStack stack : player.getInventory().items) {
            if (isPrecisionMagnet(stack))
                return stack;
        }

        return ItemStack.EMPTY;
    }

    public static void syncMagnet(Player player, ItemStack magnet) {
        if (player == null || magnet == null || magnet.isEmpty())
            return;

        // Fast path: exact reference identity on live inventory / hand slots
        if (player.getMainHandItem() == magnet) {
            player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
            broadcastIfServer(player);
            return;
        }

        if (player.getOffhandItem() == magnet) {
            player.setItemInHand(InteractionHand.OFF_HAND, magnet);
            broadcastIfServer(player);
            return;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == magnet) {
                inventory.setItem(i, magnet);
                broadcastIfServer(player);
                return;
            }
        }

        // Unified fallback across every unique physical magnet slot (inventory.items already includes main hand)
        List<ItemStack> candidates = new ArrayList<>();
        List<Consumer<ItemStack>> writers = new ArrayList<>();

        candidates.add(player.getOffhandItem());
        writers.add(stack -> player.setItemInHand(InteractionHand.OFF_HAND, stack));

        for (int i = 0; i < inventory.items.size(); i++) {
            final int slotIndex = i;
            candidates.add(inventory.items.get(i));
            writers.add(stack -> inventory.setItem(slotIndex, stack));
        }

        CuriosCompat.collectCuriosMagnetSlots(player, candidates, writers);

        int matchIndex = StackSyncHelper.findMatchingIndex(candidates, magnet);
        if (matchIndex >= 0) {
            writers.get(matchIndex).accept(magnet);
            broadcastIfServer(player);
        }
    }

    private static void broadcastIfServer(Player player) {
        if (player.level().isClientSide())
            return;
        // Only broadcast on the active containerMenu; vanilla transfers state to inventoryMenu on close.
        player.containerMenu.broadcastChanges();
    }

    private static boolean isPrecisionMagnet(ItemStack stack) {
        return stack.getItem() instanceof RotationalMagnetItem item && item.getTier().hasFilterSlots();
    }

    private static boolean containsRef(List<ItemStack> list, ItemStack target) {
        for (ItemStack stack : list) {
            if (stack == target)
                return true;
        }
        return false;
    }
}
