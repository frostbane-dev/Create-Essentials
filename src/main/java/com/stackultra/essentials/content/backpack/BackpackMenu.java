package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

import javax.annotation.Nullable;

public class BackpackMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int BACKPACK_SLOTS = BackpackInventory.SLOTS;
    public static final int TOOLBOX_SLOTS = PortableToolboxInventory.COMPARTMENTS;
    public static final int TOOLBOX_SLOT_START = BACKPACK_SLOTS;
    public static final int PLAYER_SLOT_START = BACKPACK_SLOTS + TOOLBOX_SLOTS;

    // 8x1 horizontal bottom toolbox bar centered beneath the 176x167 main window
    public static final int TOOLBOX_X = 17;
    public static final int TOOLBOX_Y = 186;

    private final Inventory playerInventory;
    private final IItemHandler backpackInventory;
    private final PortableToolboxInventory toolboxInventory;
    private final IItemHandler toolboxSlotBacking;
    @Nullable
    private final MechanicalBackpackBlockEntity blockEntity;
    private final ItemStack backpackStack;

    private SortMode sortMode;
    private boolean restockEnabled;
    private final int[] syncedToolboxCounts = new int[TOOLBOX_SLOTS];

    public BackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        boolean fromBlockEntity = extraData.readBoolean();
        MechanicalBackpackBlockEntity found = null;
        int[] initialCounts = new int[TOOLBOX_SLOTS];
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
            if (found != null) {
                for (int c = 0; c < TOOLBOX_SLOTS; c++)
                    initialCounts[c] = found.getToolboxInventory().getTotalCount(c);
            }
        } else {
            for (int c = 0; c < TOOLBOX_SLOTS; c++)
                initialCounts[c] = Math.max(0, extraData.readVarInt());
        }
        this.blockEntity = found;
        this.backpackStack = ItemStack.EMPTY;
        this.backpackInventory = found != null ? found.getItemHandler() : new BackpackInventory();

        // Client-side toolbox slots use a dedicated display handler so vanilla ClientboundContainerSetSlotPacket
        // (which clamps ItemStack counts to maxStackSize) NEVER overwrites a placed BlockEntity's 4-stack backing!
        ItemStackHandler displayHandler = new ItemStackHandler(TOOLBOX_SLOTS);
        if (found != null) {
            this.toolboxInventory = found.getToolboxInventory();
            this.sortMode = found.getSortMode();
            this.restockEnabled = found.isRestockEnabled();
            for (int c = 0; c < TOOLBOX_SLOTS; c++)
                displayHandler.setStackInSlot(c, found.getToolboxInventory().getStackInSlot(c));
        } else {
            this.toolboxInventory = PortableToolboxInventory.empty();
            this.sortMode = SortMode.NAME;
            this.restockEnabled = true;
        }
        this.toolboxSlotBacking = displayHandler;
        System.arraycopy(initialCounts, 0, this.syncedToolboxCounts, 0, TOOLBOX_SLOTS);

        addBackpackSlots();
        addToolboxSlots();
        addPlayerSlots();
        addStateDataSlots();
    }

    private BackpackMenu(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = be;
        this.backpackStack = ItemStack.EMPTY;
        this.backpackInventory = be.getItemHandler();
        this.toolboxInventory = be.getToolboxInventory();
        this.toolboxSlotBacking = this.toolboxInventory;
        this.sortMode = be.getSortMode();
        this.restockEnabled = be.isRestockEnabled();
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addBackpackSlots();
        addToolboxSlots();
        addPlayerSlots();
        addStateDataSlots();
    }

    private BackpackMenu(int windowId, Inventory inv, ItemStack stack) {
        super(AllMenus.BACKPACK.get(), windowId);
        this.playerInventory = inv;
        this.blockEntity = null;
        this.backpackStack = stack;
        this.backpackInventory = BackpackInventory.forStack(stack,
                () -> BackpackEquipment.syncBackpack(inv.player, stack));
        this.toolboxInventory = PortableToolboxInventory.forStack(stack,
                () -> BackpackEquipment.syncBackpack(inv.player, stack));
        this.toolboxSlotBacking = this.toolboxInventory;
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        this.restockEnabled = stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addBackpackSlots();
        addToolboxSlots();
        addPlayerSlots();
        addStateDataSlots();
    }

    public static BackpackMenu create(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        return new BackpackMenu(windowId, inv, be);
    }

    public static BackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        return new BackpackMenu(windowId, inv, stack);
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

    private void addToolboxSlots() {
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            addSlot(new SlotItemHandler(toolboxSlotBacking, c, TOOLBOX_X + c * 18, TOOLBOX_Y));
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

    public boolean isToolboxSlot(int slotId) {
        return slotId >= TOOLBOX_SLOT_START && slotId < PLAYER_SLOT_START;
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
            BackpackEquipment.syncBackpack(player, backpackStack);
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
            BackpackEquipment.syncBackpack(player, backpackStack);
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
    public PortableToolboxInventory getToolboxInventory() {
        return toolboxInventory;
    }

    @Override
    public boolean isBackingStack(ItemStack stack) {
        return blockEntity == null && !backpackStack.isEmpty() && backpackStack == stack;
    }

    public ItemStack getBackpackStack() {
        return backpackStack;
    }

    @Override
    public void transferMatchingToStorage(Player player) {
        if (BackpackInventoryOps.transferMatchingToStorage(
                this, toolboxInventory, 0, BACKPACK_SLOTS, PLAYER_SLOT_START, this.slots.size(),
                player, backpackStack.isEmpty() ? null : backpackStack)) {
            syncBacking(player);
        }
    }

    @Override
    public void transferMatchingToInventory(Player player) {
        if (BackpackInventoryOps.transferMatchingToInventory(
                this, toolboxInventory, 0, BACKPACK_SLOTS, PLAYER_SLOT_START, this.slots.size(), player)) {
            syncBacking(player);
        }
    }

    @Override
    public void sortStorage(Player player) {
        if (BackpackInventoryOps.sortStorage(this, toolboxInventory, 0, BACKPACK_SLOTS, getSortMode())) {
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
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
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
        return !isToolboxSlot(slotIn.index);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return !isToolboxSlot(slotIn.index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (isToolboxSlot(index))
            return ItemStack.EMPTY;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < BACKPACK_SLOTS) {
                if (!moveItemStackTo(stack, PLAYER_SLOT_START, this.slots.size(), true))
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
