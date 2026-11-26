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
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i) == backpack) {
                            stacks.setStackInSlot(i, backpack);
                            return;
                        }
                    }
                });
    }

    public static void syncBackBackpack(Player player, ItemStack backpack) {
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        if (stacks.getStackInSlot(i) == backpack) {
                            stacks.setStackInSlot(i, backpack);
                            return;
                        }
                    }
                });
    }
}
