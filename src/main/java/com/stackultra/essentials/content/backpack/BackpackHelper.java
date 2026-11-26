package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class BackpackHelper {

    private BackpackHelper() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack.getItem() instanceof MechanicalBackpackItem
                || stack.getItem() instanceof MagneticBackpackItem;
    }

    public static boolean isNestedContainer(ItemStack stack) {
        if (isBackpack(stack))
            return true;
        if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock)
            return true;
        return AllTags.AllItemTags.TOOLBOXES.matches(stack);
    }
}
