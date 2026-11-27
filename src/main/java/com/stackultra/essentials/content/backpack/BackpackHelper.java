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

    /**
     * Truncates a GUI title string to fit strictly within {@code maxWidth} pixels, appending
     * {@code "..."} without trailing spaces when clipping is required.
     */
    public static String truncateTitle(String rawTitle,
                                       int maxWidth,
                                       java.util.function.ToIntFunction<String> widthFn,
                                       java.util.function.BiFunction<String, Integer, String> substrFn) {
        if (rawTitle == null || rawTitle.isEmpty() || maxWidth <= 0)
            return "";
        if (widthFn.applyAsInt(rawTitle) <= maxWidth)
            return rawTitle;
        int ellipsisWidth = widthFn.applyAsInt("...");
        if (ellipsisWidth >= maxWidth)
            return "...";
        String clipped = substrFn.apply(rawTitle, maxWidth - ellipsisWidth).stripTrailing();
        return clipped + "...";
    }
}
