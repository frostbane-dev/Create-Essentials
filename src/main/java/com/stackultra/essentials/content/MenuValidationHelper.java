package com.stackultra.essentials.content;

import com.stackultra.essentials.compat.curios.CuriosCompat;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Shared container validity and open-item slot locking helpers for
 * backpack and magnet menus, preventing broken-block duplication and client/server
 * desync when attempting to pick up, throw, or swap a currently open backpack or magnet stack.
 */
public final class MenuValidationHelper {

    public static final double MAX_INTERACT_DISTANCE_SQ = 64.0D;
    public static final int NO_LOCKED_SLOT = -1;
    public static final int OFFHAND_SLOT = Inventory.SLOT_OFFHAND;

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

        return CuriosCompat.isStackInCurios(player, target);
    }

    /**
     * Resolves the {@link Inventory} container slot index ({@code 0..35} for hotbar/main inventory,
     * {@link #OFFHAND_SLOT} ({@code 40}) for offhand, or {@link #NO_LOCKED_SLOT} if worn in chest/Curios
     * or not in {@link Inventory}) so the client menu can lock the exact same slot locally.
     */
    public static int findPlayerInventorySlot(Player player, ItemStack target) {
        if (player == null || target == null || target.isEmpty())
            return NO_LOCKED_SLOT;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == target)
                return i;
        }
        if (player.getOffhandItem() == target)
            return OFFHAND_SLOT;

        return NO_LOCKED_SLOT;
    }

    public static boolean isLockedSlot(Slot slot,
                                       Inventory playerInventory,
                                       ItemStack openStack,
                                       int lockedInventorySlot) {
        if (slot == null)
            return false;
        if (openStack != null && !openStack.isEmpty() && slot.getItem() == openStack)
            return true;
        return lockedInventorySlot >= 0
                && ((playerInventory != null && slot.container == playerInventory)
                || (playerInventory == null && slot.container instanceof Inventory))
                && slot.getContainerSlot() == lockedInventorySlot;
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
        return isLockedOpenStackClick(menu, openStack, NO_LOCKED_SLOT, slotId, dragType, clickType, player);
    }

    /**
     * Client- and server-consistent locked-slot click guard. Matches by exact {@link ItemStack}
     * reference on the server and by synced {@link Inventory} slot index on the client so
     * client-predicted clicks ({@code Q}/THROW, PICKUP, SWAP, etc.) never leave ghost items.
     */
    public static boolean isLockedOpenStackClick(AbstractContainerMenu menu,
                                                 ItemStack openStack,
                                                 int lockedInventorySlot,
                                                 int slotId,
                                                 int dragType,
                                                 ClickType clickType,
                                                 Player player) {
        boolean hasOpenStack = openStack != null && !openStack.isEmpty();
        boolean hasLockedIndex = lockedInventorySlot >= 0;
        if (!hasOpenStack && !hasLockedIndex)
            return false;

        Inventory playerInventory = player != null ? player.getInventory() : null;

        // Direct clicks on the slot holding the open backpack/magnet
        if (slotId >= 0 && slotId < menu.slots.size()) {
            Slot slot = menu.slots.get(slotId);
            if (isLockedSlot(slot, playerInventory, openStack, lockedInventorySlot)) {
                if (clickType == ClickType.PICKUP
                        || clickType == ClickType.QUICK_MOVE
                        || clickType == ClickType.SWAP
                        || clickType == ClickType.THROW
                        || clickType == ClickType.PICKUP_ALL) {
                    return true;
                }
            }
        }

        // Hotbar (0..8) or offhand (40) SWAP targeting the open stack from any hovered slot
        if (clickType == ClickType.SWAP) {
            if (hasLockedIndex && dragType == lockedInventorySlot)
                return true;
            if (hasOpenStack && player != null) {
                if (dragType == OFFHAND_SLOT && player.getOffhandItem() == openStack)
                    return true;
                if (dragType >= 0 && dragType < Inventory.getSelectionSize()
                        && player.getInventory().getItem(dragType) == openStack) {
                    return true;
                }
            }
        }

        return false;
    }
}
