package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Shapeless crafting recipe allowing any Mechanical, Netherite Mechanical, or Magnetic Backpack
 * to be dyed with any of the 16 vanilla/tagged Minecraft dyes while preserving 100% of stored
 * inventory, toolbox compartments, filters, charge, sort mode, custom name, and enchantments.
 */
public class BackpackDyeRecipe extends CustomRecipe {

    public BackpackDyeRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        ItemStack backpack = ItemStack.EMPTY;
        DyeColor dyeColor = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty())
                continue;

            if (isDyeableBackpack(stack)) {
                if (!backpack.isEmpty())
                    return false;
                backpack = stack;
            } else {
                DyeColor found = DyeColor.getColor(stack);
                if (found == null || dyeColor != null)
                    return false;
                dyeColor = found;
            }
        }

        if (backpack.isEmpty() || dyeColor == null)
            return false;

        // Avoid redundant no-op crafting when the backpack is already dyed this exact color
        return BackpackColorHelper.resolveDyeColor(backpack) != dyeColor;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack backpack = ItemStack.EMPTY;
        DyeColor dyeColor = null;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty())
                continue;

            if (isDyeableBackpack(stack)) {
                if (!backpack.isEmpty())
                    return ItemStack.EMPTY;
                backpack = stack;
            } else {
                DyeColor found = DyeColor.getColor(stack);
                if (found == null || dyeColor != null)
                    return ItemStack.EMPTY;
                dyeColor = found;
            }
        }

        if (backpack.isEmpty() || dyeColor == null)
            return ItemStack.EMPTY;

        ItemStack result = backpack.copyWithCount(1);
        BackpackColorHelper.setDyeColor(result, dyeColor);
        return result;
    }

    public static boolean isDyeableBackpack(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        return stack.getItem() instanceof MechanicalBackpackItem
                || stack.getItem() instanceof MagneticBackpackItem;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AllRecipeSerializers.BACKPACK_DYE.get();
    }
}
