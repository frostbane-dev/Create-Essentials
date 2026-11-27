package com.stackultra.essentials.compat.curios;

import com.stackultra.essentials.content.StackSyncHelper;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class CuriosBackpackIntegration {

    private CuriosBackpackIntegration() {
    }

    public static ItemStack getBackBackpack(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .map(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.getItem() instanceof MechanicalBackpackItem
                                || stack.getItem() instanceof MagneticBackpackItem)
                            return stack;
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    public static ItemStack getBackMagneticBackpack(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .map(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.getItem() instanceof MagneticBackpackItem)
                            return stack;
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    public static void collectBackSlots(Player player,
                                        List<ItemStack> candidates,
                                        List<Consumer<ItemStack>> writers) {
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        final int slotIndex = i;
                        candidates.add(stacks.getStackInSlot(i));
                        writers.add(stack -> stacks.setStackInSlot(slotIndex, stack));
                    }
                });
    }

    public static void syncBackMagneticBackpack(Player player, ItemStack backpack) {
        syncBackBackpack(player, backpack);
    }

    public static void syncBackBackpack(Player player, ItemStack backpack) {
        if (backpack == null || backpack.isEmpty())
            return;
        List<ItemStack> candidates = new ArrayList<>();
        List<Consumer<ItemStack>> writers = new ArrayList<>();
        collectBackSlots(player, candidates, writers);
        int matchIndex = StackSyncHelper.findMatchingIndex(candidates, backpack);
        if (matchIndex >= 0) {
            writers.get(matchIndex).accept(backpack);
        }
    }
}
