package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class BackpackMenu extends AbstractContainerMenu {

    private static final int BACKPACK_SLOTS = BackpackInventory.SLOTS;

    private final Inventory playerInventory;
    private final IItemHandler backpackInventory;

    public BackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(windowId, inv, createClientInventory(extraData));
    }

    private BackpackMenu(int windowId, Inventory inv, IItemHandler backpackInventory) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.backpackInventory = backpackInventory;
        addBackpackSlots();
        addPlayerSlots();
    }

    public static BackpackMenu create(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        return new BackpackMenu(windowId, inv, be.getItemHandler());
    }

    public static BackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new BackpackMenu(windowId, inv, BackpackInventory.forStack(stack));
    }

    private static IItemHandler createClientInventory(RegistryFriendlyByteBuf extraData) {
        boolean fromBlockEntity = extraData.readBoolean();
        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            ClientLevel level = Minecraft.getInstance().level;
            if (level != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof MechanicalBackpackBlockEntity backpack) {
                    backpack.readClient(tag, extraData.registryAccess());
                    return backpack.getItemHandler();
                }
            }
        }
        return new BackpackInventory();
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
        return true;
    }
}
