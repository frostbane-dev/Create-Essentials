package com.stackultra.essentials.content.magnet;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public final class MagnetEquipment {

    private MagnetEquipment() {
    }

    public static List<ItemStack> getEquippedMagnets(Player player) {
        List<ItemStack> magnets = new ArrayList<>(2);

        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RotationalMagnetItem)
                magnets.add(stack);
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RotationalMagnetItem)
            magnets.add(offhand);

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

        if (ModList.get().isLoaded("curios")) {
            try {
                magnets.addAll(getCuriosMagnets(player));
            } catch (Throwable ignored) {
            }
        }

        return magnets;
    }

    private static List<ItemStack> getCuriosMagnets(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    handler.getCurios().values().forEach(stacksHandler -> {
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                            if (stack.getItem() instanceof RotationalMagnetItem)
                                stacks.add(stack);
                        }
                    });
                    return stacks;
                })
                .orElseGet(ArrayList::new);
    }

    public static ItemStack findEquippedMagnet(Player player) {
        for (ItemStack magnet : getEquippedMagnets(player)) {
            if (!magnet.isEmpty())
                return magnet;
        }
        return ItemStack.EMPTY;
    }
}
