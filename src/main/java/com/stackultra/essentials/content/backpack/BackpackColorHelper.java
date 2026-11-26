package com.stackultra.essentials.content.backpack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import javax.annotation.Nullable;

/**
 * Centralized tier and color resolution for Mechanical, Netherite, Magnetic,
 * and future 16-color DyeColor backpack variants.
 */
public final class BackpackColorHelper {

    public static final int NO_CUSTOM_RGB = -1;

    private BackpackColorHelper() {
    }

    @Nullable
    public static BackpackTooltipData.Tier resolveTier(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
        if (stack.getItem() instanceof MagneticBackpackItem)
            return BackpackTooltipData.Tier.MAGNETIC;
        if (stack.getItem() instanceof NetheriteBackpackItem)
            return BackpackTooltipData.Tier.NETHERITE;
        if (stack.getItem() instanceof MechanicalBackpackItem)
            return BackpackTooltipData.Tier.MECHANICAL;
        return null;
    }

    @Nullable
    public static DyeColor resolveDyeColor(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
        if (stack.getItem() instanceof ColoredBackpack colored) {
            DyeColor explicit = colored.getBackpackColor();
            if (explicit != null)
                return explicit;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String path = id.getPath();
        for (DyeColor color : DyeColor.values()) {
            String name = color.getName();
            if (path.startsWith(name + "_") || path.endsWith("_" + name) || path.contains("_" + name + "_"))
                return color;
        }
        return null;
    }

    public static int resolveCustomRgb(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return NO_CUSTOM_RGB;
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed != null ? (dyed.rgb() & 0x00FFFFFF) : NO_CUSTOM_RGB;
    }

    /**
     * Optional interface future 16-color backpack items can implement directly
     * without changing any tooltip, GUI, or toolbox code.
     */
    public interface ColoredBackpack {
        @Nullable
        DyeColor getBackpackColor();
    }
}
