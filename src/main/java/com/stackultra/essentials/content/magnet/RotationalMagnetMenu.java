package com.stackultra.essentials.content.magnet;

import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
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
    private final int lockedInventorySlot;
    private final RotationalMagnetBlockEntity blockEntity;

    private boolean enabled;
    private boolean blacklist;
    private int charge;
    private int maxCharge;

    public static void writeItemOpenData(RegistryFriendlyByteBuf buf,
                                         Player player,
                                         ItemStack stack,
                                         RotationalMagnetItem item) {
        buf.writeBoolean(false);
        buf.writeVarInt(MenuValidationHelper.findPlayerInventorySlot(player, stack));
        ItemContainerContents.STREAM_CODEC.encode(buf,
                stack.getOrDefault(AllDataComponents.MAGNET_FILTERS, ItemContainerContents.EMPTY));
        buf.writeBoolean(stack.getOrDefault(AllDataComponents.ENABLED, true));
        buf.writeBoolean(stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
        buf.writeVarInt(item.getCharge(stack));
        buf.writeVarInt(item.getMaxCharge(stack));
    }

    public RotationalMagnetMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();

        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            Level level = inv.player != null ? inv.player.level() : null;
            RotationalMagnetBlockEntity found = null;
            if (level != null) {
                BlockEntity raw = level.getBlockEntity(pos);
                if (raw instanceof RotationalMagnetBlockEntity be)
                    found = be;
            }
            this.blockEntity = found;
            this.magnetStack = ItemStack.EMPTY;
            this.lockedInventorySlot = MenuValidationHelper.NO_LOCKED_SLOT;
            if (found != null) {
                found.readClient(tag, extraData.registryAccess());
                this.filterInventory = found.getFilterInventory();
                this.enabled = found.isEnabled();
                this.blacklist = found.isBlacklist();
                this.charge = found.getCharge();
                this.maxCharge = found.getMaxCharge();
            } else {
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                this.enabled = true;
                this.blacklist = false;
                this.charge = 0;
                this.maxCharge = 1;
            }
        } else {
            this.blockEntity = null;
            this.magnetStack = ItemStack.EMPTY;
            this.lockedInventorySlot = extraData.readVarInt();
            this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
            NonNullList<ItemStack> filters = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
            ItemContainerContents.STREAM_CODEC.decode(extraData).copyInto(filters);
            for (int i = 0; i < FILTER_SLOTS; i++)
                filterInventory.setStackInSlot(i, filters.get(i));
            this.enabled = extraData.readBoolean();
            this.blacklist = extraData.readBoolean();
            this.charge = extraData.readVarInt();
            this.maxCharge = Math.max(1, extraData.readVarInt());
        }

        addSlots();
        addStateDataSlots();
    }

    private RotationalMagnetMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        this.magnetStack = stack;
        this.lockedInventorySlot = MenuValidationHelper.findPlayerInventorySlot(inv.player, stack);
        this.blockEntity = null;
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);

        NonNullList<ItemStack> filters = RotationalMagnetItem.getFilterSlots(stack);
        for (int i = 0; i < FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, filters.get(i).copy());

        RotationalMagnetItem item = (RotationalMagnetItem) stack.getItem();
        this.enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        this.blacklist = stack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        this.charge = item.getCharge(stack);
        this.maxCharge = item.getMaxCharge(stack);

        addSlots();
        addStateDataSlots();
    }

    private RotationalMagnetMenu(int windowId, Inventory inv, RotationalMagnetBlockEntity be) {
        super(AllMenus.ROTATIONAL_MAGNET.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.magnetStack = ItemStack.EMPTY;
        this.lockedInventorySlot = MenuValidationHelper.NO_LOCKED_SLOT;
        this.filterInventory = be.getFilterInventory();

        this.enabled = be.isEnabled();
        this.blacklist = be.isBlacklist();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();

        addSlots();
        addStateDataSlots();
    }

    public static RotationalMagnetMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new RotationalMagnetMenu(windowId, inv, stack);
    }

    public static RotationalMagnetMenu create(int windowId, Inventory inv, RotationalMagnetBlockEntity be) {
        return new RotationalMagnetMenu(windowId, inv, be);
    }

    private void addStateDataSlots() {
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.isEnabled() ? 1 : 0;
                if (!magnetStack.isEmpty())
                    return magnetStack.getOrDefault(AllDataComponents.ENABLED, true) ? 1 : 0;
                return enabled ? 1 : 0;
            }

            @Override
            public void set(int value) {
                enabled = value != 0;
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.isBlacklist() ? 1 : 0;
                if (!magnetStack.isEmpty())
                    return magnetStack.getOrDefault(ExtraDataComponents.BLACKLIST, false) ? 1 : 0;
                return blacklist ? 1 : 0;
            }

            @Override
            public void set(int value) {
                blacklist = value != 0;
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.getCharge();
                if (!magnetStack.isEmpty() && magnetStack.getItem() instanceof RotationalMagnetItem item)
                    return item.getCharge(magnetStack);
                return charge;
            }

            @Override
            public void set(int value) {
                charge = Math.max(0, value);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.getMaxCharge();
                if (!magnetStack.isEmpty() && magnetStack.getItem() instanceof RotationalMagnetItem item)
                    return item.getMaxCharge(magnetStack);
                return maxCharge;
            }

            @Override
            public void set(int value) {
                maxCharge = Math.max(1, value);
            }
        });
    }

    private void addSlots() {
        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new SlotItemHandler(filterInventory, i, 62 + i * 18, 57) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 110 + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return !isLockedOpenSlot(this);
                    }
                });
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 168) {
                @Override
                public boolean mayPickup(Player player) {
                    return !isLockedOpenSlot(this);
                }
            });
        }
    }

    private boolean isLockedOpenSlot(Slot slot) {
        return MenuValidationHelper.isLockedSlot(slot, playerInventory, magnetStack, lockedInventorySlot);
    }

    public boolean isBlockEntityContext() {
        return blockEntity != null;
    }

    public RotationalMagnetBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public ItemStack getMagnetStack() {
        return magnetStack;
    }

    public boolean isEnabled() {
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.isEnabled() : enabled;
    }

    public void setEnabledClient(boolean value) {
        this.enabled = value;
    }

    public boolean isBlacklist() {
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.isBlacklist() : blacklist;
    }

    public void setBlacklistClient(boolean value) {
        this.blacklist = value;
    }

    public int getCharge() {
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.getCharge() : charge;
    }

    public int getMaxCharge() {
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.getMaxCharge() : maxCharge;
    }

    private void persistFilters(Player player) {
        if (player.level().isClientSide())
            return;

        if (blockEntity != null) {
            if (!blockEntity.isRemoved()) {
                blockEntity.setChanged();
                blockEntity.sendData();
            }
            broadcastChanges();
            return;
        }

        if (!magnetStack.isEmpty() && MenuValidationHelper.isPlayerCarryingStack(player, magnetStack)) {
            List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
            for (int i = 0; i < FILTER_SLOTS; i++)
                filters.add(filterInventory.getStackInSlot(i).copy());
            RotationalMagnetItem.setFilters(magnetStack, filters);
            MagnetEquipment.syncMagnet(player, magnetStack);
        }
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!player.level().isClientSide() && !stillValid(player))
            return;
        if (MenuValidationHelper.isLockedOpenStackClick(
                this, magnetStack, lockedInventorySlot, slotId, dragType, clickType, player)) {
            return;
        }
        if (slotId >= 0 && slotId < FILTER_SLOTS) {
            if (clickType == ClickType.QUICK_CRAFT) {
                super.clicked(slotId, dragType, clickType, player);
                return;
            }
            if (clickType == ClickType.THROW
                    || clickType == ClickType.CLONE
                    || clickType == ClickType.SWAP
                    || clickType == ClickType.PICKUP_ALL) {
                return;
            }
            ItemStack held = getCarried();
            ItemStack insert = (clickType == ClickType.QUICK_MOVE || held.isEmpty())
                    ? ItemStack.EMPTY
                    : held.copyWithCount(1);
            filterInventory.setStackInSlot(slotId, insert);
            getSlot(slotId).setChanged();
            persistFilters(player);
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slotIn) {
        return slotIn.index >= FILTER_SLOTS && !isLockedOpenSlot(slotIn);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return slotIn.index >= FILTER_SLOTS && !isLockedOpenSlot(slotIn);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (blockEntity == null) {
            persistFilters(player);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide())
            return true;
        return blockEntity != null
                ? MenuValidationHelper.isBlockEntityValid(player, blockEntity)
                : MenuValidationHelper.isPlayerCarryingStack(player, magnetStack);
    }
}
