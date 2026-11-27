package com.stackultra.essentials.content;

import com.simibubi.create.AllEnchantments;
import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared item-stack utilities for chargeable magnetic equipment
 * ({@code RotationalMagnetItem} and {@code MagneticBackpackItem}):
 * Capacity enchantment level, charge clamping, durability bar width,
 * filter slot serialization, and Create Goggles tooltip lines.
 */
public final class MagneticEquipmentHelper {

    public static final int BAR_COLOR = 0xFFFFFF;
    public static final int FILTER_SLOTS = 3;

    private MagneticEquipmentHelper() {
    }

    public static boolean supportsCapacity(Holder<Enchantment> enchantment) {
        return enchantment.is(AllEnchantments.CAPACITY);
    }

    public static int getCapacityLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getTagEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(AllEnchantments.CAPACITY))
                return enchantments.getLevel(enchantment);
        }
        return 0;
    }

    public static int getCharge(ItemStack stack, int maxCharge) {
        return Math.min(stack.getOrDefault(AllDataComponents.CHARGE, 0), maxCharge);
    }

    public static int getBarWidth(int charge, int maxCharge) {
        if (maxCharge <= 0)
            return 0;
        return Math.round(13.0F * Mth.clamp(charge / (float) maxCharge, 0, 1));
    }

    public static void appendGogglesChargeTooltip(ItemStack stack, int charge, int maxCharge, List<Component> tooltip) {
        if (!GogglesHelper.isViewerWearingGoggles())
            return;
        boolean enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
        boolean charging = stack.getOrDefault(AllDataComponents.CHARGING, false);

        tooltip.add(ChargeDisplay.statusLine(enabled, charging));
        tooltip.add(ChargeDisplay.charge(charge, maxCharge));
    }

    public static NonNullList<ItemStack> getFilterSlots(ItemStack stack,
                                                        DataComponentType<ItemContainerContents> component,
                                                        int slotCount) {
        ItemContainerContents contents = stack.getOrDefault(component, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> slots = NonNullList.withSize(slotCount, ItemStack.EMPTY);
        contents.copyInto(slots);
        return slots;
    }

    public static List<ItemStack> getFilters(ItemStack stack,
                                             DataComponentType<ItemContainerContents> component,
                                             int slotCount) {
        NonNullList<ItemStack> slots = getFilterSlots(stack, component, slotCount);
        List<ItemStack> filters = new ArrayList<>(slotCount);
        for (ItemStack entry : slots) {
            if (!entry.isEmpty())
                filters.add(entry.copy());
        }
        return filters;
    }

    public static void setFilters(ItemStack stack,
                                  DataComponentType<ItemContainerContents> component,
                                  int slotCount,
                                  List<ItemStack> filters) {
        List<ItemStack> slots = new ArrayList<>(slotCount);
        for (int i = 0; i < slotCount; i++)
            slots.add(i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY);
        stack.set(component, ItemContainerContents.fromItems(slots));
    }
}
