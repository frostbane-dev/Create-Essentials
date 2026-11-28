package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Centralized tier and 16-color {@link DyeColor} resolution for Mechanical, Netherite,
 * and Magnetic Backpacks across item models, placed blocks, breaking particles, tooltips,
 * Curios rendering, and dyeing recipes.
 */
public final class BackpackColorHelper {

    public static final int NO_CUSTOM_RGB = -1;

    private static final int DEFAULT_LEATHER_RGB = 0x7C5239;
    private static final Map<DyeColor, Integer> PARTICLE_LEATHER_RGB = new EnumMap<>(DyeColor.class);

    static {
        PARTICLE_LEATHER_RGB.put(DyeColor.WHITE, 0xBAB5AC);
        PARTICLE_LEATHER_RGB.put(DyeColor.ORANGE, 0xA85C30);
        PARTICLE_LEATHER_RGB.put(DyeColor.MAGENTA, 0x924874);
        PARTICLE_LEATHER_RGB.put(DyeColor.LIGHT_BLUE, 0x688AA6);
        PARTICLE_LEATHER_RGB.put(DyeColor.YELLOW, 0xAC8E3E);
        PARTICLE_LEATHER_RGB.put(DyeColor.LIME, 0x709042);
        PARTICLE_LEATHER_RGB.put(DyeColor.PINK, 0xAE727E);
        PARTICLE_LEATHER_RGB.put(DyeColor.GRAY, 0x585A5D);
        PARTICLE_LEATHER_RGB.put(DyeColor.LIGHT_GRAY, 0x8A8A8A);
        PARTICLE_LEATHER_RGB.put(DyeColor.CYAN, 0x407A80);
        PARTICLE_LEATHER_RGB.put(DyeColor.PURPLE, 0x704C8A);
        PARTICLE_LEATHER_RGB.put(DyeColor.BLUE, 0x46588E);
        PARTICLE_LEATHER_RGB.put(DyeColor.BROWN, DEFAULT_LEATHER_RGB);
        PARTICLE_LEATHER_RGB.put(DyeColor.GREEN, 0x487044);
        PARTICLE_LEATHER_RGB.put(DyeColor.RED, 0x923C38);
        PARTICLE_LEATHER_RGB.put(DyeColor.BLACK, 0x38383E);
    }

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

    @Nullable
    public static DyeColor resolveBlockColor(@Nullable BlockState state) {
        if (state == null)
            return null;
        if (state.hasProperty(MechanicalBackpackBlock.COLOR))
            return state.getValue(MechanicalBackpackBlock.COLOR);
        if (state.hasProperty(MagneticBackpackBlock.COLOR))
            return state.getValue(MagneticBackpackBlock.COLOR);
        return null;
    }

    @Nullable
    public static DyeColor resolveBlockColor(@Nullable BlockState state, @Nullable BlockEntity blockEntity) {
        if (blockEntity instanceof MechanicalBackpackBlockEntity mech) {
            DyeColor beColor = mech.getBackpackColor();
            if (beColor != null)
                return beColor;
        } else if (blockEntity instanceof MagneticBackpackBlockEntity mag) {
            DyeColor beColor = mag.getBackpackColor();
            if (beColor != null)
                return beColor;
        }
        return resolveBlockColor(state);
    }

    /**
     * Ensures a placed backpack {@link BlockState} carries the resolved {@link DyeColor}
     * before dispatching block breaking particles ({@code levelEvent 2001}), so even
     * NBT-edited or legacy blocks spawn particles matching their dyed leather texture.
     */
    public static BlockState withResolvedBlockColor(BlockState state, @Nullable BlockEntity blockEntity) {
        DyeColor resolved = resolveBlockColor(state, blockEntity);
        if (resolved == null)
            return state;
        if (state.hasProperty(MechanicalBackpackBlock.COLOR)
                && state.getValue(MechanicalBackpackBlock.COLOR) != resolved) {
            return state.setValue(MechanicalBackpackBlock.COLOR, resolved);
        }
        if (state.hasProperty(MagneticBackpackBlock.COLOR)
                && state.getValue(MagneticBackpackBlock.COLOR) != resolved) {
            return state.setValue(MagneticBackpackBlock.COLOR, resolved);
        }
        return state;
    }

    public static ResourceLocation particleTexture(@Nullable DyeColor color) {
        if (color == null) {
            return ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "block/backpack_leather_particle");
        }
        return ResourceLocation.fromNamespaceAndPath(
                CreateEssentials.MOD_ID,
                "block/backpack/backpack_leather_particle_" + color.getName());
    }

    public static int particleLeatherRgb(@Nullable DyeColor color) {
        if (color == null)
            return DEFAULT_LEATHER_RGB;
        return PARTICLE_LEATHER_RGB.getOrDefault(color, DEFAULT_LEATHER_RGB);
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
