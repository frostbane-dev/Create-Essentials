package com.stackultra.essentials.compat.create;

import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlock;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Enables Create Mechanical Arms to directly deposit into and extract from placed
 * Mechanical, Netherite Mechanical, and Magnetic Backpacks via {@code Capabilities.ItemHandler.BLOCK}.
 */
public class BackpackArmInteractionPointType extends ArmInteractionPointType {

    @Override
    public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof MechanicalBackpackBlock
                || state.getBlock() instanceof MagneticBackpackBlock;
    }

    @Override
    public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
        return new ArmInteractionPoint(this, level, pos, state);
    }
}
