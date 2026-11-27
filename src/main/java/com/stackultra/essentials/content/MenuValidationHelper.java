package com.stackultra.essentials.content;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Shared server-side container validity and open-item slot locking helpers for
 * backpack and magnet menus, preventing broken-block duplication and dropping/swapping
 * a currently open backpack or magnet stack.
 */
public final class MenuValidationHelper {

    public static final double MAX_INTERACT_DISTANCE_SQ = 64.0D;

    private MenuValidationHelper() {
    }

    public static boolean isBlockEntityValid(Player player, BlockEntity blockEntity) {
        if (player == null || blockEntity == null || blockEntity.isRemoved())
            return false;
        Level beLevel = blockEntity.getLevel();
        if (beLevel == null || beLevel != player.level())
            return false;
        if (beLevel.getBlockEntity(blockEntity.getBlockPos()) != blockEntity)
            return false;
        return player.distanceToSqr(Vec3.atCenterOf(blockEntity.getBlockPos())) <= MAX_INTERACT_DISTANCE_SQ;
    }

    public static boolean isPlayerCarryingStack(Player player, ItemStack target) {
        if (player == null || target == null || target.isEmpty())
            return false;

        if (player.getMainHandItem() == target
                || player.getOffhandItem() == target
                || player.getItemBySlot(EquipmentSlot.CHEST) == target) {
            return true;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == target)
                return true;
        }

        if (ModList.get().isLoaded("curios")) {
            try {
                if (isInCurios(player, target))
                    return true;
            } catch (Throwable ignored) {
            }
        }

        return false;
    }

    private static boolean isInCurios(Player player, ItemStack target) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    for (var stacksHandler : handler.getCurios().values()) {
                        var stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i) == target)
                                return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    /**
     * Returns {@code true} if the given click attempts to pick up, shift-move, throw, double-click-collect,
     * or hotbar/offhand-swap the currently open backing {@link ItemStack}.
     */
    public static boolean isLockedOpenStackClick(AbstractContainerMenu menu,
                                                 ItemStack openStack,
                                                 int slotId,
                                                 int dragType,
                                                 ClickType clickType,
                                                 Player player) {
        if (openStack == null || openStack.isEmpty())
            return false;

        // Direct clicks on the slot holding the open backpack/magnet
        if (slotId >= 0 && slotId < menu.slots.size() && menu.slots.get(slotId).getItem() == openStack) {
            if (clickType == ClickType.PICKUP
                    || clickType == ClickType.QUICK_MOVE
                    || clickType == ClickType.SWAP
                    || clickType == ClickType.THROW
                    || clickType == ClickType.PICKUP_ALL) {
                return true;
            }
        }

        // Hotbar (0..8) or offhand (40) SWAP targeting the open stack from any hovered slot
        if (clickType == ClickType.SWAP) {
            if (dragType == 40 && player.getOffhandItem() == openStack)
                return true;
            if (dragType >= 0 && dragType < Inventory.getSelectionSize()
                    && player.getInventory().getItem(dragType) == openStack) {
                return true;
            }
        }

        return false;
    }
}
