package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class BackpackMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int BACKPACK_SLOTS = BackpackInventory.SLOTS;

    private final Inventory playerInventory;
    private final IItemHandler backpackInventory;
    @Nullable
    private final MechanicalBackpackBlockEntity blockEntity;
    private final ItemStack backpackStack;
    private SortMode sortMode;

    public BackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();
        MechanicalBackpackBlockEntity found = null;
        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MechanicalBackpackBlockEntity backpack) {
                    backpack.readClient(tag, extraData.registryAccess());
                    found = backpack;
                }
            }
        }
        this.blockEntity = found;
        this.backpackStack = ItemStack.EMPTY;
        this.backpackInventory = found != null ? found.getItemHandler() : new BackpackInventory();
        this.sortMode = found != null ? found.getSortMode() : SortMode.NAME;
        addBackpackSlots();
        addPlayerSlots();
        addSortModeDataSlot();
    }

    private BackpackMenu(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.backpackStack = ItemStack.EMPTY;
        this.backpackInventory = be.getItemHandler();
        this.sortMode = be.getSortMode();
        addBackpackSlots();
        addPlayerSlots();
        addSortModeDataSlot();
    }

    private BackpackMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = null;
        this.backpackStack = stack;
        this.backpackInventory = BackpackInventory.forStack(stack,
                () -> BackpackEquipment.syncBackpack(inv.player, stack));
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        addBackpackSlots();
        addPlayerSlots();
        addSortModeDataSlot();
    }

    public static BackpackMenu create(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        return new BackpackMenu(windowId, inv, be);
    }

    public static BackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new BackpackMenu(windowId, inv, stack);
    }

    private void addSortModeDataSlot() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                return blockEntity != null ? blockEntity.getSortMode().ordinal() : sortMode.ordinal();
            }

            @Override
            public void set(int value) {
                sortMode = SortMode.byOrdinal(value);
            }
        });
    }

    private void addBackpackSlots() {
        int startX = 8;
        int startY = 18;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = row * 9 + col;
                addSlot(new SlotItemHandler(backpackInventory, index, startX + col * 18, startY + row * 18));
            }
        }
    }

    private void addPlayerSlots() {
        int startX = 8;
        int startY = 85;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, startX + col * 18, startY + row * 18));
            }
        }
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot(playerInventory, hotbar, startX + hotbar * 18, startY + 58));
        }
    }

    @Override
    public SortMode getSortMode() {
        return blockEntity != null ? blockEntity.getSortMode() : sortMode;
    }

    @Override
    public void setSortMode(SortMode mode, Player player) {
        SortMode resolved = mode != null ? mode : SortMode.NAME;
        this.sortMode = resolved;
        if (blockEntity != null) {
            blockEntity.setSortMode(resolved);
        } else if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.SORT_MODE, resolved);
            BackpackEquipment.syncBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public void transferMatchingToStorage(Player player) {
        if (BackpackInventoryOps.transferMatchingToStorage(
                this, 0, BACKPACK_SLOTS, BACKPACK_SLOTS, this.slots.size(),
                player, backpackStack.isEmpty() ? null : backpackStack)) {
            syncBacking(player);
        }
    }

    @Override
    public void transferMatchingToInventory(Player player) {
        if (BackpackInventoryOps.transferMatchingToInventory(
                this, 0, BACKPACK_SLOTS, BACKPACK_SLOTS, this.slots.size(), player)) {
            syncBacking(player);
        }
    }

    @Override
    public void sortStorage(Player player) {
        if (BackpackInventoryOps.sortStorage(this, 0, BACKPACK_SLOTS, getSortMode())) {
            syncBacking(player);
        }
    }

    private void syncBacking(Player player) {
        if (blockEntity != null) {
            blockEntity.setChanged();
        } else if (!backpackStack.isEmpty()) {
            BackpackEquipment.syncBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BACKPACK_SLOTS) {
                if (!moveItemStackTo(stack, BACKPACK_SLOTS, this.slots.size(), true))
                    return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, BACKPACK_SLOTS, false)) {
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
    public boolean stillValid(Player player) {
        if (player.level().isClientSide())
            return true;
        return blockEntity != null || !backpackStack.isEmpty();
    }
}
