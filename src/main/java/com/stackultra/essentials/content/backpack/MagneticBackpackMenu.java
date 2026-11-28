package com.stackultra.essentials.content.backpack;

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
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class MagneticBackpackMenu extends AbstractBackpackMenu {

    public static final int FILTER_SLOTS = MagneticBackpackItem.FILTER_SLOTS;
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

    @Nullable
    private final MagneticBackpackBlockEntity blockEntity;
    private final ItemStackHandler filterInventory;

    private boolean enabled;
    private boolean blacklist;
    private boolean charging;
    private int charge;
    private int maxCharge;

    private record ClientInitData(
            @Nullable MagneticBackpackBlockEntity blockEntity,
            IItemHandler storageInventory,
            PortableToolboxInventory toolboxInventory,
            IItemHandler displayHandler,
            ItemStackHandler filterInventory,
            int lockedInventorySlot,
            boolean enabled,
            boolean blacklist,
            boolean charging,
            int charge,
            int maxCharge,
            SortMode sortMode,
            boolean restockEnabled,
            int[] initialCounts,
            @Nullable DyeColor backpackColor
    ) {
    }

    public static void writeItemOpenData(RegistryFriendlyByteBuf buf,
                                         Player player,
                                         ItemStack stack,
                                         MagneticBackpackItem item,
                                         PortableToolboxInventory toolbox) {
        buf.writeBoolean(false);
        buf.writeVarInt(MenuValidationHelper.findPlayerInventorySlot(player, stack));
        ItemContainerContents.STREAM_CODEC.encode(buf,
                stack.getOrDefault(AllDataComponents.BACKPACK_FILTERS, ItemContainerContents.EMPTY));
        buf.writeBoolean(stack.getOrDefault(AllDataComponents.ENABLED, true));
        buf.writeBoolean(stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
        buf.writeBoolean(stack.getOrDefault(AllDataComponents.CHARGING, false));
        buf.writeVarInt(item.getCharge(stack));
        buf.writeVarInt(item.getMaxCharge(stack));
        buf.writeVarInt(stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME).ordinal());
        buf.writeBoolean(stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true));
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            buf.writeVarInt(toolbox.getTotalCount(c));
        DyeColor color = BackpackColorHelper.resolveDyeColor(stack);
        buf.writeVarInt(color != null ? color.getId() : -1);
    }

    public MagneticBackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(windowId, inv, decodeClientData(extraData, inv.player != null ? inv.player.level() : null));
    }

    private MagneticBackpackMenu(int windowId, Inventory inv, ClientInitData data) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId, inv,
                data.storageInventory(), data.toolboxInventory(), data.displayHandler(),
                ItemStack.EMPTY, data.lockedInventorySlot(), data.sortMode(), data.restockEnabled(),
                data.backpackColor());
        this.blockEntity = data.blockEntity();
        this.filterInventory = data.filterInventory();
        this.enabled = data.enabled();
        this.blacklist = data.blacklist();
        this.charging = data.charging();
        this.charge = data.charge();
        this.maxCharge = data.maxCharge();
        System.arraycopy(data.initialCounts(), 0, this.syncedToolboxCounts, 0, TOOLBOX_SLOTS);
        addSlots();
        addCommonStateDataSlots();
        addMagneticStateDataSlots();
    }

    private MagneticBackpackMenu(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId, inv,
                be.getItemHandler(), be.getToolboxInventory(), be.getToolboxInventory().asMenuDisplayHandler(),
                ItemStack.EMPTY, MenuValidationHelper.NO_LOCKED_SLOT, be.getSortMode(), be.isRestockEnabled(),
                be.getBackpackColor());
        this.blockEntity = be;
        this.filterInventory = be.getFilterInventory();
        this.enabled = be.isEnabled();
        this.blacklist = be.isBlacklist();
        this.charge = be.getCharge();
        this.maxCharge = be.getMaxCharge();
        this.charging = be.getSpeed() != 0 && this.charge < this.maxCharge;
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addCommonStateDataSlots();
        addMagneticStateDataSlots();
    }

    private MagneticBackpackMenu(int windowId, Inventory inv, ItemStack stack,
                                 BackpackInventory storage, PortableToolboxInventory toolbox) {
        super(AllMenus.MAGNETIC_BACKPACK.get(), windowId, inv,
                storage, toolbox, toolbox.asMenuDisplayHandler(),
                stack,
                MenuValidationHelper.findPlayerInventorySlot(inv.player, stack),
                stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME),
                stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true),
                BackpackColorHelper.resolveDyeColor(stack));
        this.blockEntity = null;
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
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addCommonStateDataSlots();
        addMagneticStateDataSlots();
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, MagneticBackpackBlockEntity be) {
        return new MagneticBackpackMenu(windowId, inv, be);
    }

    public static MagneticBackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        BackpackInventory storage = BackpackInventory.forStack(stack,
                () -> MagneticBackpackEquipment.syncMagneticBackpack(inv.player, stack));
        PortableToolboxInventory toolbox = PortableToolboxInventory.forStack(stack,
                () -> MagneticBackpackEquipment.syncMagneticBackpack(inv.player, stack));
        return new MagneticBackpackMenu(windowId, inv, stack, storage, toolbox);
    }

    private static ClientInitData decodeClientData(RegistryFriendlyByteBuf extraData, @Nullable Level level) {
        boolean fromBlockEntity = extraData.readBoolean();
        ItemStackHandler displayHandler = new ItemStackHandler(TOOLBOX_SLOTS);
        ItemStackHandler filters = new ItemStackHandler(FILTER_SLOTS);
        int[] initialCounts = new int[TOOLBOX_SLOTS];

        if (fromBlockEntity) {
            BlockPos pos = extraData.readBlockPos();
            CompoundTag tag = extraData.readNbt();
            MagneticBackpackBlockEntity found = null;
            if (level != null) {
                BlockEntity raw = level.getBlockEntity(pos);
                if (raw instanceof MagneticBackpackBlockEntity be)
                    found = be;
            }
            if (found != null) {
                found.readClient(tag, extraData.registryAccess());
                IItemHandler menuView = found.getToolboxInventory().asMenuDisplayHandler();
                for (int c = 0; c < TOOLBOX_SLOTS; c++) {
                    initialCounts[c] = found.getToolboxInventory().getTotalCount(c);
                    displayHandler.setStackInSlot(c, menuView.getStackInSlot(c));
                }
                int charge = found.getCharge();
                int maxCharge = found.getMaxCharge();
                boolean charging = found.getSpeed() != 0 && charge < maxCharge;
                return new ClientInitData(found, found.getItemHandler(), found.getToolboxInventory(),
                        displayHandler, found.getFilterInventory(), MenuValidationHelper.NO_LOCKED_SLOT,
                        found.isEnabled(), found.isBlacklist(),
                        charging, charge, maxCharge, found.getSortMode(), found.isRestockEnabled(),
                        initialCounts, found.getBackpackColor());
            }
            return new ClientInitData(null, new BackpackInventory(), PortableToolboxInventory.empty(),
                    displayHandler, filters, MenuValidationHelper.NO_LOCKED_SLOT,
                    true, false, false, 0, 1, SortMode.NAME, true, initialCounts, null);
        }

        int lockedInventorySlot = extraData.readVarInt();
        NonNullList<ItemStack> decodedFilters = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
        ItemContainerContents.STREAM_CODEC.decode(extraData).copyInto(decodedFilters);
        for (int i = 0; i < FILTER_SLOTS; i++)
            filters.setStackInSlot(i, decodedFilters.get(i));
        boolean enabled = extraData.readBoolean();
        boolean blacklist = extraData.readBoolean();
        boolean charging = extraData.readBoolean();
        int charge = extraData.readVarInt();
        int maxCharge = Math.max(1, extraData.readVarInt());
        SortMode sortMode = SortMode.byOrdinal(extraData.readVarInt());
        boolean restockEnabled = extraData.readBoolean();
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            initialCounts[c] = Math.max(0, extraData.readVarInt());
        int colorId = extraData.readVarInt();
        DyeColor color = colorId >= 0 ? DyeColor.byId(colorId) : null;
        return new ClientInitData(null, new BackpackInventory(), PortableToolboxInventory.empty(),
                displayHandler, filters, lockedInventorySlot, enabled, blacklist, charging, charge, maxCharge,
                sortMode, restockEnabled, initialCounts, color);
    }

    private void addMagneticStateDataSlots() {
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
    }

    private void addSlots() {
        // 0..26: 27 main storage slots
        addStorageSlots(STORAGE_X, STORAGE_Y);

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
        addToolboxSlots(TOOLBOX_X, TOOLBOX_Y);

        // 38..73: 36 player inventory + hotbar slots
        addPlayerInventorySlots(STORAGE_X, PLAYER_Y, HOTBAR_Y);
    }

    @Nullable
    public MagneticBackpackBlockEntity getBlockEntity() {
        return blockEntity;
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

    private boolean isFilterSlot(int slotId) {
        return slotId >= STORAGE_SLOTS && slotId < TOOLBOX_SLOT_START;
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
        if (!backpackStack.isEmpty() && MenuValidationHelper.isPlayerCarryingStack(player, backpackStack)) {
            List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
            for (int i = 0; i < FILTER_SLOTS; i++)
                filters.add(filterInventory.getStackInSlot(i).copy());
            MagneticBackpackItem.setFilters(backpackStack, filters);
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
        }
        broadcastChanges();
    }

    @Override
    protected boolean isProtectedSlot(int slotId) {
        return isFilterSlot(slotId) || isToolboxSlot(slotId);
    }

    @Override
    protected boolean handleCustomSlotClick(int slotId, int dragType, ClickType clickType, Player player) {
        if (!isFilterSlot(slotId))
            return false;
        if (clickType == ClickType.QUICK_CRAFT) {
            super.clicked(slotId, dragType, clickType, player);
            return true;
        }
        if (clickType == ClickType.THROW
                || clickType == ClickType.CLONE
                || clickType == ClickType.SWAP
                || clickType == ClickType.PICKUP_ALL) {
            return true;
        }
        ItemStack held = getCarried();
        ItemStack insert = (clickType == ClickType.QUICK_MOVE || held.isEmpty())
                ? ItemStack.EMPTY
                : held.copyWithCount(1);
        int filterIndex = slotId - STORAGE_SLOTS;
        filterInventory.setStackInSlot(filterIndex, insert);
        getSlot(slotId).setChanged();
        persistFilters(player);
        return true;
    }

    @Override
    protected void onBeforeRemoved(Player player) {
        if (blockEntity == null) {
            persistFilters(player);
        }
    }

    @Nullable
    @Override
    protected BlockEntity getBackingBlockEntity() {
        return blockEntity;
    }

    @Override
    protected SortMode readBlockEntitySortMode() {
        return blockEntity.getSortMode();
    }

    @Override
    protected void writeBlockEntitySortMode(SortMode mode) {
        blockEntity.setSortMode(mode);
    }

    @Override
    protected boolean readBlockEntityRestockEnabled() {
        return blockEntity.isRestockEnabled();
    }

    @Override
    protected void writeBlockEntityRestockEnabled(boolean enabled) {
        blockEntity.setRestockEnabled(enabled);
    }

    @Override
    protected void syncBlockEntity() {
        blockEntity.setChanged();
        blockEntity.sendData();
    }

    @Override
    protected void syncItemStack(Player player, ItemStack stack) {
        MagneticBackpackEquipment.syncMagneticBackpack(player, stack);
    }

    @Override
    public int getToolboxSlotStart() {
        return TOOLBOX_SLOT_START;
    }

    @Override
    public int getPlayerSlotStart() {
        return PLAYER_SLOT_START;
    }
}
