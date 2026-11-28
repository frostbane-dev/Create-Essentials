package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import javax.annotation.Nullable;

/**
 * Centralized tier and 16-color {@link DyeColor} resolution for Mechanical, Netherite,
 * and Magnetic Backpacks across item models, placed blocks, tooltips, Curios rendering,
 * and dyeing recipes.
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
        DyeColor componentColor = stack.get(ExtraDataComponents.BACKPACK_COLOR);
        if (componentColor != null)
            return componentColor;
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

    public static void setDyeColor(ItemStack stack, @Nullable DyeColor color) {
        if (stack == null || stack.isEmpty())
            return;
        if (color == null) {
            stack.remove(ExtraDataComponents.BACKPACK_COLOR);
        } else {
            stack.set(ExtraDataComponents.BACKPACK_COLOR, color);
            stack.remove(DataComponents.DYED_COLOR);
        }
    }

    /**
     * Returns {@code 0.0F} for unpainted backpacks (default vintage brown base model)
     * or {@code 1.0F..16.0F} matching ascending {@code createessentials:color} item model overrides.
     */
    public static float getModelPropertyValue(ItemStack stack) {
        DyeColor color = resolveDyeColor(stack);
        return color == null ? 0.0F : (float) (color.getId() + 1);
    }

    public static Component coloredName(ItemStack stack, Component baseName) {
        return coloredName(resolveDyeColor(stack), baseName);
    }

    public static Component coloredName(@Nullable DyeColor color, Component baseName) {
        if (color == null)
            return baseName;
        return Component.translatable("createessentials.backpack.colored",
                Component.translatable("color.minecraft." + color.getName()), baseName);
    }

    public static int resolveCustomRgb(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return NO_CUSTOM_RGB;
        DyedItemColor dyed = stack.get(DataComponents.DYED_COLOR);
        return dyed != null ? (dyed.rgb() & 0x00FFFFFF) : NO_CUSTOM_RGB;
    }

    /**
     * Optional interface custom colored backpack items can implement directly
     * without changing any tooltip, GUI, or toolbox code.
     */
    public interface ColoredBackpack {
        @Nullable
        DyeColor getBackpackColor();
    }
}
