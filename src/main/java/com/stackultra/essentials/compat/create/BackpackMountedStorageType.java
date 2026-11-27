package com.stackultra.essentials.compat.create;

import com.simibubi.create.api.contraption.storage.item.MountedItemStorageType;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * {@link MountedItemStorageType} mounting placed {@link MechanicalBackpackBlockEntity} and
 * {@link MagneticBackpackBlockEntity} 27-slot inventories and 8-compartment toolboxes onto Create Contraptions.
 */
public class BackpackMountedStorageType extends MountedItemStorageType<BackpackMountedStorage> {

    public BackpackMountedStorageType() {
        super(BackpackMountedStorage.CODEC);
    }

    @Override
    @Nullable
    public BackpackMountedStorage mount(Level level, BlockState state, BlockPos pos, @Nullable BlockEntity be) {
        if (be instanceof MechanicalBackpackBlockEntity mechBe) {
            return BackpackMountedStorage.fromBlockEntity(
                    this,
                    mechBe.getInventory(),
                    mechBe.getToolboxInventory(),
                    mechBe.getSortMode(),
                    mechBe.isRestockEnabled());
        }
        if (be instanceof MagneticBackpackBlockEntity magBe) {
            return BackpackMountedStorage.fromBlockEntity(
                    this,
                    magBe.getInventory(),
                    magBe.getToolboxInventory(),
                    magBe.getSortMode(),
                    magBe.isRestockEnabled());
        }
        return null;
    }
}
