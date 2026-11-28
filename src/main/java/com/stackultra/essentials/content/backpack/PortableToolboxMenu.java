package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * Interactive server-backed container menu mirroring Create 6.0.9's {@code ToolboxMenu}
 * for the backpack's portable 8-compartment toolbox (opened via {@code Alt}).
 * <p>
 * Layout matches Create's {@code ToolboxMenu} pixel-for-pixel:
 * <ul>
 *   <li>Slots {@code 0..7}: 8 circular toolbox compartments (up to 256 items each, persistent ghost filters)</li>
 *   <li>Slots {@code 8..34}: 27 main player inventory slots ({@code 8, 165})</li>
 *   <li>Slots {@code 35..43}: 9 player hotbar slots ({@code 8, 223})</li>
 * </ul>
 * Enforces anti-nesting, open-slot locking on the backing backpack, and live shared
 * {@link PortableToolboxInventory} state via {@link IBackpackMenu}.
 */
public class PortableToolboxMenu extends AbstractContainerMenu implements IBackpackMenu {

    public static final int TOOLBOX_SLOTS = PortableToolboxInventory.COMPARTMENTS;
    public static final int PLAYER_SLOT_START = TOOLBOX_SLOTS;

    // Exact Create 6.0.9 ToolboxMenu circular slot coordinates (x0 = 79, y0 = 37)
    public static final int[] SLOT_X = { 79, 112, 145, 151, 145, 112, 79, 73 };
    public static final int[] SLOT_Y = { 37,  31,  37,  70, 103, 109, 103, 70 };

    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 165;
    public static final int HOTBAR_Y = 223;

    private final Inventory playerInventory;
    private final PortableToolboxInventory toolboxInventory;
    private final IItemHandler toolboxSlotBacking;
    private final ItemStack backpackStack;
    private final int lockedInventorySlot;

    private boolean restockEnabled;
    private final int[] syncedToolboxCounts = new int[TOOLBOX_SLOTS];

    public static PortableToolboxMenu create(int windowId, Inventory playerInventory, ItemStack backpackStack) {
        PortableToolboxInventory toolbox = PortableToolboxInventory.forStack(backpackStack);
        int lockedSlot = MenuValidationHelper.findPlayerInventorySlot(playerInventory.player, backpackStack);
        int[] initialCounts = new int[TOOLBOX_SLOTS];
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            initialCounts[c] = toolbox.getTotalCount(c);
        }
        return new PortableToolboxMenu(
                windowId,
                playerInventory,
                toolbox,
                toolbox.asMenuDisplayHandler(),
                backpackStack,
                lockedSlot,
                backpackStack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true),
                initialCounts);
    }

    public static void writeOpenData(RegistryFriendlyByteBuf buf,
                                     Player player,
                                     ItemStack backpackStack,
                                     PortableToolboxInventory toolbox) {
        buf.writeBoolean(backpackStack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true));
        buf.writeVarInt(MenuValidationHelper.findPlayerInventorySlot(player, backpackStack));
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            buf.writeVarInt(toolbox.getTotalCount(c));
        }
    }

    private record OpenData(boolean restockEnabled, int lockedInventorySlot, int[] counts) {
        static OpenData read(RegistryFriendlyByteBuf extraData) {
            boolean restock = extraData == null || extraData.readBoolean();
            int locked = extraData != null ? extraData.readVarInt() : MenuValidationHelper.NO_LOCKED_SLOT;
            int[] counts = new int[TOOLBOX_SLOTS];
            if (extraData != null) {
                for (int c = 0; c < TOOLBOX_SLOTS; c++) {
                    counts[c] = Math.max(0, extraData.readVarInt());
                }
            }
            return new OpenData(restock, locked, counts);
        }
    }

    public PortableToolboxMenu(int windowId, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(windowId, playerInventory, OpenData.read(extraData));
    }

    private PortableToolboxMenu(int windowId, Inventory playerInventory, OpenData data) {
        this(
                windowId,
                playerInventory,
                PortableToolboxInventory.empty(),
                new net.neoforged.neoforge.items.ItemStackHandler(TOOLBOX_SLOTS),
                ItemStack.EMPTY,
                data.lockedInventorySlot(),
                data.restockEnabled(),
                data.counts());
    }

    private PortableToolboxMenu(int windowId,
                                Inventory playerInventory,
                                PortableToolboxInventory toolboxInventory,
                                IItemHandler toolboxSlotBacking,
                                ItemStack backpackStack,
                                int lockedInventorySlot,
                                boolean initialRestockEnabled,
                                int[] initialCounts) {
        super(AllMenus.PORTABLE_TOOLBOX.get(), windowId);
        this.playerInventory = playerInventory;
        this.toolboxInventory = toolboxInventory;
        this.toolboxSlotBacking = toolboxSlotBacking;
        this.backpackStack = backpackStack;
        this.lockedInventorySlot = lockedInventorySlot;
        this.restockEnabled = initialRestockEnabled;
        if (initialCounts != null) {
            System.arraycopy(initialCounts, 0, this.syncedToolboxCounts, 0, TOOLBOX_SLOTS);
        }

        // 8 circular Create Toolbox slots (indices 0..7)
        for (int c = 0; c < TOOLBOX_SLOTS; c++) {
            addSlot(new SlotItemHandler(this.toolboxSlotBacking, c, SLOT_X[c], SLOT_Y[c]));
        }

        // 27 main player inventory slots (indices 8..34)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18) {
                    @Override
                    public boolean mayPickup(Player player) {
                        return !isLockedOpenSlot(this);
                    }
                });
            }
        }

        // 9 player hotbar slots (indices 35..43)
        for (int hotbar = 0; hotbar < 9; hotbar++) {
            addSlot(new Slot(playerInventory, hotbar, PLAYER_INV_X + hotbar * 18, HOTBAR_Y) {
                @Override
                public boolean mayPickup(Player player) {
                    return !isLockedOpenSlot(this);
                }
            });
        }

        // Sync Restock toggle & exact 0..256 compartment counts
        addDataSlot(new DataSlot() {
            @Override
            public int get() {
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

    public boolean isToolboxSlot(int slotId) {
        return slotId >= 0 && slotId < TOOLBOX_SLOTS;
    }

    private boolean isLockedOpenSlot(Slot slot) {
        return MenuValidationHelper.isLockedSlot(slot, playerInventory, backpackStack, lockedInventorySlot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player == null)
            return false;
        if (player.level().isClientSide())
            return true;
        return MenuValidationHelper.isPlayerCarryingStack(player, backpackStack);
    }

    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (!player.level().isClientSide() && !stillValid(player))
            return;
        if (MenuValidationHelper.isLockedOpenStackClick(
                this, backpackStack, lockedInventorySlot, slotId, dragType, clickType, player)) {
            return;
        }
        if (isToolboxSlot(slotId)) {
            if (clickType == ClickType.QUICK_CRAFT) {
                super.clicked(slotId, dragType, clickType, player);
                return;
            }
            if (!player.level().isClientSide()) {
                if (BackpackInventoryOps.handleToolboxSlotClick(
                        this, toolboxInventory, slotId, dragType, clickType, player,
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
        return !isToolboxSlot(slotIn.index) && !isLockedOpenSlot(slotIn);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slotIn) {
        return !isToolboxSlot(slotIn.index) && !isLockedOpenSlot(slotIn);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < PLAYER_SLOT_START || index >= this.slots.size())
            return ItemStack.EMPTY;

        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || isLockedOpenSlot(slot))
            return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        if (stack == backpackStack || BackpackHelper.isNestedContainer(stack))
            return ItemStack.EMPTY;

        if (player.level().isClientSide())
            return ItemStack.EMPTY;

        ItemStack original = stack.copy();
        ItemStack remaining = stack.copy();

        // 1. Insert into any existing matching or ghost-reserved compartment first
        for (int c = 0; c < TOOLBOX_SLOTS && !remaining.isEmpty(); c++) {
            if (!toolboxInventory.getFilter(c).isEmpty() && toolboxInventory.canAccept(c, remaining)) {
                remaining = toolboxInventory.insertIntoCompartment(c, remaining, false);
            }
        }

        // 2. Otherwise claim the first completely empty unreserved compartment
        if (!remaining.isEmpty()) {
            for (int c = 0; c < TOOLBOX_SLOTS; c++) {
                if (toolboxInventory.getFilter(c).isEmpty()
                        && toolboxInventory.getTotalCount(c) == 0
                        && toolboxInventory.canAccept(c, remaining)) {
                    remaining = toolboxInventory.insertIntoCompartment(c, remaining, false);
                    break;
                }
            }
        }

        if (remaining.getCount() == original.getCount())
            return ItemStack.EMPTY;

        slot.set(remaining);
        slot.setChanged();
        syncBacking(player);
        return remaining.isEmpty() ? original : ItemStack.EMPTY;
    }

    /**
     * Returns all matching items from the player's 36 inventory/hotbar slots into matching
     * or ghost-reserved toolbox compartments (matching Create's native {@code ToolboxDisposeAllPacket}).
     */
    public boolean disposeMatchingFromPlayer(Player player) {
        if (player.level().isClientSide() || !stillValid(player))
            return false;

        boolean changed = false;
        for (int i = 0; i < playerInventory.items.size(); i++) {
            ItemStack stack = playerInventory.getItem(i);
            if (stack.isEmpty()
                    || stack == backpackStack
                    || PortableToolboxHandler.isExtendoGrip(stack)
                    || BackpackHelper.isNestedContainer(stack)) {
                continue;
            }
            ItemStack remaining = stack.copy();
            for (int c = 0; c < TOOLBOX_SLOTS && !remaining.isEmpty(); c++) {
                if (!toolboxInventory.getFilter(c).isEmpty() && toolboxInventory.canAccept(c, remaining)) {
                    remaining = toolboxInventory.insertIntoCompartment(c, remaining, false);
                }
            }
            if (remaining.getCount() != stack.getCount() || remaining.isEmpty()) {
                playerInventory.setItem(i, remaining);
                changed = true;
            }
        }

        if (changed) {
            syncBacking(player);
        }
        return changed;
    }

    private void syncBacking(Player player) {
        if (!backpackStack.isEmpty()) {
            toolboxInventory.save(backpackStack);
            if (backpackStack.getItem() instanceof MagneticBackpackItem) {
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
            } else {
                BackpackEquipment.syncBackpack(player, backpackStack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.onToolboxModified(serverPlayer, backpackStack, toolboxInventory);
            }
        }
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && !backpackStack.isEmpty()) {
            toolboxInventory.save(backpackStack);
            if (backpackStack.getItem() instanceof MagneticBackpackItem) {
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpackStack);
            } else {
                BackpackEquipment.syncBackpack(player, backpackStack);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.onToolboxModified(serverPlayer, backpackStack, toolboxInventory);
            }
        }
    }

    // ---- IBackpackMenu contract (shared live PortableToolboxInventory integration) ----

    @Override
    public SortMode getSortMode() {
        return SortMode.NAME;
    }

    @Override
    public void setSortMode(SortMode mode, Player player) {
    }

    @Override
    public void transferMatchingToStorage(Player player) {
    }

    @Override
    public void transferMatchingToInventory(Player player) {
    }

    @Override
    public void sortStorage(Player player) {
    }

    @Override
    public boolean isRestockEnabled() {
        if (!playerInventory.player.level().isClientSide() && !backpackStack.isEmpty())
            return backpackStack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        return restockEnabled;
    }

    @Override
    public void setRestockEnabled(boolean enabled, Player player) {
        this.restockEnabled = enabled;
        if (!backpackStack.isEmpty()) {
            backpackStack.set(ExtraDataComponents.TOOLBOX_RESTOCK, enabled);
            syncBacking(player);
            if (player instanceof ServerPlayer serverPlayer) {
                PortableToolboxHandler.notifyRestockToggled(serverPlayer, backpackStack);
            }
        }
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
        return !backpackStack.isEmpty() && backpackStack == stack;
    }
}
