package com.stackultra.essentials.content.magnet;

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
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import java.util.ArrayList;
import java.util.List;

public class RotationalMagnetMenu extends AbstractContainerMenu {

    public static final int FILTER_SLOTS = RotationalMagnetItem.FILTER_SLOTS;
    public static final int PLAYER_SLOT_START = FILTER_SLOTS;

    private final Inventory playerInventory;
    private final ItemStackHandler filterInventory;
    private final ItemStack magnetStack;
    private final RotationalMagnetBlockEntity blockEntity;

    private boolean enabled;
    private int charge;
    private int maxCharge;

    public RotationalMagnetMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();

        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            ClientLevel level = Minecraft.getInstance().level;
            RotationalMagnetBlockEntity found = null;
            if (level != null) {
                BlockEntity raw = level.getBlockEntity(pos);
                if (raw instanceof RotationalMagnetBlockEntity be)
                    found = be;
            }
            this.blockEntity = found;
            this.magnetStack = ItemStack.EMPTY;
            this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
            if (found != null) {
                found.readClient(tag, extraData.registryAccess());
                List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
                for (int i = 0; i < FILTER_SLOTS; i++)
                    filters.add(found.getFilterInventory().getStackInSlot(i).copy());
                for (int i = 0; i < FILTER_SLOTS; i++)
                    filterInventory.setStackInSlot(i, filters.get(i));
                this.enabled = found.isEnabled();
                this.charge = found.getCharge();
                this.maxCharge = found.getMaxCharge();
            } else {
                this.enabled = true;
                this.charge = 0;
                this.maxCharge = 1;
            }
        } else {
            this.blockEntity = null;
            this.magnetStack = ItemStack.EMPTY;
            this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
            NonNullList<ItemStack> filters = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
            ItemContainerContents.STREAM_CODEC.decode(extraData).copyInto(filters);
            for (int i = 0; i < FILTER_SLOTS; i++)
                filterInventory.setStackInSlot(i, filters.get(i));
            this.enabled = extraData.readBoolean();
            this.charge = extraData.readVarInt();
            this.maxCharge = extraData.readVarInt();
        }

        addSlots();
    }

    private RotationalMagnetMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        this.magnetStack = stack;
        this.blockEntity = null;
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);

        List<ItemStack> filters = RotationalMagnetItem.getFilters(stack);
        for (int i = 0; i < FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i) : ItemStack.EMPTY);

        RotationalMagnetItem item = (RotationalMagnetItem) stack.getItem();
        this.enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        this.charge = item.getCharge(stack);
        this.maxCharge = item.getMaxCharge(stack);

        addSlots();
    }

    private RotationalMagnetMenu(int windowId, Inventory inv, RotationalMagnetBlockEntity be) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.magnetStack = ItemStack.EMPTY;
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);

        for (int i = 0; i < FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, be.getFilterInventory().getStackInSlot(i).copy());

        this.enabled = be.isEnabled();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();

        addSlots();
    }

    public static RotationalMagnetMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new RotationalMagnetMenu(windowId, inv, stack);
    }

    public static RotationalMagnetMenu create(int windowId, Inventory inv, RotationalMagnetBlockEntity be) {
        return new RotationalMagnetMenu(windowId, inv, be);
    }

    private void addSlots() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new SlotItemHandler(filterInventory, i, 8 + i * 18, 20) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 76 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 134));
        }
    }

    public boolean isBlockEntityContext() {
        return blockEntity != null;
    }

    public RotationalMagnetBlockEntity getBlockEntity() {
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

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER_SLOTS && clickType != ClickType.THROW && clickType != ClickType.CLONE) {
            ItemStack held = getCarried();
            ItemStack insert = held.isEmpty() ? ItemStack.EMPTY : held.copyWithCount(1);
            filterInventory.setStackInSlot(slotId, insert);
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
        return ItemStack.EMPTY;
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
        } else if (!magnetStack.isEmpty()) {
            RotationalMagnetItem.setFilters(magnetStack, filters);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}
