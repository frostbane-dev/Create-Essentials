package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.network.SyncToolboxFiltersPayload;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class MagneticBackpackMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int STORAGE_SLOTS = BackpackInventory.SLOTS;
    public static final int FILTER_SLOTS = MagneticBackpackItem.FILTER_SLOTS;
    public static final int TOOLBOX_SLOTS = PortableToolboxInventory.COMPARTMENTS;
    public static final int TOOLBOX_SLOT_START = STORAGE_SLOTS + FILTER_SLOTS;
    public static final int PLAYER_SLOT_START = TOOLBOX_SLOT_START + TOOLBOX_SLOTS;

    private static final int FILTER_X = -20;
    private static final int FILTER_Y = 21;
    private static final int STORAGE_X = 8;
    private static final int STORAGE_Y = 42;
    public static final int TOOLBOX_X = 180;
    public static final int TOOLBOX_Y = 37;
    private static final int PLAYER_Y = 109;
    private static final int HOTBAR_Y = 167;

    private final Inventory playerInventory;
    private final IItemHandler storageInventory;
    private final ItemStackHandler filterInventory;
    private final PortableToolboxInventory toolboxInventory;

    private final MagneticBackpackBlockEntity blockEntity;
    private final ItemStack backpackStack;

    private boolean enabled;
    private boolean blacklist;
    private int charge;
    private int maxCharge;
    private SortMode sortMode = SortMode.NAME;
    private boolean restockEnabled = true;
    private final int[] syncedToolboxCounts = new int[TOOLBOX_SLOTS];

    private MagneticBackpackMenu(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.backpackStack = ItemStack.EMPTY;
        this.storageInventory = be.getItemHandler();
        this.toolboxInventory = be.getToolboxInventory();
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, be.getFilterInventory().getStackInSlot(i).copy());
        this.enabled = be.isEnabled();
        this.blacklist = be.isBlacklist();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();
        this.sortMode = be.getSortMode();
        this.restockEnabled = be.isRestockEnabled();
        addSlots();
        addStateDataSlots();
    }

    private MagneticBackpackMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = null;
        this.backpackStack = stack;
        this.storageInventory = BackpackInventory.forStack(stack,
                () -> MagneticBackpackEquipment.syncMagneticBackpack(inv.player, stack));
        this.toolboxInventory = PortableToolboxInventory.forStack(stack,
                () -> MagneticBackpackEquipment.syncMagneticBackpack(inv.player, stack));
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        NonNullList<ItemStack> filters = MagneticBackpackItem.getFilterSlots(stack);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, filters.get(i).copy());
        MagneticBackpackItem item = (MagneticBackpackItem) stack.getItem();
        this.enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        this.blacklist = stack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        this.charge = item.getCharge(stack);
        this.maxCharge = item.getMaxCharge(stack);
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        this.restockEnabled = stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        addSlots();
        addStateDataSlots();
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
                this.toolboxInventory = found.getToolboxInventory();
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                for (int i = 0; i < FILTER_SLOTS; i++)
                    this.filterInventory.setStackInSlot(i, found.getFilterInventory().getStackInSlot(i).copy());
                this.enabled = found.isEnabled();
                this.blacklist = found.isBlacklist();
                this.charge = found.getCharge();
                this.maxCharge = found.getMaxCharge();
                this.sortMode = found.getSortMode();
                this.restockEnabled = found.isRestockEnabled();
            } else {
                this.storageInventory = new BackpackInventory();
                this.toolboxInventory = PortableToolboxInventory.empty();
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                this.enabled = true;
                this.blacklist = false;
                this.charge = 0;
                this.maxCharge = 1;
                this.sortMode = SortMode.NAME;
                this.restockEnabled = true;
            }
        } else {
            this.blockEntity = null;
            this.backpackStack = ItemStack.EMPTY;
            this.storageInventory = new BackpackInventory();
            this.toolboxInventory = PortableToolboxInventory.empty();
            this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
            NonNullList<ItemStack> filters = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
            ItemContainerContents.STREAM_CODEC.decode(extraData).copyInto(filters);
            for (int i = 0; i < FILTER_SLOTS; i++)
                this.filterInventory.setStackInSlot(i, filters.get(i));
            this.enabled = extraData.readBoolean();
            this.blacklist = false;
            this.charge = extraData.readVarInt();
            this.maxCharge = extraData.readVarInt();
            this.toolboxInventory.applyClientFilters(ItemContainerContents.STREAM_CODEC.decode(extraData));
            this.sortMode = SortMode.NAME;
            this.restockEnabled = true;
        }
        addSlots();
        addStateDataSlots();
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        return new MagneticBackpackMenu(windowId, inv, be);
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new MagneticBackpackMenu(windowId, inv, stack);
    }

    private void addStateDataSlots() {
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

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.isEnabled() ? 1 : 0;
                if (!backpackStack.isEmpty())
                    return backpackStack.getOrDefault(AllDataComponents.ENABLED, true) ? 1 : 0;
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
                if (!backpackStack.isEmpty())
                    return backpackStack.getOrDefault(ExtraDataComponents.BLACKLIST, false) ? 1 : 0;
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
                    return blockEntity.isRestockEnabled() ? 1 : 0;
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

    private void addSlots() {
        // 0..26: 27 main storage slots
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(storageInventory, row * 9 + col,
                        STORAGE_X + col * 18, STORAGE_Y + row * 18));
            }
        }

        // 27..29: 3 magnet filter slots (left panel)
        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new SlotItemHandler(filterInventory, i, FILTER_X, FILTER_Y + i * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        // 30..37: 8 toolbox compartment slots (right panel)
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            int col = c % 2;
            int row = c / 2;
            addSlot(new SlotItemHandler(toolboxInventory, c, TOOLBOX_X + col * 18, TOOLBOX_Y + row * 18));
        }

        // 38..73: 36 player inventory + hotbar slots
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

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public boolean isBackingStack(ItemStack stack) {
        return blockEntity == null && !backpackStack.isEmpty() && backpackStack == stack;
    }

    public BackpackInventory getBackpackInventory() {
        return storageInventory instanceof BackpackInventory inv ? inv : null;
    }

    @Override
    public PortableToolboxInventory getToolboxInventory() {
        return toolboxInventory;
    }

    public boolean isEnabled() {
        return blockEntity != null ? blockEntity.isEnabled() : enabled;
    }

    public void setEnabledClient(boolean value) {
        this.enabled = value;
    }

    public boolean isBlacklist() {
        return blockEntity != null ? blockEntity.isBlacklist() : blacklist;
    }

    public void setBlacklistClient(boolean value) {
        this.blacklist = value;
    }

    public int getCharge() {
        return blockEntity != null ? blockEntity.getCharge() : charge;
    }

    public int getMaxCharge() {
        return blockEntity != null ? blockEntity.getMaxCharge() : maxCharge;
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
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public boolean isRestockEnabled() {
        return blockEntity != null ? blockEntity.isRestockEnabled() : restockEnabled;
    }

    @Override
    public void setRestockEnabled(boolean enabled, Player player) {
        this.restockEnabled = enabled;
        if (blockEntity != null) {
            blockEntity.setRestockEnabled(enabled);
        } else if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.TOOLBOX_RESTOCK, enabled);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public int getToolboxCount(int compartment) {
        if (compartment < 0 || compartment >= TOOLBOX_SLOTS)
            return 0;
        if (blockEntity != null)
            return blockEntity.getToolboxInventory().getTotalCount(compartment);
        return syncedToolboxCounts[compartment];
    }

    @Override
    public ItemStack getToolboxFilter(int compartment) {
        return toolboxInventory.getFilter(compartment);
    }

    @Override
    public void clearToolboxSlot(int compartment, Player player) {
        if (toolboxInventory.clearReservation(compartment)) {
            syncToolbox(player);
        }
    }

    @Override
    public void applyClientToolboxFilters(ItemContainerContents filters) {
        toolboxInventory.applyClientFilters(filters);
    }

    public void syncToolboxFiltersToClient(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new SyncToolboxFiltersPayload(this.containerId, toolboxInventory.getFiltersComponent()));
        }
    }

    private void syncToolbox(Player player) {
        syncBacking(player);
        syncToolboxFiltersToClient(player);
    }

    @Override
    public void transferMatchingToStorage(Player player) {
        // Strictly scoped to 27 main storage slots [0..27) and player slots [38..74)
        if (BackpackInventoryOps.transferMatchingToStorage(
                this, 0, STORAGE_SLOTS, PLAYER_SLOT_START, this.slots.size(),
                player, backpackStack.isEmpty() ? null : backpackStack)) {
            syncBacking(player);
        }
    }

    @Override
    public void transferMatchingToInventory(Player player) {
        if (BackpackInventoryOps.transferMatchingToInventory(
                this, 0, STORAGE_SLOTS, PLAYER_SLOT_START, this.slots.size(), player)) {
            syncBacking(player);
        }
    }

    @Override
    public void sortStorage(Player player) {
        if (BackpackInventoryOps.sortStorage(this, 0, STORAGE_SLOTS, getSortMode())) {
            syncBacking(player);
        }
    }

    private void syncBacking(Player player) {
        if (blockEntity != null) {
            blockEntity.setChanged();
            blockEntity.sendData();
        } else if (!backpackStack.isEmpty()) {
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    private boolean isFilterSlot(int slotId) {
        return slotId >= STORAGE_SLOTS && slotId < TOOLBOX_SLOT_START;
    }

    public boolean isToolboxSlot(int slotId) {
        return slotId >= TOOLBOX_SLOT_START && slotId < PLAYER_SLOT_START;
    }

    private void persistFilters(Player player) {
        if (player.level().isClientSide())
            return;
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            filters.add(filterInventory.getStackInSlot(i).copy());
        if (blockEntity != null) {
            blockEntity.setFilters(filters);
        } else if (!backpackStack.isEmpty()) {
            MagneticBackpackItem.setFilters(backpackStack, filters);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (isFilterSlot(slotId)) {
            if (clickType == ClickType.THROW || clickType == ClickType.CLONE)
                return;
            ItemStack held = getCarried();
            ItemStack insert = (clickType == ClickType.QUICK_MOVE || held.isEmpty())
                    ? ItemStack.EMPTY
                    : held.copyWithCount(1);
            int filterIndex = slotId - STORAGE_SLOTS;
            filterInventory.setStackInSlot(filterIndex, insert);
            getSlot(slotId).setChanged();
            persistFilters(player);
            return;
        }
        if (isToolboxSlot(slotId)) {
            if (!player.level().isClientSide()) {
                int compartment = slotId - TOOLBOX_SLOT_START;
                if (BackpackInventoryOps.handleToolboxSlotClick(
                        this, toolboxInventory, compartment, dragType, clickType, player,
                        PLAYER_SLOT_START, this.slots.size())) {
                    syncToolbox(player);
                }
            }
            return;
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public boolean canDragTo(Slot slotIn) {
        return !isFilterSlot(slotIn.index) && !isToolboxSlot(slotIn.index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return !isFilterSlot(slotIn.index) && !isToolboxSlot(slotIn.index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isFilterSlot(index) || isToolboxSlot(index))
            return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < STORAGE_SLOTS) {
                if (!moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true))
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
        persistFilters(player);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide())
            return true;
        return blockEntity != null || !backpackStack.isEmpty();
    }
}
