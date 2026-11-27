package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.registry.AllMenus;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class BackpackMenu extends AbstractBackpackMenu {

    public static final int BACKPACK_SLOTS = STORAGE_SLOTS;
    public static final int TOOLBOX_SLOT_START = BACKPACK_SLOTS;
    public static final int PLAYER_SLOT_START = BACKPACK_SLOTS + TOOLBOX_SLOTS;

    // 8x1 horizontal toolbox bar positioned directly beneath the 27-slot storage grid and above player inventory
    public static final int STORAGE_X = 8;
    public static final int STORAGE_Y = 18;
    public static final int TOOLBOX_X = 17;
    public static final int TOOLBOX_Y = 95;
    public static final int PLAYER_Y = 131;
    public static final int HOTBAR_Y = 189;

    @Nullable
    private final MechanicalBackpackBlockEntity blockEntity;
    @Nullable
    private final Predicate<Player> mountedValidity;
    @Nullable
    private final Supplier<SortMode> mountedSortGetter;
    @Nullable
    private final Consumer<SortMode> mountedSortSetter;
    @Nullable
    private final BooleanSupplier mountedRestockGetter;
    @Nullable
    private final Consumer<Boolean> mountedRestockSetter;

    private record ClientInitData(
            @Nullable MechanicalBackpackBlockEntity blockEntity,
            IItemHandler storageInventory,
            PortableToolboxInventory toolboxInventory,
            IItemHandler displayHandler,
            int lockedInventorySlot,
            SortMode sortMode,
            boolean restockEnabled,
            int[] initialCounts
    ) {
    }

    public static void writeItemOpenData(RegistryFriendlyByteBuf buf,
                                         Player player,
                                         ItemStack stack,
                                         PortableToolboxInventory toolbox) {
        buf.writeBoolean(false);
        buf.writeVarInt(MenuValidationHelper.findPlayerInventorySlot(player, stack));
        buf.writeVarInt(stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME).ordinal());
        buf.writeBoolean(stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true));
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            buf.writeVarInt(toolbox.getTotalCount(c));
    }

    public static void writeMountedOpenData(RegistryFriendlyByteBuf buf,
                                            PortableToolboxInventory toolbox,
                                            SortMode sortMode,
                                            boolean restockEnabled) {
        buf.writeBoolean(false);
        buf.writeVarInt(MenuValidationHelper.NO_LOCKED_SLOT);
        buf.writeVarInt((sortMode != null ? sortMode : SortMode.NAME).ordinal());
        buf.writeBoolean(restockEnabled);
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            buf.writeVarInt(toolbox.getTotalCount(c));
    }

    public BackpackMenu(int windowId, Inventory inv, RegistryFriendlyByteBuf extraData) {
        this(windowId, inv, decodeClientData(extraData));
    }

    private BackpackMenu(int windowId, Inventory inv, ClientInitData data) {
        super(AllMenus.BACKPACK.get(), windowId, inv,
                data.storageInventory(), data.toolboxInventory(), data.displayHandler(),
                ItemStack.EMPTY, data.lockedInventorySlot(), data.sortMode(), data.restockEnabled());
        this.blockEntity = data.blockEntity();
        this.mountedValidity = null;
        this.mountedSortGetter = null;
        this.mountedSortSetter = null;
        this.mountedRestockGetter = null;
        this.mountedRestockSetter = null;
        System.arraycopy(data.initialCounts(), 0, this.syncedToolboxCounts, 0, TOOLBOX_SLOTS);
        addSlots();
        addCommonStateDataSlots();
    }

    private BackpackMenu(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        super(AllMenus.BACKPACK.get(), windowId, inv,
                be.getItemHandler(), be.getToolboxInventory(), be.getToolboxInventory().asMenuDisplayHandler(),
                ItemStack.EMPTY, MenuValidationHelper.NO_LOCKED_SLOT,
                be.getSortMode(), be.isRestockEnabled());
        this.blockEntity = be;
        this.mountedValidity = null;
        this.mountedSortGetter = null;
        this.mountedSortSetter = null;
        this.mountedRestockGetter = null;
        this.mountedRestockSetter = null;
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addCommonStateDataSlots();
    }

    private BackpackMenu(int windowId, Inventory inv, ItemStack stack,
                         BackpackInventory storage, PortableToolboxInventory toolbox) {
        super(AllMenus.BACKPACK.get(), windowId, inv,
                storage, toolbox, toolbox.asMenuDisplayHandler(),
                stack,
                MenuValidationHelper.findPlayerInventorySlot(inv.player, stack),
                stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME),
                stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true));
        this.blockEntity = null;
        this.mountedValidity = null;
        this.mountedSortGetter = null;
        this.mountedSortSetter = null;
        this.mountedRestockGetter = null;
        this.mountedRestockSetter = null;
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addCommonStateDataSlots();
    }

    private BackpackMenu(int windowId,
                         Inventory inv,
                         BackpackInventory storage,
                         PortableToolboxInventory toolbox,
                         Predicate<Player> mountedValidity,
                         Supplier<SortMode> mountedSortGetter,
                         Consumer<SortMode> mountedSortSetter,
                         BooleanSupplier mountedRestockGetter,
                         Consumer<Boolean> mountedRestockSetter) {
        super(AllMenus.BACKPACK.get(), windowId, inv,
                storage, toolbox, toolbox.asMenuDisplayHandler(),
                ItemStack.EMPTY,
                MenuValidationHelper.NO_LOCKED_SLOT,
                mountedSortGetter.get(),
                mountedRestockGetter.getAsBoolean());
        this.blockEntity = null;
        this.mountedValidity = mountedValidity;
        this.mountedSortGetter = mountedSortGetter;
        this.mountedSortSetter = mountedSortSetter;
        this.mountedRestockGetter = mountedRestockGetter;
        this.mountedRestockSetter = mountedRestockSetter;
        for (int c = 0; c < TOOLBOX_SLOTS; c++)
            this.syncedToolboxCounts[c] = this.toolboxInventory.getTotalCount(c);
        addSlots();
        addCommonStateDataSlots();
    }

    public static BackpackMenu create(int windowId, Inventory inv, MechanicalBackpackBlockEntity be) {
        return new BackpackMenu(windowId, inv, be);
    }

    public static BackpackMenu create(int windowId, Inventory inv, ItemStack stack) {
        BackpackInventory storage = BackpackInventory.forStack(stack,
                () -> BackpackEquipment.syncBackpack(inv.player, stack));
        PortableToolboxInventory toolbox = PortableToolboxInventory.forStack(stack,
                () -> BackpackEquipment.syncBackpack(inv.player, stack));
        return new BackpackMenu(windowId, inv, stack, storage, toolbox);
    }

    public static BackpackMenu createMounted(int windowId,
                                             Inventory inv,
                                             BackpackInventory storage,
                                             PortableToolboxInventory toolbox,
                                             Predicate<Player> validityCheck,
                                             Supplier<SortMode> sortGetter,
                                             Consumer<SortMode> sortSetter,
                                             BooleanSupplier restockGetter,
                                             Consumer<Boolean> restockSetter) {
        return new BackpackMenu(windowId, inv, storage, toolbox, validityCheck,
                sortGetter, sortSetter, restockGetter, restockSetter);
    }

    private static ClientInitData decodeClientData(RegistryFriendlyByteBuf extraData) {
        boolean fromBlockEntity = extraData.readBoolean();
        MechanicalBackpackBlockEntity found = null;
        int lockedInventorySlot = MenuValidationHelper.NO_LOCKED_SLOT;
        int[] initialCounts = new int[TOOLBOX_SLOTS];
        SortMode initialSortMode = SortMode.NAME;
        boolean initialRestock = true;

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
                initialSortMode = found.getSortMode();
                initialRestock = found.isRestockEnabled();
                for (int c = 0; c < TOOLBOX_SLOTS; c++)
                    initialCounts[c] = found.getToolboxInventory().getTotalCount(c);
            }
        } else {
            lockedInventorySlot = extraData.readVarInt();
            initialSortMode = SortMode.byOrdinal(extraData.readVarInt());
            initialRestock = extraData.readBoolean();
            for (int c = 0; c < TOOLBOX_SLOTS; c++)
                initialCounts[c] = Math.max(0, extraData.readVarInt());
        }

        IItemHandler storage = found != null ? found.getItemHandler() : new BackpackInventory();
        // Client-side toolbox slots use a dedicated display handler so vanilla ClientboundContainerSetSlotPacket
        // (which clamps ItemStack counts to maxStackSize) NEVER overwrites a placed BlockEntity's 4-stack backing!
        ItemStackHandler displayHandler = new ItemStackHandler(TOOLBOX_SLOTS);
        PortableToolboxInventory toolbox;
        if (found != null) {
            toolbox = found.getToolboxInventory();
            IItemHandler menuView = toolbox.asMenuDisplayHandler();
            for (int c = 0; c < TOOLBOX_SLOTS; c++)
                displayHandler.setStackInSlot(c, menuView.getStackInSlot(c));
        } else {
            toolbox = PortableToolboxInventory.empty();
        }
        return new ClientInitData(found, storage, toolbox, displayHandler, lockedInventorySlot,
                initialSortMode, initialRestock, initialCounts);
    }

    private void addSlots() {
        addStorageSlots(STORAGE_X, STORAGE_Y);
        addToolboxSlots(TOOLBOX_X, TOOLBOX_Y);
        addPlayerInventorySlots(STORAGE_X, PLAYER_Y, HOTBAR_Y);
    }

    @Nullable
    @Override
    protected BlockEntity getBackingBlockEntity() {
        return blockEntity;
    }

    @Override
    protected boolean isMountedContext() {
        return mountedValidity != null;
    }

    @Override
    protected boolean isMountedValid(Player player) {
        return mountedValidity != null && mountedValidity.test(player);
    }

    @Override
    protected SortMode readMountedSortMode() {
        return mountedSortGetter != null ? mountedSortGetter.get() : sortMode;
    }

    @Override
    protected void writeMountedSortMode(SortMode mode) {
        if (mountedSortSetter != null)
            mountedSortSetter.accept(mode);
    }

    @Override
    protected boolean readMountedRestockEnabled() {
        return mountedRestockGetter != null ? mountedRestockGetter.getAsBoolean() : restockEnabled;
    }

    @Override
    protected void writeMountedRestockEnabled(boolean enabled) {
        if (mountedRestockSetter != null)
            mountedRestockSetter.accept(enabled);
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
        blockEntity.syncToClients();
    }

    @Override
    protected void syncItemStack(Player player, ItemStack stack) {
        BackpackEquipment.syncBackpack(player, stack);
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
