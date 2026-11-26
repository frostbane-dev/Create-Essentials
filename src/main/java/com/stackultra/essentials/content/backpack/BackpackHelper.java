package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllTags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.neoforged.neoforge.capabilities.Capabilities;

public final class BackpackHelper {

    private BackpackHelper() {
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof MechanicalBackpackItem
                || stack.getItem() instanceof MagneticBackpackItem);
    }

    public static boolean isNestedContainer(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        if (isBackpack(stack))
            return true;
        if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock)
            return true;
        if (AllTags.AllItemTags.TOOLBOXES.matches(stack))
            return true;
        if (stack.getItem() instanceof BundleItem)
            return true;
        if (stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS))
            return true;
        return stack.getCapability(Capabilities.ItemHandler.ITEM) != null;
    }
}
