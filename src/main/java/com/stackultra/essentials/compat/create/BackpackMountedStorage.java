package com.stackultra.essentials.compat.create;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.codec.CreateCodecs;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.BlockItemComponentHelper;
import com.stackultra.essentials.content.backpack.BackpackHelper;
import com.stackultra.essentials.content.backpack.BackpackInventory;
import com.stackultra.essentials.content.backpack.BackpackMenu;
import com.stackultra.essentials.content.backpack.BackpackSounds;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlock;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.content.backpack.SortMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.OptionalInt;
import java.util.function.Predicate;

/**
 * Create Contraption {@link com.simibubi.create.api.contraption.storage.item.MountedItemStorage}
 * backing placed Mechanical, Netherite Mechanical, and Magnetic Backpacks on Trains,
 * Minecart Contraptions, Mechanical Bearings, and Gantries.
 * <p>
 * Exposes the 27-slot main storage to Contraption Harvesters, Drills, Saws, Deployers, and
 * Portable Storage Interfaces (with strict {@link BackpackHelper#isNestedContainer} anti-nesting),
 * carries the 8-compartment {@link PortableToolboxInventory} + ghost filters + SortMode + Restock
 * state across Contraption save/load/unmount, and opens our custom dark beveled {@link BackpackMenu}
 * with Create's exact Toolbox open sound when players interact with a moving Contraption backpack.
 */
public class BackpackMountedStorage extends WrapperMountedItemStorage<BackpackInventory> {

    public static final MapCodec<BackpackMountedStorage> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    CreateCodecs.ITEM_STACK_HANDLER.fieldOf("value")
                            .forGetter(storage -> (ItemStackHandler) storage.wrapped),
                    CreateCodecs.ITEM_STACK_HANDLER
                            .optionalFieldOf("toolbox_items", new ItemStackHandler(PortableToolboxInventory.BACKING_SLOTS))
                            .forGetter(storage -> storage.toolbox.toBackingItemsHandler()),
                    CreateCodecs.ITEM_STACK_HANDLER
                            .optionalFieldOf("toolbox_filters", new ItemStackHandler(PortableToolboxInventory.COMPARTMENTS))
                            .forGetter(storage -> storage.toolbox.toFiltersHandler()),
                    Codec.STRING
                            .optionalFieldOf("sort_mode", SortMode.NAME.getSerializedName())
                            .forGetter(storage -> storage.sortMode.getSerializedName()),
                    Codec.BOOL
                            .optionalFieldOf("restock", true)
                            .forGetter(storage -> storage.restockEnabled)
            ).apply(instance, BackpackMountedStorage::decode));

    private final PortableToolboxInventory toolbox;
    private SortMode sortMode;
    private boolean restockEnabled;

    public BackpackMountedStorage(MountedItemStorageType<?> type,
                                  BackpackInventory wrapped,
                                  PortableToolboxInventory toolbox,
                                  SortMode sortMode,
                                  boolean restockEnabled) {
        super(type, wrapped);
        this.toolbox = toolbox != null ? toolbox : PortableToolboxInventory.empty();
        this.sortMode = sortMode != null ? sortMode : SortMode.NAME;
        this.restockEnabled = restockEnabled;
    }

    private static BackpackMountedStorage decode(ItemStackHandler storageHandler,
                                                 ItemStackHandler toolboxItems,
                                                 ItemStackHandler toolboxFilters,
                                                 String sortModeName,
                                                 boolean restockEnabled) {
        BackpackInventory storage = copyStorage(storageHandler);
        PortableToolboxInventory toolbox = PortableToolboxInventory.fromHandlers(toolboxItems, toolboxFilters);
        return new BackpackMountedStorage(
                AllCreateCompat.BACKPACK_STORAGE.get(),
                storage,
                toolbox,
                SortMode.byName(sortModeName),
                restockEnabled);
    }

    static BackpackMountedStorage fromBlockEntity(MountedItemStorageType<?> type,
                                                  IItemHandler storageSource,
                                                  PortableToolboxInventory toolboxSource,
                                                  SortMode sortMode,
                                                  boolean restockEnabled) {
        return new BackpackMountedStorage(
                type,
                copyStorage(storageSource),
                PortableToolboxInventory.copyOf(toolboxSource),
                sortMode,
                restockEnabled);
    }

    private static BackpackInventory copyStorage(@Nullable IItemHandler source) {
        BackpackInventory copy = new BackpackInventory();
        if (source != null) {
            int limit = Math.min(copy.getSlots(), source.getSlots());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = source.getStackInSlot(i);
                if (!stack.isEmpty())
                    copy.setStackInSlot(i, stack.copy());
            }
        }
        return copy;
    }

    public PortableToolboxInventory getToolboxInventory() {
        return toolbox;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        this.sortMode = sortMode != null ? sortMode : SortMode.NAME;
    }

    public boolean isRestockEnabled() {
        return restockEnabled;
    }

    public void setRestockEnabled(boolean restockEnabled) {
        this.restockEnabled = restockEnabled;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !BackpackHelper.isNestedContainer(stack) && super.isItemValid(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (BackpackHelper.isNestedContainer(stack))
            return stack;
        return super.insertItem(slot, stack, simulate);
    }

    @Override
    public boolean handleInteraction(ServerPlayer player,
                                     Contraption contraption,
                                     StructureTemplate.StructureBlockInfo info) {
        if (contraption == null || contraption.entity == null)
            return false;

        ServerLevel level = player.serverLevel();
        Vec3 localPos = Vec3.atCenterOf(info.pos());
        Predicate<Player> validityCheck = p -> {
            if (!contraption.entity.isAlive())
                return false;
            Vec3 globalPos = contraption.entity.toGlobalVector(localPos, 0);
            return p.distanceToSqr(globalPos) < 64.0D;
        };

        Component menuName = resolveMenuName(info, contraption, level.registryAccess());
        OptionalInt opened = player.openMenu(
                new SimpleMenuProvider(
                        (id, inv, p) -> BackpackMenu.createMounted(
                                id, inv, this.wrapped, this.toolbox, validityCheck,
                                this::getSortMode, this::setSortMode,
                                this::isRestockEnabled, this::setRestockEnabled),
                        menuName),
                buf -> BackpackMenu.writeMountedOpenData(buf, this.toolbox, this.sortMode, this.restockEnabled));

        if (opened.isPresent()) {
            Vec3 globalPos = contraption.entity.toGlobalVector(localPos, 0);
            playOpeningSound(level, globalPos);
            return true;
        }
        return false;
    }

    @Override
    protected void playOpeningSound(ServerLevel level, Vec3 pos) {
        BackpackSounds.playOpenSound(level, pos.x, pos.y, pos.z, SoundSource.BLOCKS);
    }

    @Override
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MechanicalBackpackBlockEntity mechBe) {
            ItemHelper.copyContents(this.wrapped, mechBe.getInventory());
            mechBe.getToolboxInventory().copyFrom(this.toolbox);
            mechBe.setSortMode(this.sortMode);
            mechBe.setRestockEnabled(this.restockEnabled);
            mechBe.syncToClients();
        } else if (be instanceof MagneticBackpackBlockEntity magBe) {
            ItemHelper.copyContents(this.wrapped, magBe.getInventory());
            magBe.getToolboxInventory().copyFrom(this.toolbox);
            magBe.setSortMode(this.sortMode);
            magBe.setRestockEnabled(this.restockEnabled);
            magBe.setChanged();
            magBe.sendData();
        }
    }

    Component resolveMenuName(StructureTemplate.StructureBlockInfo info,
                              Contraption contraption,
                              @Nullable HolderLookup.Provider registries) {
        if (info.nbt() != null && registries != null) {
            BlockItemComponentHelper.PreservedComponents preserved =
                    new BlockItemComponentHelper.PreservedComponents();
            preserved.readFromTag(info.nbt(), registries);
            Component custom = preserved.getCustomName();
            if (custom != null)
                return custom;
        }
        return getMenuName(info, contraption);
    }

    @Override
    protected Component getMenuName(StructureTemplate.StructureBlockInfo info, Contraption contraption) {
        BlockState state = info.state();
        if (state.hasProperty(MechanicalBackpackBlock.NETHERITE)
                && state.getValue(MechanicalBackpackBlock.NETHERITE)) {
            return CreateLang.translateDirect("contraptions.moving_container",
                    Component.translatable("item.createessentials.netherite_backpack"));
        }
        return super.getMenuName(info, contraption);
    }
}
