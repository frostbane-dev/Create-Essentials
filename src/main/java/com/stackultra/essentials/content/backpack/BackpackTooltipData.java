package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
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
        int occupiedSlots,
        List<ToolboxSlotEntry> toolboxSlots,
        boolean hasToolboxData
) implements TooltipComponent {

    public enum Tier {
        MECHANICAL,
        NETHERITE,
        MAGNETIC
    }

    public record ToolboxSlotEntry(ItemStack displayStack, int count) {
    }

    public static final int SLOT_COUNT = BackpackInventory.SLOTS;
    public static final int NO_CUSTOM_RGB = BackpackColorHelper.NO_CUSTOM_RGB;

    @Nullable
    public static Tier resolveTier(ItemStack stack) {
        return BackpackColorHelper.resolveTier(stack);
    }

    @Nullable
    public static DyeColor resolveDyeColor(ItemStack stack) {
        return BackpackColorHelper.resolveDyeColor(stack);
    }

    public static int resolveCustomRgb(ItemStack stack) {
        return BackpackColorHelper.resolveCustomRgb(stack);
    }

    public static boolean isStorageEmpty(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return true;
        ItemContainerContents contents = stack.get(AllDataComponents.BACKPACK_INVENTORY);
        if (contents != null) {
            NonNullList<ItemStack> raw = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
            contents.copyInto(raw);
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack entry = raw.get(i);
                if (entry != null && !entry.isEmpty())
                    return false;
            }
        }
        ItemContainerContents tbContents = stack.get(ExtraDataComponents.TOOLBOX_CONTENTS);
        if (tbContents != null) {
            NonNullList<ItemStack> rawTb = NonNullList.withSize(PortableToolboxInventory.BACKING_SLOTS, ItemStack.EMPTY);
            tbContents.copyInto(rawTb);
            for (ItemStack entry : rawTb) {
                if (entry != null && !entry.isEmpty())
                    return false;
            }
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

        // Main 27 storage slots (strictly excluding Magnetic Backpack's 3 filter slots)
        List<ItemStack> copied = new ArrayList<>(SLOT_COUNT);
        int occupied = 0;
        ItemContainerContents contents = stack.get(AllDataComponents.BACKPACK_INVENTORY);
        if (contents != null) {
            NonNullList<ItemStack> raw = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
            contents.copyInto(raw);
            for (int i = 0; i < SLOT_COUNT; i++) {
                ItemStack entry = raw.get(i);
                if (entry == null || entry.isEmpty()) {
                    copied.add(ItemStack.EMPTY);
                } else {
                    copied.add(entry.copy());
                    occupied++;
                }
            }
        } else {
            for (int i = 0; i < SLOT_COUNT; i++)
                copied.add(ItemStack.EMPTY);
        }

        // Portable 8-compartment toolbox snapshot (physically stored items only)
        List<ToolboxSlotEntry> toolboxEntries = new ArrayList<>(PortableToolboxInventory.COMPARTMENTS);
        boolean hasToolbox = false;
        ItemContainerContents tbContents = stack.get(ExtraDataComponents.TOOLBOX_CONTENTS);
        NonNullList<ItemStack> backing = NonNullList.withSize(PortableToolboxInventory.BACKING_SLOTS, ItemStack.EMPTY);
        if (tbContents != null)
            tbContents.copyInto(backing);

        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
            int total = 0;
            ItemStack sample = ItemStack.EMPTY;
            int base = c * PortableToolboxInventory.STACKS_PER_COMPARTMENT;
            for (int s = 0; s < PortableToolboxInventory.STACKS_PER_COMPARTMENT; s++) {
                ItemStack sub = backing.get(base + s);
                if (sub != null && !sub.isEmpty()) {
                    total += sub.getCount();
                    if (sample.isEmpty())
                        sample = sub.copyWithCount(1);
                }
            }

            if (total > 0 && !sample.isEmpty()) {
                toolboxEntries.add(new ToolboxSlotEntry(sample, total));
                hasToolbox = true;
            } else {
                toolboxEntries.add(new ToolboxSlotEntry(ItemStack.EMPTY, 0));
            }
        }

        if (occupied == 0 && !hasToolbox)
            return Optional.empty();

        return Optional.of(new BackpackTooltipData(
                tier,
                resolveDyeColor(stack),
                resolveCustomRgb(stack),
                Collections.unmodifiableList(copied),
                occupied,
                Collections.unmodifiableList(toolboxEntries),
                hasToolbox));
    }
}
