package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

/**
 * Shared container menu base for Mechanical, Netherite, and Magnetic Backpacks.
 * Consolidates 27-slot storage, 8-compartment portable toolbox sync & clicks,
 * transfer/sort operations, open-item slot locking, and container validity checks.
 */
public abstract class AbstractBackpackMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int STORAGE_SLOTS = BackpackInventory.SLOTS;
    public static final int TOOLBOX_SLOTS = PortableToolboxInventory.COMPARTMENTS;

    protected final Inventory playerInventory;
    protected final IItemHandler storageInventory;
    protected final PortableToolboxInventory toolboxInventory;
    protected final IItemHandler toolboxSlotBacking;
    protected final ItemStack backpackStack;
    protected final int lockedInventorySlot;
    @Nullable
    protected final DyeColor backpackColor;

    protected SortMode sortMode;
    protected boolean restockEnabled;
    protected final int[] syncedToolboxCounts = new int[TOOLBOX_SLOTS];

    protected AbstractBackpackMenu(MenuType<?> menuType,
                                   int windowId,
                                   Inventory playerInventory,
                                   IItemHandler storageInventory,
                                   PortableToolboxInventory toolboxInventory,
                                   IItemHandler toolboxSlotBacking,
                                   ItemStack backpackStack,
                                   int lockedInventorySlot,
                                   SortMode initialSortMode,
                                   boolean initialRestockEnabled,
                                   @Nullable DyeColor initialBackpackColor) {
        super(menuType, windowId);
        this.playerInventory = playerInventory;
        this.storageInventory = storageInventory;
        this.toolboxInventory = toolboxInventory;
        this.toolboxSlotBacking = toolboxSlotBacking;
        this.backpackStack = backpackStack;
        this.lockedInventorySlot = lockedInventorySlot;
        this.sortMode = initialSortMode != null ? initialSortMode : SortMode.NAME;
        this.restockEnabled = initialRestockEnabled;
        this.backpackColor = initialBackpackColor;
    }

    @Nullable
    public DyeColor getBackpackColor() {
        return backpackColor;
    }

    @Nullable
    protected abstract BlockEntity getBackingBlockEntity();

    protected boolean isMountedContext() {
        return false;
    }

    protected boolean isMountedValid(Player player) {
        return false;
    }

    protected SortMode readMountedSortMode() {
        return sortMode;
    }

    protected void writeMountedSortMode(SortMode mode) {
    }

    protected boolean readMountedRestockEnabled() {
        return restockEnabled;
    }

    protected void writeMountedRestockEnabled(boolean enabled) {
    }

    protected abstract SortMode readBlockEntitySortMode();

    protected abstract void writeBlockEntitySortMode(SortMode mode);

    protected abstract boolean readBlockEntityRestockEnabled();

    protected abstract void writeBlockEntityRestockEnabled(boolean enabled);

    protected abstract void syncBlockEntity();

    protected abstract void syncItemStack(Player player, ItemStack stack);

    public abstract int getToolboxSlotStart();

    public abstract int getPlayerSlotStart();

    protected boolean isProtectedSlot(int slotId) {
        return isToolboxSlot(slotId);
    }

    /**
     * Subclass hook for handling custom slots (e.g. Magnetic Backpack filter slots) before
     * standard toolbox/storage click handling.
     *
     * @return {@code true} if the click was fully handled.
     */
    protected boolean handleCustomSlotClick(int slotId, int dragType, ClickType clickType, Player player) {
        return false;
    }

    /**
     * Subclass hook invoked at the start of {@link #removed(Player)} before shared persistence.
     */
    protected void onBeforeRemoved(Player player) {
    }

    protected void addCommonStateDataSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (isMountedContext())
                    return readMountedSortMode().ordinal();
                return getBackingBlockEntity() != null ? readBlockEntitySortMode().ordinal() : sortMode.ordinal();
            }

            @Override
            public void set(int value) {
                sortMode = SortMode.byOrdinal(value);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (isMountedContext())
                    return readMountedRestockEnabled() ? 1 : 0;
                if (getBackingBlockEntity() != null)
                    return readBlockEntityRestockEnabled() ? 1 : 0;
                if (!backpackStack.isEmpty())
                    return backpackStack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true) ? 1 : 0;
                return restockEnabled ? 1 : 0;
            }

            @Override
            public void set(int value) {
                restockEnabled = value != 0;
            }
        });

        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            final int compartment = c;
            addDataSlot(new DataSlot() {
                @Override
                public int get() {
                    return toolboxInventory.getTotalCount(compartment);
                }

                @Override
                public void set(int value) {
                    syncedToolboxCounts[compartment] = Math.max(0, value);
                }
            });
        }
    }

    protected void addStorageSlots(int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(storageInventory, row * 9 + col,
                        x + col * 18, y + row * 18));
            }
        }
    }

    protected void addToolboxSlots(int x, int y) {
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            addSlot(new SlotItemHandler(toolboxSlotBacking, c, x + c * 18, y));
        }
    }

    protected void addPlayerInventorySlots(int x, int playerY, int hotbarY) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        x + col * 18, playerY + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return !isLockedOpenSlot(this);
                    }
                });
            }
        }
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot(playerInventory, hotbar, x + hotbar * 18, hotbarY) {
                @Override
                public boolean mayPickup(Player player) {
                    return !isLockedOpenSlot(this);
                }
            });
        }
    }

    protected boolean isLockedOpenSlot(Slot slot) {
        return MenuValidationHelper.isLockedSlot(slot, playerInventory, backpackStack, lockedInventorySlot);
    }

    public boolean isToolboxSlot(int slotId) {
        return slotId >= getToolboxSlotStart() && slotId < getPlayerSlotStart();
    }

    public boolean isBlockEntityContext() {
        return getBackingBlockEntity() != null;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public boolean isBackingStack(ItemStack stack) {
        return getBackingBlockEntity() == null && !backpackStack.isEmpty() && backpackStack == stack;
    }

    public BackpackInventory getBackpackInventory() {
        return storageInventory instanceof BackpackInventory inv ? inv : null;
    }

    @Override
    public PortableToolboxInventory getToolboxInventory() {
        return toolboxInventory;
    }

    @Override
    public SortMode getSortMode() {
        if (isMountedContext() && !playerInventory.player.level().isClientSide())
            return readMountedSortMode();
        return getBackingBlockEntity() != null && !playerInventory.player.level().isClientSide()
                ? readBlockEntitySortMode() : sortMode;
    }

    @Override
    public void setSortMode(SortMode mode, Player player) {
        SortMode resolved = mode != null ? mode : SortMode.NAME;
        this.sortMode = resolved;
        if (isMountedContext()) {
            writeMountedSortMode(resolved);
            broadcastChanges();
            return;
        }
        BlockEntity be = getBackingBlockEntity();
        if (be != null) {
            if (!be.isRemoved())
                writeBlockEntitySortMode(resolved);
        } else if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.SORT_MODE, resolved);
            syncItemStack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public boolean isRestockEnabled() {
        if (isMountedContext() && !playerInventory.player.level().isClientSide())
            return readMountedRestockEnabled();
        return getBackingBlockEntity() != null && !playerInventory.player.level().isClientSide()
                ? readBlockEntityRestockEnabled() : restockEnabled;
    }

    @Override
    public void setRestockEnabled(boolean enabled, Player player) {
        this.restockEnabled = enabled;
        if (isMountedContext()) {
            writeMountedRestockEnabled(enabled);
            broadcastChanges();
            return;
        }
        BlockEntity be = getBackingBlockEntity();
        if (be != null) {
            if (!be.isRemoved())
                writeBlockEntityRestockEnabled(enabled);
        } else if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.TOOLBOX_RESTOCK, enabled);
            syncItemStack(player, backpackStack);
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.notifyRestockToggled(serverPlayer, backpackStack);
            }
        }
        broadcastChanges();
    }

    @Override
    public int getToolboxCount(int compartment) {
        if (compartment < 0 || compartment >= TOOLBOX_SLOTS)
            return 0;
        if (playerInventory.player.level().isClientSide())
            return syncedToolboxCounts[compartment];
        return toolboxInventory.getTotalCount(compartment);
    }

    @Override
    public void transferMatchingToStorage(Player player) {
        if (BackpackInventoryOps.transferMatchingToStorage(
                this, toolboxInventory, 0, STORAGE_SLOTS, getPlayerSlotStart(), this.slots.size(),
                player, backpackStack.isEmpty() ? null : backpackStack)) {
            syncBacking(player);
        }
    }

    @Override
    public void transferMatchingToInventory(Player player) {
        if (BackpackInventoryOps.transferMatchingToInventory(
                this, toolboxInventory, 0, STORAGE_SLOTS, getPlayerSlotStart(), this.slots.size(), player)) {
            syncBacking(player);
        }
    }

    @Override
    public void sortStorage(Player player) {
        if (BackpackInventoryOps.sortStorage(this, toolboxInventory, 0, STORAGE_SLOTS, getSortMode())) {
            syncBacking(player);
        }
    }

    protected void syncBacking(Player player) {
        BlockEntity be = getBackingBlockEntity();
        if (be != null) {
            if (!be.isRemoved())
                syncBlockEntity();
        } else if (!backpackStack.isEmpty()) {
            toolboxInventory.save(backpackStack);
            syncItemStack(player, backpackStack);
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.onToolboxModified(serverPlayer, backpackStack, toolboxInventory);
            }
        }
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!player.level().isClientSide() && !stillValid(player))
            return;
        if (MenuValidationHelper.isLockedOpenStackClick(
                this, backpackStack, lockedInventorySlot, slotId, dragType, clickType, player)) {
            return;
        }
        if (handleCustomSlotClick(slotId, dragType, clickType, player))
            return;
        if (isToolboxSlot(slotId)) {
            // Allow drag-split state machine to finish cleanly without touching toolbox slots (canDragTo is false)
            if (clickType == ClickType.QUICK_CRAFT) {
                super.clicked(slotId, dragType, clickType, player);
                return;
            }
            if (!player.level().isClientSide()) {
                int compartment = slotId - getToolboxSlotStart();
                if (BackpackInventoryOps.handleToolboxSlotClick(
                        this, toolboxInventory, compartment, dragType, clickType, player,
                        getPlayerSlotStart(), this.slots.size(),
                        backpackStack.isEmpty() ? null : backpackStack)) {
                    syncBacking(player);
                }
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slotIn) {
        return !isProtectedSlot(slotIn.index) && !isLockedOpenSlot(slotIn);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return !isProtectedSlot(slotIn.index) && !isLockedOpenSlot(slotIn);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isProtectedSlot(index))
            return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            if (isLockedOpenSlot(slot))
                return ItemStack.EMPTY;
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < STORAGE_SLOTS) {
                if (!moveItemStackTo(stack, getPlayerSlotStart(), this.slots.size(), true))
                    return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, STORAGE_SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty())
                slot.setByPlayer(ItemStack.EMPTY);
            else
                slot.setChanged();
        }
        return result;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        onBeforeRemoved(player);
        if (!player.level().isClientSide()) {
            BlockEntity be = getBackingBlockEntity();
            if (be != null) {
                if (!be.isRemoved())
                    syncBlockEntity();
            } else if (!backpackStack.isEmpty() && MenuValidationHelper.isPlayerCarryingStack(player, backpackStack)) {
                toolboxInventory.save(backpackStack);
                syncItemStack(player, backpackStack);
                if (player instanceof ServerPlayer serverPlayer) {
                    PortableToolboxHandler.onToolboxModified(serverPlayer, backpackStack, toolboxInventory);
                }
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide())
            return true;
        if (isMountedContext())
            return isMountedValid(player);
        BlockEntity be = getBackingBlockEntity();
        return be != null
                ? MenuValidationHelper.isBlockEntityValid(player, be)
                : MenuValidationHelper.isPlayerCarryingStack(player, backpackStack);
    }
}
