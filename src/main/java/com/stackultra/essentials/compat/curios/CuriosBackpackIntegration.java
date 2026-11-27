package com.stackultra.essentials.compat.curios;

import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

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

    public static void syncBackMagneticBackpack(Player player, ItemStack backpack) {
        syncBackBackpack(player, backpack);
    }

    public static void syncBackBackpack(Player player, ItemStack backpack) {
        if (backpack == null || backpack.isEmpty())
            return;
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    int fallbackSlot = -1;
                    int matchCount = 0;
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack existing = stacks.getStackInSlot(i);
                        if (existing == backpack) {
                            stacks.setStackInSlot(i, backpack);
                            return;
                        }
                        if (!existing.isEmpty() && existing.getItem() == backpack.getItem()) {
                            fallbackSlot = i;
                            matchCount++;
                        }
                    }
                    if (matchCount == 1 && fallbackSlot >= 0) {
                        stacks.setStackInSlot(fallbackSlot, backpack);
                    }
                });
    }
}
