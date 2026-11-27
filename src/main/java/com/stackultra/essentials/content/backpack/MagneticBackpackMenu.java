package com.stackultra.essentials.content.backpack;

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

import java.util.ArrayList;
import java.util.List;

public class MagneticBackpackMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int STORAGE_SLOTS = BackpackInventory.SLOTS;
    public static final int FILTER_SLOTS = MagneticBackpackItem.FILTER_SLOTS;
    public static final int TOOLBOX_SLOTS = PortableToolboxInventory.COMPARTMENTS;
    public static final int TOOLBOX_SLOT_START = STORAGE_SLOTS + FILTER_SLOTS;
    public static final int PLAYER_SLOT_START = TOOLBOX_SLOT_START + TOOLBOX_SLOTS;

    // Left 3-slot filter panel aligned flush with the top-left corner of the main window
    private static final int FILTER_X = -20;
    private static final int FILTER_Y = 5;
    private static final int STORAGE_X = 8;
    private static final int STORAGE_Y = 51;
    // 8x1 horizontal toolbox bar positioned directly beneath the 27-slot storage grid and above player inventory
    public static final int TOOLBOX_X = 17;
    public static final int TOOLBOX_Y = 128;
    private static final int PLAYER_Y = 164;
    private static final int HOTBAR_Y = 222;

    private final Inventory playerInventory;
    private final IItemHandler storageInventory;
    private final ItemStackHandler filterInventory;
    private final PortableToolboxInventory toolboxInventory;
    private final IItemHandler toolboxSlotBacking;

    private final MagneticBackpackBlockEntity blockEntity;
    private final ItemStack backpackStack;

    private boolean enabled;
    private boolean blacklist;
    private boolean charging;
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
        this.toolboxSlotBacking = this.toolboxInventory.asMenuDisplayHandler();
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, be.getFilterInventory().getStackInSlot(i).copy());
        this.enabled = be.isEnabled();
        this.blacklist = be.isBlacklist();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();
        this.charging = be.getSpeed() != 0 && this.charge < this.maxCharge;
        this.sortMode = be.getSortMode();
        this.restockEnabled = be.isRestockEnabled();
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
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
        this.toolboxSlotBacking = this.toolboxInventory.asMenuDisplayHandler();
        this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
        NonNullList<ItemStack> filters = MagneticBackpackItem.getFilterSlots(stack);
        for (int i = 0; i < FILTER_SLOTS; i++)
            this.filterInventory.setStackInSlot(i, filters.get(i).copy());
        MagneticBackpackItem item = (MagneticBackpackItem) stack.getItem();
        this.enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        this.blacklist = stack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        this.charge = item.getCharge(stack);
        this.maxCharge = item.getMaxCharge(stack);
        this.charging = stack.getOrDefault(AllDataComponents.CHARGING, false);
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        this.restockEnabled = stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addStateDataSlots();
    }

    public MagneticBackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();
        ItemStackHandler displayHandler = new ItemStackHandler(TOOLBOX_SLOTS);

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
                this.charging = found.getSpeed() != 0 && this.charge < this.maxCharge;
                this.sortMode = found.getSortMode();
                this.restockEnabled = found.isRestockEnabled();
                IItemHandler menuView = found.getToolboxInventory().asMenuDisplayHandler();
                for (int c = 0; c < TOOLBOX_SLOTS; c++) {
                    this.syncedToolboxCounts[c] = found.getToolboxInventory().getTotalCount(c);
                    displayHandler.setStackInSlot(c, menuView.getStackInSlot(c));
                }
            } else {
                this.storageInventory = new BackpackInventory();
                this.toolboxInventory = PortableToolboxInventory.empty();
                this.filterInventory = new ItemStackHandler(FILTER_SLOTS);
                this.enabled = true;
                this.blacklist = false;
                this.charge = 0;
                this.maxCharge = 1;
                this.charging = false;
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
            this.charging = false;
            for (int c = 0; c < TOOLBOX_SLOTS; c++)
                this.syncedToolboxCounts[c] = Math.max(0, extraData.readVarInt());
            this.sortMode = SortMode.NAME;
            this.restockEnabled = true;
        }
        // Client-side toolbox slots always bind to displayHandler so vanilla slot sync never wipes BE sub-stacks
        this.toolboxSlotBacking = displayHandler;
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

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.getCharge();
                if (!backpackStack.isEmpty() && backpackStack.getItem() instanceof MagneticBackpackItem item)
                    return item.getCharge(backpackStack);
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
                if (!backpackStack.isEmpty() && backpackStack.getItem() instanceof MagneticBackpackItem item)
                    return item.getMaxCharge(backpackStack);
                return maxCharge;
            }

            @Override
            public void set(int value) {
                maxCharge = Math.max(1, value);
            }
        });

        addDataSlot(new DataSlot() {
            @Override
            public int get() {
                if (blockEntity != null)
                    return blockEntity.getSpeed() != 0 && blockEntity.getCharge() < blockEntity.getMaxCharge() ? 1 : 0;
                if (!backpackStack.isEmpty())
                    return backpackStack.getOrDefault(AllDataComponents.CHARGING, false) ? 1 : 0;
                return charging ? 1 : 0;
            }

            @Override
            public void set(int value) {
                charging = value != 0;
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

        // 27..29: 3 magnet filter slots (flush-top left panel)
        for (int i = 0; i < FILTER_SLOTS; i++) {
            addSlot(new SlotItemHandler(filterInventory, i, FILTER_X, FILTER_Y + i * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        // 30..37: 8 toolbox compartment slots (8x1 horizontal bottom bar)
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            addSlot(new SlotItemHandler(toolboxSlotBacking, c, TOOLBOX_X + c * 18, TOOLBOX_Y));
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

    public boolean isCharging() {
        if (blockEntity != null && !playerInventory.player.level().isClientSide())
            return blockEntity.getSpeed() != 0 && blockEntity.getCharge() < blockEntity.getMaxCharge();
        return charging;
    }

    @Override
    public SortMode getSortMode() {
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.getSortMode() : sortMode;
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
        return blockEntity != null && !playerInventory.player.level().isClientSide()
                ? blockEntity.isRestockEnabled() : restockEnabled;
    }

    @Override
    public void setRestockEnabled(boolean enabled, Player player) {
        this.restockEnabled = enabled;
        if (blockEntity != null) {
            blockEntity.setRestockEnabled(enabled);
        } else if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.TOOLBOX_RESTOCK, enabled);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
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
        // Strictly scoped to 27 main storage slots [0..27) + 8 toolbox slots, never touching 3 filter slots
        if (BackpackInventoryOps.transferMatchingToStorage(
                this, toolboxInventory, 0, STORAGE_SLOTS, PLAYER_SLOT_START, this.slots.size(),
                player, backpackStack.isEmpty() ? null : backpackStack)) {
            syncBacking(player);
        }
    }

    @Override
    public void transferMatchingToInventory(Player player) {
        if (BackpackInventoryOps.transferMatchingToInventory(
                this, toolboxInventory, 0, STORAGE_SLOTS, PLAYER_SLOT_START, this.slots.size(), player)) {
            syncBacking(player);
        }
    }

    @Override
    public void sortStorage(Player player) {
        if (BackpackInventoryOps.sortStorage(this, toolboxInventory, 0, STORAGE_SLOTS, getSortMode())) {
            syncBacking(player);
        }
    }

    private void syncBacking(Player player) {
        if (blockEntity != null) {
            blockEntity.setChanged();
            blockEntity.sendData();
        } else if (!backpackStack.isEmpty()) {
            toolboxInventory.save(backpackStack);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.onToolboxModified(serverPlayer, backpackStack, toolboxInventory);
            }
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
            // Allow drag-split state machine to finish cleanly without touching toolbox slots (canDragTo is false)
            if (clickType == ClickType.QUICK_CRAFT) {
                super.clicked(slotId, dragType, clickType, player);
                return;
            }
            if (!player.level().isClientSide()) {
                int compartment = slotId - TOOLBOX_SLOT_START;
                if (BackpackInventoryOps.handleToolboxSlotClick(
                        this, toolboxInventory, compartment, dragType, clickType, player,
                        PLAYER_SLOT_START, this.slots.size(),
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
        if (!player.level().isClientSide()) {
            if (blockEntity != null) {
                blockEntity.setChanged();
                blockEntity.sendData();
            } else if (!backpackStack.isEmpty()) {
                toolboxInventory.save(backpackStack);
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
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
        return blockEntity != null || !backpackStack.isEmpty();
    }
}
