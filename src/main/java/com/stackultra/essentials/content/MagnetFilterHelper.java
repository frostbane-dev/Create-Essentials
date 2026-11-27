package com.stackultra.essentials.content;

import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * Shared Create filter-slot evaluation for magnets, magnetic backpacks, and placed magnetic backpack blocks.
 */
public final class MagnetFilterHelper {

    private MagnetFilterHelper() {
    }

    public static boolean matchesFilter(Level level, List<ItemStack> filters, ItemStack candidate, ItemStack ownerStack) {
        boolean blacklist = ownerStack != null && ownerStack.getOrDefault(ExtraDataComponents.BLACKLIST, false);
        return matchesFilter(level, filters, candidate, blacklist);
    }

    public static boolean matchesFilter(Level level, List<ItemStack> filters, ItemStack candidate, boolean blacklist) {
        if (filters == null || filters.isEmpty())
            return true;

        boolean anyFilter = false;
        boolean matches = false;
        for (ItemStack filter : filters) {
            if (filter == null || filter.isEmpty())
                continue;
            anyFilter = true;
            if (FilterItemStack.of(filter).test(level, candidate)) {
                matches = true;
                break;
            }
        }
        if (!anyFilter)
            return true;
        return blacklist ? !matches : matches;
    }

    public static boolean matchesFilter(Level level, ItemStackHandler filterInventory, ItemStack candidate, boolean blacklist) {
        if (filterInventory == null)
            return true;

        boolean anyFilter = false;
        boolean matches = false;
        for (int i = 0; i < filterInventory.getSlots(); i++) {
            ItemStack filter = filterInventory.getStackInSlot(i);
            if (filter.isEmpty())
                continue;
            anyFilter = true;
            if (FilterItemStack.of(filter).test(level, candidate)) {
                matches = true;
                break;
            }
        }
        if (!anyFilter)
            return true;
        return blacklist ? !matches : matches;
    }
}
