package com.stackultra.essentials.compat.create;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.simibubi.create.api.contraption.storage.item.WrapperMountedItemStorage;
import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.foundation.codec.CreateCodecs;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.backpack.BackpackHelper;
import com.stackultra.essentials.content.backpack.BackpackInventory;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlock;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * Create Contraption {@link com.simibubi.create.api.contraption.storage.item.MountedItemStorage}
 * backing placed Mechanical, Netherite Mechanical, and Magnetic Backpacks on Trains,
 * Minecart Contraptions, Mechanical Bearings, and Gantries.
 * <p>
 * Exposes the backpack's 27-slot main storage to Contraption Harvesters, Drills, Saws, Deployers,
 * and Portable Storage Interfaces while strictly enforcing {@link BackpackHelper#isNestedContainer}
 * anti-nesting rules. On disassembly, Create restores all other BlockEntity NBT (8 toolbox
 * compartments + ghost filters, custom name, dye color, enchantments, Netherite state, and Magnetic
 * Backpack charge/filters/enabled/blacklist) before {@link #unmount} writes the updated 27 slots back.
 */
public class BackpackMountedStorage extends WrapperMountedItemStorage<BackpackInventory> {

    public static final MapCodec<BackpackMountedStorage> CODEC =
            CreateCodecs.ITEM_STACK_HANDLER
                    .xmap(BackpackMountedStorage::fromHandler, storage -> (ItemStackHandler) storage.wrapped)
                    .fieldOf("value");

    public BackpackMountedStorage(MountedItemStorageType<?> type, BackpackInventory wrapped) {
        super(type, wrapped);
    }

    protected BackpackMountedStorage(BackpackInventory wrapped) {
        this(AllCreateCompat.BACKPACK_STORAGE.get(), wrapped);
    }

    public static BackpackMountedStorage fromHandler(IItemHandler source) {
        return fromHandler(AllCreateCompat.BACKPACK_STORAGE.get(), source);
    }

    static BackpackMountedStorage fromHandler(MountedItemStorageType<?> type, IItemHandler source) {
        BackpackInventory copy = new BackpackInventory();
        if (source != null) {
            int limit = Math.min(copy.getSlots(), source.getSlots());
            for (int i = 0; i < limit; i++) {
                ItemStack stack = source.getStackInSlot(i);
                if (!stack.isEmpty())
                    copy.setStackInSlot(i, stack.copy());
            }
        }
        return new BackpackMountedStorage(type, copy);
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
    public void unmount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MechanicalBackpackBlockEntity mechBe) {
            ItemHelper.copyContents(this.wrapped, mechBe.getInventory());
            mechBe.syncToClients();
        } else if (be instanceof MagneticBackpackBlockEntity magBe) {
            ItemHelper.copyContents(this.wrapped, magBe.getInventory());
            magBe.setChanged();
            magBe.sendData();
        }
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
