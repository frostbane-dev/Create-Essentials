package com.stackultra.essentials.content.backpack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class BackpackInventoryOps {

    private BackpackInventoryOps() {
    }

    /**
     * Green Arrow: Transfers matching items from player inventory into matching toolbox compartments
     * (including empty reserved ghost compartments) FIRST (respecting anti-nesting and strict per-item
     * caps: 1 for unstackables, 4 * maxStackSize for stackables), then into matching/empty slots among
     * the 27 main storage slots.
     */
    public static boolean transferMatchingToStorage(AbstractContainerMenu menu,
                                                    @Nullable PortableToolboxInventory toolbox,
                                                    int storageStart, int storageEnd,
                                                    int playerStart, int playerEnd,
                                                    Player player,
                                                    @Nullable ItemStack openBackpackStack) {
        List<ItemStack> storageSnapshot = snapshotNonEmpty(menu, storageStart, storageEnd);
        boolean hasToolboxTargets = toolbox != null && toolbox.hasAnyContentsOrFilters();
        if (storageSnapshot.isEmpty() && !hasToolboxTargets)
            return false;

        boolean movedAny = false;
        for (int i = playerStart; i < playerEnd; i++) {
            Slot sourceSlot = menu.getSlot(i);
            ItemStack source = sourceSlot.getItem();
            if (source.isEmpty() || !sourceSlot.mayPickup(player))
                continue;
            if (openBackpackStack != null && source == openBackpackStack)
                continue;
            if (BackpackHelper.isNestedContainer(source))
                continue;

            // Priority 1: matching toolbox compartments (populated or empty ghost-reserved)
            if (hasToolboxTargets) {
                int beforeToolbox = source.getCount();
                ItemStack remaining = toolbox.insertIntoMatchingCompartment(source, false);
                if (remaining.getCount() != beforeToolbox || remaining.isEmpty()) {
                    sourceSlot.set(remaining);
                    sourceSlot.setChanged();
                    movedAny = true;
                }
            }

            // Priority 2: 27 main storage slots (for items matching main storage snapshot)
            ItemStack afterToolbox = sourceSlot.getItem();
            if (!afterToolbox.isEmpty() && !storageSnapshot.isEmpty()
                    && matchesAny(afterToolbox, storageSnapshot)) {
                if (moveStackToRange(menu, sourceSlot, storageStart, storageEnd))
                    movedAny = true;
            }
        }
        return movedAny;
    }

    /**
     * Red Arrow: Transfers matching items from BOTH the 8 toolbox compartments (splitting > maxStackSize
     * stacks cleanly into valid <= maxStackSize player stacks without losing remainders, preserving
     * reserved ghost filters when emptied) AND the 27 main storage slots into the player's inventory.
     */
    public static boolean transferMatchingToInventory(AbstractContainerMenu menu,
                                                      @Nullable PortableToolboxInventory toolbox,
                                                      int storageStart, int storageEnd,
                                                      int playerStart, int playerEnd,
                                                      Player player) {
        List<ItemStack> playerSnapshot = snapshotNonEmpty(menu, playerStart, playerEnd);
        if (playerSnapshot.isEmpty())
            return false;

        boolean movedAny = false;

        // Step 1: matching items from the 8 toolbox compartments
        if (toolbox != null) {
            for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
                if (toolbox.getTotalCount(c) <= 0)
                    continue;
                ItemStack template = toolbox.getTemplate(c);
                if (template.isEmpty() || !matchesAny(template, playerSnapshot))
                    continue;

                int maxStack = Math.max(1, template.getMaxStackSize());
                while (toolbox.getTotalCount(c) > 0) {
                    ItemStack extracted = toolbox.extractFromCompartment(c, maxStack, false);
                    if (extracted.isEmpty())
                        break;
                    ItemStack remainder = insertIntoPlayerSlots(menu, extracted, playerStart, playerEnd);
                    if (remainder.getCount() != extracted.getCount() || remainder.isEmpty()) {
                        movedAny = true;
                    }
                    if (!remainder.isEmpty()) {
                        // Player inventory full for this item -> restore untransferred remainder safely
                        toolbox.insertIntoCompartment(c, remainder, false);
                        break;
                    }
                }
            }
        }

        // Step 2: matching items from the 27 main storage slots
        for (int i = storageStart; i < storageEnd; i++) {
            Slot sourceSlot = menu.getSlot(i);
            ItemStack source = sourceSlot.getItem();
            if (source.isEmpty() || !sourceSlot.mayPickup(player))
                continue;
            if (!matchesAny(source, playerSnapshot))
                continue;

            if (moveStackToRange(menu, sourceSlot, playerStart, playerEnd))
                movedAny = true;
        }
        return movedAny;
    }

    /**
     * Consolidates and sorts both the 27 main storage slots and the 8 toolbox compartments
     * cleanly within their respective capacity rules, never touching Magnetic Backpack's 3 filter slots.
     */
    public static boolean sortStorage(AbstractContainerMenu menu,
                                      @Nullable PortableToolboxInventory toolbox,
                                      int storageStart, int storageEnd,
                                      SortMode mode) {
        boolean mainChanged = sortMainSlots(menu, storageStart, storageEnd, mode);
        boolean toolboxChanged = toolbox != null && toolbox.sortCompartments(mode);
        return mainChanged || toolboxChanged;
    }

    private static boolean sortMainSlots(AbstractContainerMenu menu,
                                         int storageStart, int storageEnd,
                                         SortMode mode) {
        int slotCount = storageEnd - storageStart;
        if (slotCount <= 0)
            return false;

        List<ItemStack> extracted = new ArrayList<>(slotCount);
        int totalBefore = 0;
        for (int i = storageStart; i < storageEnd; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                extracted.add(stack.copy());
                totalBefore += stack.getCount();
            }
        }
        if (extracted.isEmpty())
            return false;

        List<ItemStack> consolidated = new ArrayList<>(extracted.size());
        for (ItemStack raw : extracted) {
            ItemStack remaining = raw.copy();
            if (remaining.isStackable()) {
                for (ItemStack existing : consolidated) {
                    int maxStack = existing.getMaxStackSize();
                    if (existing.getCount() < maxStack
                            && ItemStack.isSameItemSameComponents(existing, remaining)) {
                        int move = Math.min(maxStack - existing.getCount(), remaining.getCount());
                        existing.grow(move);
                        remaining.shrink(move);
                        if (remaining.isEmpty())
                            break;
                    }
                }
            }
            while (!remaining.isEmpty()) {
                int maxStack = Math.max(1, remaining.getMaxStackSize());
                int chunk = Math.min(maxStack, remaining.getCount());
                consolidated.add(remaining.copyWithCount(chunk));
                remaining.shrink(chunk);
            }
        }

        int totalAfter = 0;
        for (ItemStack stack : consolidated)
            totalAfter += stack.getCount();

        // Safety invariant: never drop, truncate, or duplicate items
        if (totalAfter != totalBefore || consolidated.size() > slotCount)
            return false;

        consolidated.sort(comparatorFor(mode));

        boolean changed = false;
        for (int i = 0; i < slotCount; i++) {
            Slot slot = menu.getSlot(storageStart + i);
            ItemStack target = i < consolidated.size() ? consolidated.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(slot.getItem(), target)) {
                slot.set(target);
                slot.setChanged();
                changed = true;
            }
        }
        return changed;
    }

    private static List<ItemStack> snapshotNonEmpty(AbstractContainerMenu menu, int start, int end) {
        List<ItemStack> snapshot = new ArrayList<>();
        for (int i = start; i < end; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty())
                snapshot.add(stack.copy());
        }
        return snapshot;
    }

    private static boolean matchesAny(ItemStack candidate, List<ItemStack> references) {
        for (ItemStack ref : references) {
            if (ItemStack.isSameItemSameComponents(candidate, ref))
                return true;
            if (candidate.isStackable() && ref.isStackable() && ItemStack.isSameItem(candidate, ref))
                return true;
        }
        return false;
    }

    private static boolean moveStackToRange(AbstractContainerMenu menu, Slot sourceSlot,
                                            int destStart, int destEnd) {
        ItemStack source = sourceSlot.getItem();
        if (source.isEmpty())
            return false;

        ItemStack remaining = source.copy();
        int originalCount = remaining.getCount();

        // Pass 1: merge into existing non-full matching stacks (identical components)
        if (remaining.isStackable()) {
            for (int i = destStart; i < destEnd && !remaining.isEmpty(); i++) {
                Slot destSlot = menu.getSlot(i);
                ItemStack dest = destSlot.getItem();
                if (dest.isEmpty() || !ItemStack.isSameItemSameComponents(dest, remaining))
                    continue;
                int limit = Math.min(destSlot.getMaxStackSize(remaining), remaining.getMaxStackSize());
                int space = limit - dest.getCount();
                if (space <= 0)
                    continue;
                int move = Math.min(space, remaining.getCount());
                destSlot.set(dest.copyWithCount(dest.getCount() + move));
                destSlot.setChanged();
                remaining.shrink(move);
            }
        }

        // Pass 2: place remainder into empty destination slots
        for (int i = destStart; i < destEnd && !remaining.isEmpty(); i++) {
            Slot destSlot = menu.getSlot(i);
            if (destSlot.hasItem() || !destSlot.mayPlace(remaining))
                continue;
            int limit = Math.min(destSlot.getMaxStackSize(remaining), remaining.getMaxStackSize());
            int move = Math.min(limit, remaining.getCount());
            if (move <= 0)
                continue;
            destSlot.set(remaining.copyWithCount(move));
            destSlot.setChanged();
            remaining.shrink(move);
        }

        if (remaining.getCount() != originalCount) {
            sourceSlot.set(remaining.isEmpty() ? ItemStack.EMPTY : remaining);
            sourceSlot.setChanged();
            return true;
        }
        return false;
    }

    private static Comparator<ItemStack> comparatorFor(SortMode mode) {
        return switch (mode) {
            case NAME -> Comparator
                    .comparing(BackpackInventoryOps::displayName)
                    .thenComparing(s -> itemId(s).toString())
                    .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
                    .thenComparing(s -> s.getComponents().toString());
            case MOD -> Comparator
                    .comparing((ItemStack s) -> itemId(s).getNamespace())
                    .thenComparing(BackpackInventoryOps::displayName)
                    .thenComparing(s -> itemId(s).getPath())
                    .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed())
                    .thenComparing(s -> s.getComponents().toString());
            case COUNT -> Comparator
                    .comparingInt(ItemStack::getCount).reversed()
                    .thenComparing(s -> itemId(s).getNamespace())
                    .thenComparing(BackpackInventoryOps::displayName)
                    .thenComparing(s -> itemId(s).getPath())
                    .thenComparing(s -> s.getComponents().toString());
        };
    }

    private static String displayName(ItemStack stack) {
        return stack.getHoverName().getString().toLowerCase(Locale.ROOT);
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem());
    }

    /**
     * Handles all server-authoritative toolbox compartment interactions with strict count preservation
     * and Create-style reserved ghost filter handling:
     * <ul>
     *   <li>Extracting the last item preserves the reserved ghost preview.</li>
     *   <li>Clicking or shift-clicking an empty reserved slot with an empty cursor clears the ghost preview.</li>
     *   <li>Inserting a different item into an empty reserved slot safely replaces the ghost preview.</li>
     *   <li>Swapping a populated slot only succeeds when total count fits into a single valid stack.</li>
     * </ul>
     */
    public static boolean handleToolboxSlotClick(AbstractContainerMenu menu,
                                                 PortableToolboxInventory toolbox,
                                                 int compartment,
                                                 int button,
                                                 ClickType clickType,
                                                 Player player,
                                                 int playerSlotStart,
                                                 int playerSlotEnd,
                                                 @Nullable ItemStack openBackpackStack) {
        if (compartment < 0 || compartment >= PortableToolboxInventory.COMPARTMENTS)
            return false;

        switch (clickType) {
            case QUICK_MOVE -> {
                int total = toolbox.getTotalCount(compartment);
                if (total <= 0) {
                    // Explicit ghost clear on empty reserved compartment when cursor is empty
                    if (menu.getCarried().isEmpty() && toolbox.isReservedEmpty(compartment))
                        return toolbox.clearFilter(compartment);
                    return false;
                }
                ItemStack template = toolbox.getTemplate(compartment);
                int maxExtract = template.isEmpty() ? 64 : Math.max(1, template.getMaxStackSize());
                ItemStack extracted = toolbox.extractFromCompartment(compartment, maxExtract, false);
                if (extracted.isEmpty())
                    return false;
                ItemStack remaining = insertIntoPlayerSlots(menu, extracted, playerSlotStart, playerSlotEnd);
                if (!remaining.isEmpty())
                    toolbox.insertIntoCompartment(compartment, remaining, false);
                return remaining.getCount() != extracted.getCount() || remaining.isEmpty();
            }
            case PICKUP -> {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty()) {
                    if (BackpackHelper.isNestedContainer(carried))
                        return false;
                    if (openBackpackStack != null && carried == openBackpackStack)
                        return false;

                    if (toolbox.canAccept(compartment, carried)) {
                        if (button == 1) {
                            ItemStack one = carried.copyWithCount(1);
                            ItemStack rem = toolbox.insertIntoCompartment(compartment, one, false);
                            if (rem.isEmpty()) {
                                ItemStack nextCarried = carried.copy();
                                nextCarried.shrink(1);
                                menu.setCarried(nextCarried.isEmpty() ? ItemStack.EMPTY : nextCarried);
                                return true;
                            }
                            return false;
                        } else {
                            ItemStack rem = toolbox.insertIntoCompartment(compartment, carried, false);
                            if (rem.getCount() != carried.getCount() || rem.isEmpty()) {
                                menu.setCarried(rem);
                                return true;
                            }
                            return false;
                        }
                    }

                    // Empty reserved compartment with a different ghost filter -> safely replace filter & insert
                    if (toolbox.isReservedEmpty(compartment)) {
                        if (button == 1) {
                            ItemStack one = carried.copyWithCount(1);
                            ItemStack rem = toolbox.replaceEmptyReservedAndInsert(compartment, one);
                            if (rem.isEmpty()) {
                                ItemStack nextCarried = carried.copy();
                                nextCarried.shrink(1);
                                menu.setCarried(nextCarried.isEmpty() ? ItemStack.EMPTY : nextCarried);
                                return true;
                            }
                            return false;
                        } else if (button == 0) {
                            ItemStack rem = toolbox.replaceEmptyReservedAndInsert(compartment, carried);
                            if (rem.getCount() != carried.getCount() || rem.isEmpty()) {
                                menu.setCarried(rem);
                                return true;
                            }
                            return false;
                        }
                        return false;
                    }

                    // LMB with a different item onto a populated compartment whose total <= maxStackSize: swap cleanly
                    if (button == 0) {
                        int existingTotal = toolbox.getTotalCount(compartment);
                        ItemStack existingTemplate = toolbox.getTemplate(compartment);
                        if (existingTotal > 0 && !existingTemplate.isEmpty()
                                && existingTotal <= Math.max(1, existingTemplate.getMaxStackSize())
                                && carried.getCount() <= PortableToolboxInventory.maxCapacityForItem(carried)
                                && !ItemStack.isSameItemSameComponents(existingTemplate, carried)) {
                            ItemStack extracted = toolbox.extractFromCompartment(compartment, existingTotal, false);
                            if (!extracted.isEmpty()) {
                                toolbox.replaceCompartment(compartment, carried);
                                menu.setCarried(extracted);
                                return true;
                            }
                        }
                    }
                    return false;
                }

                // Empty cursor: extract if populated, or explicitly clear ghost preview if empty-reserved
                int total = toolbox.getTotalCount(compartment);
                if (total <= 0) {
                    if (toolbox.isReservedEmpty(compartment))
                        return toolbox.clearFilter(compartment);
                    return false;
                }

                ItemStack template = toolbox.getTemplate(compartment);
                int maxStack = template.isEmpty() ? 64 : Math.max(1, template.getMaxStackSize());
                int avail = Math.min(total, maxStack);
                int amount = button == 1 ? Math.max(1, (avail + 1) / 2) : maxStack;
                ItemStack extracted = toolbox.extractFromCompartment(compartment, amount, false);
                if (!extracted.isEmpty()) {
                    menu.setCarried(extracted);
                    return true;
                }
                return false;
            }
            case SWAP -> {
                if (!menu.getCarried().isEmpty())
                    return false;
                boolean isOffhand = button == 40;
                if (!isOffhand && (button < 0 || button >= Inventory.getSelectionSize()))
                    return false;

                ItemStack swapStack = isOffhand
                        ? player.getOffhandItem()
                        : player.getInventory().getItem(button);
                if (openBackpackStack != null && swapStack == openBackpackStack)
                    return false;

                int existingTotal = toolbox.getTotalCount(compartment);
                if (swapStack.isEmpty()) {
                    if (existingTotal <= 0)
                        return false;
                    ItemStack template = toolbox.getTemplate(compartment);
                    int maxStack = template.isEmpty() ? 64 : Math.max(1, template.getMaxStackSize());
                    ItemStack extracted = toolbox.extractFromCompartment(compartment, maxStack, false);
                    if (extracted.isEmpty())
                        return false;
                    setSwapTarget(player, button, isOffhand, extracted);
                    return true;
                }

                if (BackpackHelper.isNestedContainer(swapStack))
                    return false;

                if (toolbox.canAccept(compartment, swapStack)) {
                    ItemStack rem = toolbox.insertIntoCompartment(compartment, swapStack, false);
                    if (rem.getCount() != swapStack.getCount() || rem.isEmpty()) {
                        setSwapTarget(player, button, isOffhand, rem);
                        return true;
                    }
                    return false;
                }

                if (toolbox.isReservedEmpty(compartment)) {
                    ItemStack rem = toolbox.replaceEmptyReservedAndInsert(compartment, swapStack);
                    if (rem.getCount() != swapStack.getCount() || rem.isEmpty()) {
                        setSwapTarget(player, button, isOffhand, rem);
                        return true;
                    }
                    return false;
                }

                // Different item in hotbar/offhand: swap only if compartment total fits into one valid stack
                ItemStack existingTemplate = toolbox.getTemplate(compartment);
                if (existingTotal > 0 && !existingTemplate.isEmpty()
                        && existingTotal <= Math.max(1, existingTemplate.getMaxStackSize())
                        && swapStack.getCount() <= PortableToolboxInventory.maxCapacityForItem(swapStack)) {
                    ItemStack extracted = toolbox.extractFromCompartment(compartment, existingTotal, false);
                    if (!extracted.isEmpty()) {
                        toolbox.replaceCompartment(compartment, swapStack);
                        setSwapTarget(player, button, isOffhand, extracted);
                        return true;
                    }
                }
                return false;
            }
            case THROW -> {
                if (!menu.getCarried().isEmpty())
                    return false;
                int total = toolbox.getTotalCount(compartment);
                if (total <= 0)
                    return false;
                ItemStack template = toolbox.getTemplate(compartment);
                int maxStack = template.isEmpty() ? 64 : Math.max(1, template.getMaxStackSize());
                int amount = button == 1 ? maxStack : 1;
                ItemStack extracted = toolbox.extractFromCompartment(compartment, amount, false);
                if (!extracted.isEmpty()) {
                    player.drop(extracted, true);
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    private static void setSwapTarget(Player player, int button, boolean isOffhand, ItemStack stack) {
        if (isOffhand) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
        } else {
            player.getInventory().setItem(button, stack);
        }
    }

    private static ItemStack insertIntoPlayerSlots(AbstractContainerMenu menu, ItemStack stack,
                                                   int playerStart, int playerEnd) {
        ItemStack remaining = stack.copy();
        if (remaining.isStackable()) {
            for (int i = playerStart; i < playerEnd && !remaining.isEmpty(); i++) {
                Slot slot = menu.getSlot(i);
                ItemStack dest = slot.getItem();
                if (dest.isEmpty() || !ItemStack.isSameItemSameComponents(dest, remaining))
                    continue;
                int limit = Math.min(slot.getMaxStackSize(remaining), remaining.getMaxStackSize());
                int space = limit - dest.getCount();
                if (space <= 0)
                    continue;
                int move = Math.min(space, remaining.getCount());
                slot.set(dest.copyWithCount(dest.getCount() + move));
                slot.setChanged();
                remaining.shrink(move);
            }
        }
        for (int i = playerStart; i < playerEnd && !remaining.isEmpty(); i++) {
            Slot slot = menu.getSlot(i);
            if (slot.hasItem() || !slot.mayPlace(remaining))
                continue;
            int limit = Math.min(slot.getMaxStackSize(remaining), remaining.getMaxStackSize());
            int move = Math.min(limit, remaining.getCount());
            if (move <= 0)
                continue;
            slot.set(remaining.copyWithCount(move));
            slot.setChanged();
            remaining.shrink(move);
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }
}
