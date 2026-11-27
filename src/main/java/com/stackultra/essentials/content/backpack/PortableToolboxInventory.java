package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Codec-safe 8-compartment portable Create Toolbox inventory with Create-style reserved ghost filters.
 * <p>
 * Each compartment stores up to 4 backing stacks plus an optional 1-count reserved filter:
 * <ul>
 *   <li>Non-stackable items ({@code maxStackSize == 1}): strict compartment capacity = <b>1</b>.</li>
 *   <li>Stackable items ({@code maxStackSize > 1}): strict compartment capacity = <b>4 * maxStackSize</b>
 *       (256 for 64-stack items, 64 for 16-stack items, scaling proportionally for modded stack sizes).</li>
 *   <li>When the last item is removed, the compartment's reserved filter remains so priority routing,
 *       auto-restock, and ghost previews stay intact until explicitly cleared or replaced when empty.</li>
 * </ul>
 * Every individual backing {@link ItemStack} stays {@code <= maxStackSize}, avoiding Minecraft 1.21.1's
 * {@code ItemStack.CODEC} count-99 serialization limit.
 */
public class PortableToolboxInventory implements IItemHandlerModifiable {

    public static final int COMPARTMENTS = 8;
    public static final int STACKS_PER_COMPARTMENT = 4;
    public static final int BACKING_SLOTS = COMPARTMENTS * STACKS_PER_COMPARTMENT;
    public static final int EQUIP_TARGET_COUNT = 32;

    private final NonNullList<ItemStack> stacks = NonNullList.withSize(BACKING_SLOTS, ItemStack.EMPTY);
    private final NonNullList<ItemStack> filters = NonNullList.withSize(COMPARTMENTS, ItemStack.EMPTY);

    private final ItemStack boundStack;
    private final Runnable onChanged;

    private PortableToolboxInventory(ItemStack boundStack, Runnable onChanged) {
        this.boundStack = boundStack;
        this.onChanged = onChanged;
        if (boundStack != null)
            load(boundStack);
    }

    public static PortableToolboxInventory empty() {
        return new PortableToolboxInventory(null, null);
    }

    public static PortableToolboxInventory forBlockEntity(Runnable onChanged) {
        return new PortableToolboxInventory(null, onChanged);
    }

    public static PortableToolboxInventory forStack(ItemStack stack) {
        return new PortableToolboxInventory(stack, null);
    }

    public static PortableToolboxInventory forStack(ItemStack stack, Runnable onChanged) {
        return new PortableToolboxInventory(stack, onChanged);
    }

    public static int maxStacksForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return STACKS_PER_COMPARTMENT;
        return stack.getMaxStackSize() <= 1 ? 1 : STACKS_PER_COMPARTMENT;
    }

    public static int maxCapacityForItem(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return 0;
        int maxStack = Math.max(1, stack.getMaxStackSize());
        return maxStack == 1 ? 1 : maxStack * STACKS_PER_COMPARTMENT;
    }

    public void load(ItemStack stack) {
        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);
        for (int c = 0; c < COMPARTMENTS; c++)
            filters.set(c, ItemStack.EMPTY);

        if (stack == null || stack.isEmpty())
            return;

        ItemContainerContents contents = stack.get(ExtraDataComponents.TOOLBOX_CONTENTS);
        if (contents != null)
            contents.copyInto(stacks);

        ItemContainerContents savedFilters = stack.get(ExtraDataComponents.TOOLBOX_FILTERS);
        if (savedFilters != null)
            savedFilters.copyInto(filters);

        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
        }
    }

    public void save(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return;
        stack.set(ExtraDataComponents.TOOLBOX_CONTENTS, ItemContainerContents.fromItems(stacks));
        boolean anyFilter = false;
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (!filters.get(c).isEmpty()) {
                anyFilter = true;
                break;
            }
        }
        if (anyFilter) {
            stack.set(ExtraDataComponents.TOOLBOX_FILTERS, ItemContainerContents.fromItems(filters));
        } else {
            stack.remove(ExtraDataComponents.TOOLBOX_FILTERS);
        }
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag itemsTag = new CompoundTag();
        ContainerHelper.saveAllItems(itemsTag, stacks, registries);
        tag.put("Items", itemsTag);
        CompoundTag filtersTag = new CompoundTag();
        ContainerHelper.saveAllItems(filtersTag, filters, registries);
        tag.put("Filters", filtersTag);
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);
        for (int c = 0; c < COMPARTMENTS; c++)
            filters.set(c, ItemStack.EMPTY);

        if (tag == null)
            return;
        if (tag.contains("Items"))
            ContainerHelper.loadAllItems(tag.getCompound("Items"), stacks, registries);
        if (tag.contains("Filters"))
            ContainerHelper.loadAllItems(tag.getCompound("Filters"), filters, registries);

        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
        }
    }

    public void copyFrom(PortableToolboxInventory source) {
        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);
        for (int c = 0; c < COMPARTMENTS; c++)
            filters.set(c, ItemStack.EMPTY);

        if (source != null) {
            for (int i = 0; i < BACKING_SLOTS; i++) {
                ItemStack s = source.stacks.get(i);
                if (!s.isEmpty())
                    stacks.set(i, s.copy());
            }
            for (int c = 0; c < COMPARTMENTS; c++) {
                ItemStack f = source.filters.get(c);
                if (!f.isEmpty())
                    filters.set(c, f.copy());
            }
        }
        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
        }
        notifyChanged();
    }

    public static PortableToolboxInventory copyOf(PortableToolboxInventory source) {
        PortableToolboxInventory copy = empty();
        copy.copyFrom(source);
        return copy;
    }

    public net.neoforged.neoforge.items.ItemStackHandler toBackingItemsHandler() {
        net.neoforged.neoforge.items.ItemStackHandler handler =
                new net.neoforged.neoforge.items.ItemStackHandler(BACKING_SLOTS);
        for (int i = 0; i < BACKING_SLOTS; i++) {
            ItemStack s = stacks.get(i);
            if (!s.isEmpty())
                handler.setStackInSlot(i, s.copy());
        }
        return handler;
    }

    public net.neoforged.neoforge.items.ItemStackHandler toFiltersHandler() {
        net.neoforged.neoforge.items.ItemStackHandler handler =
                new net.neoforged.neoforge.items.ItemStackHandler(COMPARTMENTS);
        for (int c = 0; c < COMPARTMENTS; c++) {
            ItemStack f = filters.get(c);
            if (!f.isEmpty())
                handler.setStackInSlot(c, f.copy());
        }
        return handler;
    }

    public static PortableToolboxInventory fromHandlers(net.neoforged.neoforge.items.IItemHandler backingItems,
                                                        net.neoforged.neoforge.items.IItemHandler savedFilters) {
        PortableToolboxInventory inv = empty();
        if (backingItems != null) {
            int limit = Math.min(BACKING_SLOTS, backingItems.getSlots());
            for (int i = 0; i < limit; i++) {
                ItemStack s = backingItems.getStackInSlot(i);
                if (!s.isEmpty())
                    inv.stacks.set(i, s.copy());
            }
        }
        if (savedFilters != null) {
            int limit = Math.min(COMPARTMENTS, savedFilters.getSlots());
            for (int c = 0; c < limit; c++) {
                ItemStack f = savedFilters.getStackInSlot(c);
                if (!f.isEmpty())
                    inv.filters.set(c, f.copyWithCount(1));
            }
        }
        for (int c = 0; c < COMPARTMENTS; c++) {
            inv.settleSilent(c);
        }
        return inv;
    }

    /**
     * Returns a 1-count copy of the reserved filter for {@code compartment} (present for both
     * populated compartments and empty ghost-reserved compartments), or {@link ItemStack#EMPTY}
     * if the compartment is completely unreserved.
     */
    public ItemStack getFilter(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return ItemStack.EMPTY;
        ItemStack f = filters.get(compartment);
        return f.isEmpty() ? ItemStack.EMPTY : f.copyWithCount(1);
    }

    /**
     * Returns a 1-count copy of the physically stored item in {@code compartment},
     * or {@link ItemStack#EMPTY} if the compartment is physically empty.
     */
    public ItemStack getTemplate(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return ItemStack.EMPTY;
        int base = compartment * STACKS_PER_COMPARTMENT;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
            ItemStack sub = stacks.get(base + i);
            if (!sub.isEmpty())
                return sub.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Returns the item to display in UI/tooltips/radial wheel: the physical item if populated,
     * otherwise the reserved ghost filter, or {@link ItemStack#EMPTY} if unreserved.
     */
    public ItemStack getDisplayTemplate(int compartment) {
        ItemStack template = getTemplate(compartment);
        return template.isEmpty() ? getFilter(compartment) : template;
    }

    public boolean isReservedEmpty(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return false;
        return getTotalCount(compartment) == 0 && !filters.get(compartment).isEmpty();
    }

    /**
     * Explicitly clears a reserved ghost filter ONLY when the compartment is physically empty.
     * Refuses to clear if any items are still stored (zero item-loss guarantee).
     */
    public boolean clearFilter(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return false;
        if (getTotalCount(compartment) > 0)
            return false;
        if (filters.get(compartment).isEmpty())
            return false;
        filters.set(compartment, ItemStack.EMPTY);
        notifyChanged();
        return true;
    }

    public int getTotalCount(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return 0;
        int total = 0;
        int base = compartment * STACKS_PER_COMPARTMENT;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
            ItemStack s = stacks.get(base + i);
            if (!s.isEmpty())
                total += s.getCount();
        }
        return total;
    }

    public int getMaxCapacity(int compartment, ItemStack template) {
        return maxCapacityForItem(template);
    }

    public boolean canAccept(int compartment, ItemStack stack) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return false;
        if (stack == null || stack.isEmpty())
            return false;
        if (BackpackHelper.isNestedContainer(stack))
            return false;
        int total = getTotalCount(compartment);
        ItemStack filter = filters.get(compartment);
        if (filter.isEmpty())
            return total == 0;
        if (!ItemStack.isSameItemSameComponents(filter, stack))
            return false;
        return total < maxCapacityForItem(filter);
    }

    public ItemStack insertIntoCompartment(int compartment, ItemStack stack, boolean simulate) {
        if (!canAccept(compartment, stack))
            return stack;

        ItemStack remaining = stack.copy();
        int original = remaining.getCount();
        int base = compartment * STACKS_PER_COMPARTMENT;
        int maxStack = Math.max(1, remaining.getMaxStackSize());
        int allowedSubSlots = maxStacksForItem(remaining);

        // Pass 1: fill existing non-full sub-stacks (only for stackables)
        if (remaining.isStackable()) {
            for (int i = 0; i < allowedSubSlots && !remaining.isEmpty(); i++) {
                ItemStack existing = stacks.get(base + i);
                if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, remaining))
                    continue;
                int space = maxStack - existing.getCount();
                if (space <= 0)
                    continue;
                int move = Math.min(space, remaining.getCount());
                if (!simulate)
                    existing.grow(move);
                remaining.shrink(move);
            }
        }

        // Pass 2: fill empty sub-slots up to strict per-item sub-slot cap (1 for unstackables, 4 for stackables)
        for (int i = 0; i < allowedSubSlots && !remaining.isEmpty(); i++) {
            if (!stacks.get(base + i).isEmpty())
                continue;
            int move = Math.min(maxStack, remaining.getCount());
            if (!simulate)
                stacks.set(base + i, remaining.copyWithCount(move));
            remaining.shrink(move);
        }

        if (!simulate && remaining.getCount() != original) {
            filters.set(compartment, stack.copyWithCount(1));
            settleSilent(compartment);
            notifyChanged();
        }

        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Safely replaces an empty reserved ghost filter with {@code stack} and inserts {@code stack},
     * returning any untransferred remainder. Refuses to operate if the compartment contains items.
     */
    public ItemStack replaceEmptyReservedAndInsert(int compartment, ItemStack stack) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return stack;
        if (stack == null || stack.isEmpty() || BackpackHelper.isNestedContainer(stack))
            return stack;
        if (getTotalCount(compartment) > 0)
            return stack;
        filters.set(compartment, ItemStack.EMPTY);
        return insertIntoCompartment(compartment, stack, false);
    }

    /**
     * Inserts into compartments whose reserved filter matches {@code stack} (both populated
     * and empty ghost-reserved compartments). Never claims unreserved empty compartments and
     * strictly enforces per-item capacity caps (1 for unstackables, 4 * maxStackSize for stackables).
     */
    public ItemStack insertIntoMatchingCompartment(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || BackpackHelper.isNestedContainer(stack))
            return stack;

        ItemStack remaining = stack.copy();
        for (int c = 0; c < COMPARTMENTS && !remaining.isEmpty(); c++) {
            ItemStack filter = filters.get(c);
            if (filter.isEmpty() || !ItemStack.isSameItemSameComponents(filter, remaining))
                continue;
            remaining = insertIntoCompartment(c, remaining, simulate);
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Extracts up to {@code maxAmount} items (strictly clamped to {@code maxStackSize}) from a compartment.
     * When the compartment reaches 0, its reserved ghost filter is preserved (Create Toolbox behavior).
     */
    public ItemStack extractFromCompartment(int compartment, int maxAmount, boolean simulate) {
        if (compartment < 0 || compartment >= COMPARTMENTS || maxAmount <= 0)
            return ItemStack.EMPTY;

        int base = compartment * STACKS_PER_COMPARTMENT;
        // CRITICAL: copy template BEFORE shrinking any sub-slot so shrinking sub-slot 0 to 0
        // never mutates template into ItemStack.EMPTY!
        ItemStack template = getTemplate(compartment);
        if (template.isEmpty())
            return ItemStack.EMPTY;

        int limit = Math.min(maxAmount, Math.max(1, template.getMaxStackSize()));
        int taken = 0;

        // Drain from the tail sub-slots first to keep leading stacks full
        for (int i = STACKS_PER_COMPARTMENT - 1; i >= 0 && taken < limit; i--) {
            ItemStack sub = stacks.get(base + i);
            if (sub.isEmpty())
                continue;
            int take = Math.min(limit - taken, sub.getCount());
            if (!simulate) {
                sub.shrink(take);
                if (sub.isEmpty())
                    stacks.set(base + i, ItemStack.EMPTY);
            }
            taken += take;
        }

        if (taken <= 0)
            return ItemStack.EMPTY;

        ItemStack result = template.copyWithCount(taken);
        if (!simulate) {
            // Preserve reserved ghost filter when emptied
            if (filters.get(compartment).isEmpty())
                filters.set(compartment, template.copyWithCount(1));
            settleSilent(compartment);
            notifyChanged();
        }
        return result;
    }

    /**
     * Atomically replaces the entire contents and filter of a compartment with a single valid stack (used for swaps).
     */
    public void replaceCompartment(int compartment, ItemStack stack) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return;
        int base = compartment * STACKS_PER_COMPARTMENT;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++)
            stacks.set(base + i, ItemStack.EMPTY);
        if (stack != null && !stack.isEmpty() && !BackpackHelper.isNestedContainer(stack)) {
            int cap = Math.min(stack.getCount(), maxCapacityForItem(stack));
            int maxStack = Math.max(1, stack.getMaxStackSize());
            int remaining = cap;
            int allowed = maxStacksForItem(stack);
            for (int i = 0; i < allowed && remaining > 0; i++) {
                int chunk = Math.min(maxStack, remaining);
                stacks.set(base + i, stack.copyWithCount(chunk));
                remaining -= chunk;
            }
            filters.set(compartment, stack.copyWithCount(1));
        } else {
            filters.set(compartment, ItemStack.EMPTY);
        }
        notifyChanged();
    }

    public int findMatchingCompartment(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return -1;
        int fallback = -1;
        for (int c = 0; c < COMPARTMENTS; c++) {
            ItemStack filter = filters.get(c);
            if (filter.isEmpty() || !ItemStack.isSameItemSameComponents(filter, stack))
                continue;
            if (getTotalCount(c) < maxCapacityForItem(filter))
                return c;
            if (fallback < 0)
                fallback = c;
        }
        return fallback;
    }

    public boolean hasAnyContents() {
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (getTotalCount(c) > 0)
                return true;
        }
        return false;
    }

    public boolean hasAnyContentsOrFilters() {
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (getTotalCount(c) > 0 || !filters.get(c).isEmpty())
                return true;
        }
        return false;
    }

    /**
     * Consolidates and sorts all 8 toolbox compartments deterministically within strict per-item
     * compartment caps (1 for unstackables, 4 * maxStackSize for stackables), preserving exact item counts
     * and retaining distinct empty reserved ghost filters after populated compartments.
     */
    public boolean sortCompartments(SortMode mode) {
        record Entry(ItemStack template, int count) {
        }

        List<Entry> beforeSnapshot = new ArrayList<>(COMPARTMENTS);
        List<Entry> rawPopulated = new ArrayList<>(COMPARTMENTS);
        List<ItemStack> rawEmptyFilters = new ArrayList<>(COMPARTMENTS);
        int totalBefore = 0;
        for (int c = 0; c < COMPARTMENTS; c++) {
            int count = getTotalCount(c);
            ItemStack template = getTemplate(c);
            ItemStack filter = getFilter(c);
            if (count > 0 && !template.isEmpty()) {
                Entry entry = new Entry(template, count);
                beforeSnapshot.add(entry);
                rawPopulated.add(entry);
                totalBefore += count;
            } else if (!filter.isEmpty()) {
                beforeSnapshot.add(new Entry(filter, 0));
                rawEmptyFilters.add(filter);
            } else {
                beforeSnapshot.add(new Entry(ItemStack.EMPTY, 0));
            }
        }
        if (rawPopulated.isEmpty() && rawEmptyFilters.isEmpty())
            return false;

        // Consolidate matching stackable compartments up to each compartment's strict maxCapacityForItem
        List<ItemStack> consolidatedTemplates = new ArrayList<>(COMPARTMENTS);
        List<Integer> consolidatedCounts = new ArrayList<>(COMPARTMENTS);

        for (Entry entry : rawPopulated) {
            ItemStack template = entry.template();
            int remaining = entry.count();
            int maxCap = maxCapacityForItem(template);

            if (template.getMaxStackSize() > 1) {
                for (int i = 0; i < consolidatedTemplates.size() && remaining > 0; i++) {
                    ItemStack existing = consolidatedTemplates.get(i);
                    if (!ItemStack.isSameItemSameComponents(existing, template))
                        continue;
                    int existingCount = consolidatedCounts.get(i);
                    int space = maxCap - existingCount;
                    if (space <= 0)
                        continue;
                    int move = Math.min(space, remaining);
                    consolidatedCounts.set(i, existingCount + move);
                    remaining -= move;
                }
            }

            while (remaining > 0) {
                int chunk = Math.min(maxCap, remaining);
                consolidatedTemplates.add(template.copyWithCount(1));
                consolidatedCounts.add(chunk);
                remaining -= chunk;
            }
        }

        int totalAfter = 0;
        for (int c : consolidatedCounts)
            totalAfter += c;

        // Safety invariant: never drop, truncate, over-cap, or duplicate items
        if (totalAfter != totalBefore || consolidatedTemplates.size() > COMPARTMENTS)
            return false;

        List<Entry> sortedPopulated = new ArrayList<>(consolidatedTemplates.size());
        for (int i = 0; i < consolidatedTemplates.size(); i++) {
            sortedPopulated.add(new Entry(consolidatedTemplates.get(i), consolidatedCounts.get(i)));
        }

        // Preserve empty reserved ghost filters that are not redundant with populated or earlier empty filters
        List<Entry> sortedEmpty = new ArrayList<>(rawEmptyFilters.size());
        for (ItemStack emptyFilter : rawEmptyFilters) {
            boolean duplicate = false;
            for (Entry pop : sortedPopulated) {
                if (ItemStack.isSameItemSameComponents(pop.template(), emptyFilter)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                for (Entry prevEmpty : sortedEmpty) {
                    if (ItemStack.isSameItemSameComponents(prevEmpty.template(), emptyFilter)) {
                        duplicate = true;
                        break;
                    }
                }
            }
            if (!duplicate && sortedPopulated.size() + sortedEmpty.size() < COMPARTMENTS) {
                sortedEmpty.add(new Entry(emptyFilter.copyWithCount(1), 0));
            }
        }

        Comparator<Entry> comp = switch (mode) {
            case NAME -> Comparator
                    .comparing((Entry e) -> e.template().getHoverName().getString().toLowerCase(Locale.ROOT))
                    .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.template().getItem()).toString())
                    .thenComparing(Comparator.comparingInt(Entry::count).reversed())
                    .thenComparing(e -> e.template().getComponents().toString());
            case MOD -> Comparator
                    .comparing((Entry e) -> BuiltInRegistries.ITEM.getKey(e.template().getItem()).getNamespace())
                    .thenComparing(e -> e.template().getHoverName().getString().toLowerCase(Locale.ROOT))
                    .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.template().getItem()).getPath())
                    .thenComparing(Comparator.comparingInt(Entry::count).reversed())
                    .thenComparing(e -> e.template().getComponents().toString());
            case COUNT -> Comparator
                    .comparingInt(Entry::count).reversed()
                    .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.template().getItem()).getNamespace())
                    .thenComparing(e -> e.template().getHoverName().getString().toLowerCase(Locale.ROOT))
                    .thenComparing(e -> BuiltInRegistries.ITEM.getKey(e.template().getItem()).getPath())
                    .thenComparing(e -> e.template().getComponents().toString());
        };
        sortedPopulated.sort(comp);
        sortedEmpty.sort(comp);

        List<Entry> combined = new ArrayList<>(COMPARTMENTS);
        combined.addAll(sortedPopulated);
        combined.addAll(sortedEmpty);

        boolean changed = false;
        for (int c = 0; c < COMPARTMENTS; c++) {
            Entry prev = beforeSnapshot.get(c);
            Entry next = c < combined.size() ? combined.get(c) : new Entry(ItemStack.EMPTY, 0);
            if (prev.count() != next.count()
                    || !ItemStack.isSameItemSameComponents(prev.template(), next.template())) {
                changed = true;
                break;
            }
        }
        if (!changed)
            return false;

        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);
        for (int c = 0; c < COMPARTMENTS; c++)
            filters.set(c, ItemStack.EMPTY);

        for (int c = 0; c < combined.size(); c++) {
            Entry e = combined.get(c);
            filters.set(c, e.template().copyWithCount(1));
            if (e.count() <= 0)
                continue;
            int base = c * STACKS_PER_COMPARTMENT;
            int maxStack = Math.max(1, e.template().getMaxStackSize());
            int allowed = maxStacksForItem(e.template());
            int rem = e.count();
            for (int i = 0; i < allowed && rem > 0; i++) {
                int chunk = Math.min(maxStack, rem);
                stacks.set(base + i, e.template().copyWithCount(chunk));
                rem -= chunk;
            }
        }

        notifyChanged();
        return true;
    }

    public static int equipTargetCount(ItemStack template) {
        if (template == null || template.isEmpty())
            return EQUIP_TARGET_COUNT;
        return Math.min(EQUIP_TARGET_COUNT, Math.max(1, template.getMaxStackSize()));
    }

    private void settleSilent(int compartment) {
        int base = compartment * STACKS_PER_COMPARTMENT;
        ItemStack template = ItemStack.EMPTY;
        int total = 0;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
            ItemStack sub = stacks.get(base + i);
            if (!sub.isEmpty()) {
                if (template.isEmpty()) {
                    template = sub.copyWithCount(1);
                    total += sub.getCount();
                } else if (ItemStack.isSameItemSameComponents(template, sub)) {
                    total += sub.getCount();
                }
            }
        }
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++)
            stacks.set(base + i, ItemStack.EMPTY);

        if (template.isEmpty() || total <= 0) {
            // Preserve valid reserved ghost filter (clamped to count 1, non-nested)
            ItemStack existingFilter = filters.get(compartment);
            if (existingFilter.isEmpty() || BackpackHelper.isNestedContainer(existingFilter)) {
                filters.set(compartment, ItemStack.EMPTY);
            } else {
                filters.set(compartment, existingFilter.copyWithCount(1));
            }
            return;
        }

        filters.set(compartment, template.copyWithCount(1));
        int maxStack = Math.max(1, template.getMaxStackSize());
        int allowed = maxStacksForItem(template);
        int maxCap = maxCapacityForItem(template);
        int remaining = Math.min(total, maxCap);
        for (int i = 0; i < allowed && remaining > 0; i++) {
            int count = Math.min(maxStack, remaining);
            stacks.set(base + i, template.copyWithCount(count));
            remaining -= count;
        }
    }

    private void notifyChanged() {
        if (boundStack != null)
            save(boundStack);
        if (onChanged != null)
            onChanged.run();
    }

    /**
     * Read-only 8-slot display view for container menu slots.
     * Returns the physical stack (clamped to {@code maxStackSize}) when populated, or the 1-count
     * reserved ghost filter when empty-reserved, while blocking direct external mutation so
     * vanilla slot mechanics can never corrupt multi-stack compartments or filters.
     */
    public net.neoforged.neoforge.items.IItemHandler asMenuDisplayHandler() {
        return new net.neoforged.neoforge.items.IItemHandler() {
            @Override
            public int getSlots() {
                return COMPARTMENTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                if (slot < 0 || slot >= COMPARTMENTS)
                    return ItemStack.EMPTY;
                int total = getTotalCount(slot);
                if (total > 0) {
                    ItemStack first = stacks.get(slot * STACKS_PER_COMPARTMENT);
                    if (!first.isEmpty())
                        return first.copyWithCount(Math.min(total, Math.max(1, first.getMaxStackSize())));
                }
                ItemStack filter = filters.get(slot);
                return filter.isEmpty() ? ItemStack.EMPTY : filter.copyWithCount(1);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
    }

    // ---- IItemHandlerModifiable facade (8 compartment physical slots, each <= maxStackSize) ----

    @Override
    public int getSlots() {
        return COMPARTMENTS;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= COMPARTMENTS)
            return ItemStack.EMPTY;
        int total = getTotalCount(slot);
        if (total <= 0)
            return ItemStack.EMPTY;
        ItemStack first = stacks.get(slot * STACKS_PER_COMPARTMENT);
        if (first.isEmpty())
            return ItemStack.EMPTY;
        return first.copyWithCount(Math.min(total, Math.max(1, first.getMaxStackSize())));
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        replaceCompartment(slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return insertIntoCompartment(slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return extractFromCompartment(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return canAccept(slot, stack);
    }
}
