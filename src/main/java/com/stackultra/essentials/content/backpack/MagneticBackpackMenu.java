package com.stackultra.essentials.content.backpack;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

public class MagneticBackpackMenu extends AbstractContainerMenu {

    public static final int STORAGE_SLOTS = BackpackInventory.SLOTS;
    public static final int FILTER_SLOTS = MagneticBackpackItem.FILTER_SLOTS;
    public static final int PLAYER_SLOT_START = STORAGE_SLOTS + FILTER_SLOTS;

    private static final int FILTER_X = 8;
    private static final int FILTER_Y = 20;
    private static final int STORAGE_X = 32;
    private static final int STORAGE_Y = 20;
    private static final int PLAYER_Y = 96;
    private static final int HOTBAR_Y = 154;

    private final Inventory playerInventory;
    private final IItemHandler storageInventory;
    private final ItemStackHandler filterInventory;

    private final MagneticBackpackBlockEntity blockEntity;
    private final ItemStack backpackStack;

    private boolean enabled;
    private int charge;
    private int maxCharge;

    private MagneticBackpackMenu(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.backpackStack = ItemStack.EMPTY;
        this.storageInventory = be.getItemHandler();
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, be.getFilterInventory().getStackInSlot(i).copy());
        this.enabled = be.isEnabled();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();
        addSlots();
    }

    private MagneticBackpackMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = null;
        this.backpackStack = stack;
        this.storageInventory = BackpackInventory.forStack(stack);
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        List<ItemStack> filters = MagneticBackpackItem.getFilters(stack);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i) : ItemStack.EMPTY);
        MagneticBackpackItem item = (MagneticBackpackItem) stack.getItem();
        this.enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        this.charge = item.getCharge(stack);
        this.maxCharge = item.getMaxCharge(stack);
        addSlots();
    }

    public MagneticBackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();

        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            ClientLevel level = Minecraft.getInstance().level;
            MagneticBackpackBlockEntity found = null;
            if (level != null) {
                BlockEntity raw = level.getBlockEntity(pos);
                if (raw instanceof MagneticBackpackBlockEntity be)
                    found = be;
            }
            this.blockEntity = found;
            this.backpackStack = ItemStack.EMPTY;
            if (found != null) {
                found.readClient(tag, extraData.registryAccess());
                this.storageInventory = found.getItemHandler();
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                for (int i = 0; i < FILTER_SLOTS; i++)
                    this.filterInventory.setStackInSlot(i, found.getFilterInventory().getStackInSlot(i).copy());
                this.enabled = found.isEnabled();
                this.charge = found.getCharge();
                this.maxCharge = found.getMaxCharge();
            } else {
                this.storageInventory = new BackpackInventory();
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                this.enabled = true;
                this.charge = 0;
                this.maxCharge = 1;
            }
        } else {
            this.blockEntity = null;
            this.backpackStack = ItemStack.EMPTY;
            this.storageInventory = new BackpackInventory();
            this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
            NonNullList<ItemStack> filters = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
            ItemContainerContents.STREAM_CODEC.decode(extraData).copyInto(filters);
            for (int i = 0; i < FILTER_SLOTS; i++)
                this.filterInventory.setStackInSlot(i, filters.get(i));
            this.enabled = extraData.readBoolean();
            this.charge = extraData.readVarInt();
            this.maxCharge = extraData.readVarInt();
        }
        addSlots();
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        return new MagneticBackpackMenu(windowId, inv, be);
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new MagneticBackpackMenu(windowId, inv, stack);
    }

    private void addSlots() {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(storageInventory, row * 9 + col,
                        STORAGE_X + col * 18, STORAGE_Y + row * 18));
            }
        }

        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new SlotItemHandler(filterInventory, i, FILTER_X, FILTER_Y + i * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.getItem() instanceof FilterItem;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        STORAGE_X + col * 18, PLAYER_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, STORAGE_X + col * 18, HOTBAR_Y));
        }
    }

    public boolean isBlockEntityContext() {
        return blockEntity != null;
    }

    public MagneticBackpackBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public boolean isEnabled() {
        return blockEntity != null ? blockEntity.isEnabled() : enabled;
    }

    public void setEnabledClient(boolean value) {
        this.enabled = value;
    }

    public int getCharge() {
        return blockEntity != null ? blockEntity.getCharge() : charge;
    }

    public int getMaxCharge() {
        return blockEntity != null ? blockEntity.getMaxCharge() : maxCharge;
    }

    private boolean isFilterSlot(int slotId) {
        return slotId >= STORAGE_SLOTS && slotId < PLAYER_SLOT_START;
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (isFilterSlot(slotId) && clickType != ClickType.THROW && clickType != ClickType.CLONE) {
            ItemStack held = getCarried();
            if (!held.isEmpty() && !(held.getItem() instanceof FilterItem))
                return;
            ItemStack insert = held.isEmpty() ? ItemStack.EMPTY : held.copy();
            if (!insert.isEmpty())
                insert.setCount(1);
            int filterIndex = slotId - STORAGE_SLOTS;
            filterInventory.setStackInSlot(filterIndex, insert);
            getSlot(slotId).setChanged();
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slotIn) {
        return slotIn.container != filterInventory;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return slotIn.container != filterInventory;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < STORAGE_SLOTS) {
                if (!moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true))
                    return ItemStack.EMPTY;
            } else if (isFilterSlot(index)) {
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
        if (player.level().isClientSide())
            return;
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            filters.add(filterInventory.getStackInSlot(i).copy());
        if (blockEntity != null) {
            blockEntity.setFilters(filters);
        } else if (!backpackStack.isEmpty()) {
            MagneticBackpackItem.setFilters(backpackStack, filters);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
