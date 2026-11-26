package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record BackpackTooltipData(
        Tier tier,
        @Nullable DyeColor dyeColor,
        int customRgb,
        List<ItemStack> slots,
        int occupiedSlots
) implements TooltipComponent {

    public enum Tier {
        MECHANICAL,
        NETHERITE,
        MAGNETIC
    }

    public static final int SLOT_COUNT = BackpackInventory.SLOTS;
    public static final int NO_CUSTOM_RGB = -1;

    @Nullable
    public static Tier resolveTier(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
        if (stack.getItem() instanceof MagneticBackpackItem)
            return Tier.MAGNETIC;
        if (stack.getItem() instanceof NetheriteBackpackItem)
            return Tier.NETHERITE;
        if (stack.getItem() instanceof MechanicalBackpackItem)
            return Tier.MECHANICAL;
        return null;
    }

    @Nullable
    public static DyeColor resolveDyeColor(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return null;
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

    public static boolean isStorageEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return true;
        ItemContainerContents contents = stack.get(AllDataComponents.BACKPACK_INVENTORY);
        if (contents == null)
            return true;
        NonNullList<ItemStack> raw = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        contents.copyInto(raw);
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack entry = raw.get(i);
            if (entry != null && !entry.isEmpty())
                return false;
        }
        return true;
    }

    public static void appendEmptyHint(ItemStack stack, List<Component> tooltip) {
        if (isStorageEmpty(stack)) {
            tooltip.add(Component.translatable("createessentials.tooltip.backpack_empty")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static Optional<TooltipComponent> fromStack(ItemStack stack, Tier tier) {
        if (stack == null || stack.isEmpty() || tier == null)
            return Optional.empty();

        ItemContainerContents contents = stack.get(AllDataComponents.BACKPACK_INVENTORY);
        if (contents == null)
            return Optional.empty();

        NonNullList<ItemStack> raw = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        contents.copyInto(raw);

        List<ItemStack> copied = new ArrayList<>(SLOT_COUNT);
        int occupied = 0;
        for (int i = 0; i < SLOT_COUNT; i++) {
            ItemStack entry = raw.get(i);
            if (entry == null || entry.isEmpty()) {
                copied.add(ItemStack.EMPTY);
            } else {
                copied.add(entry.copy());
                occupied++;
            }
        }

        if (occupied == 0)
            return Optional.empty();

        return Optional.of(new BackpackTooltipData(
                tier,
                resolveDyeColor(stack),
                resolveCustomRgb(stack),
                Collections.unmodifiableList(copied),
                occupied));
    }
}
