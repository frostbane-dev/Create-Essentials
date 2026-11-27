package com.stackultra.essentials.content;

import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Resolves the target slot index for syncing a mutated {@link ItemStack} back into a player's
 * inventory or Curios slots.
 * <p>
 * Prefers exact reference identity ({@code ==}) so carrying multiple identical items stays
 * unambiguous, then falls back cleanly to a unique component-equal stack or unique same-item
 * stack when a copy was passed, and refuses to guess when multiple candidate slots are ambiguous.
 */
public final class StackSyncHelper {

    private StackSyncHelper() {
    }

    public static int findMatchingIndex(List<ItemStack> slots, ItemStack target) {
        if (target == null || target.isEmpty() || slots == null || slots.isEmpty())
            return -1;

        // Pass 1: exact reference match (live stack from inventory / Curios)
        for (int i = 0; i < slots.size(); i++) {
            if (slots.get(i) == target)
                return i;
        }

        // Pass 2: unique identical item + components match
        int exactIndex = -1;
        int exactCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack existing = slots.get(i);
            if (existing != null && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, target)) {
                exactIndex = i;
                exactCount++;
            }
        }
        if (exactCount == 1)
            return exactIndex;

        // Pass 3: unique same-item match (handles copies whose components were just mutated)
        int itemIndex = -1;
        int itemCount = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack existing = slots.get(i);
            if (existing != null && !existing.isEmpty() && existing.getItem() == target.getItem()) {
                itemIndex = i;
                itemCount++;
            }
        }
        return itemCount == 1 ? itemIndex : -1;
    }
}
