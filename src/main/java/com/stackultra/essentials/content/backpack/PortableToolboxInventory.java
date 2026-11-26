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
 * Codec-safe 8-compartment portable Create Toolbox inventory.
 * <p>
 * Each compartment stores physically present items only (no phantom empty reservations):
 * <ul>
 *   <li>Non-stackable items ({@code maxStackSize == 1}): strict compartment capacity = <b>1</b>.</li>
 *   <li>Stackable items ({@code maxStackSize > 1}): strict compartment capacity = <b>4 * maxStackSize</b>
 *       (256 for 64-stack items, 64 for 16-stack items, scaling proportionally for modded stack sizes).</li>
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

        if (stack == null || stack.isEmpty())
            return;

        ItemContainerContents contents = stack.get(ExtraDataComponents.TOOLBOX_CONTENTS);
        if (contents != null)
            contents.copyInto(stacks);

        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
        }
    }

    public void save(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return;
        stack.set(ExtraDataComponents.TOOLBOX_CONTENTS, ItemContainerContents.fromItems(stacks));
        // Remove any legacy phantom reservation filter component
        stack.remove(ExtraDataComponents.TOOLBOX_FILTERS);
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        CompoundTag itemsTag = new CompoundTag();
        ContainerHelper.saveAllItems(itemsTag, stacks, registries);
        tag.put("Items", itemsTag);
        return tag;
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);

        if (tag == null)
            return;
        if (tag.contains("Items"))
            ContainerHelper.loadAllItems(tag.getCompound("Items"), stacks, registries);

        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
        }
    }

    /**
     * Returns a 1-count copy of the physically stored item in {@code compartment},
     * or {@link ItemStack#EMPTY} if the compartment is empty.
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
        if (total <= 0)
            return true;
        ItemStack existing = getTemplate(compartment);
        if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack))
            return false;
        return total < maxCapacityForItem(existing);
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
            settleSilent(compartment);
            notifyChanged();
        }

        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Inserts into compartments that physically contain a matching item (count &gt; 0).
     * Never claims empty compartments and strictly enforces per-item capacity caps.
     */
    public ItemStack insertIntoMatchingCompartment(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || BackpackHelper.isNestedContainer(stack))
            return stack;
        // Unstackables have strict capacity 1, so an already-populated matching compartment cannot accept more
        if (stack.getMaxStackSize() <= 1)
            return stack;

        ItemStack remaining = stack.copy();
        for (int c = 0; c < COMPARTMENTS && !remaining.isEmpty(); c++) {
            if (getTotalCount(c) <= 0)
                continue;
            ItemStack template = getTemplate(c);
            if (template.isEmpty() || !ItemStack.isSameItemSameComponents(template, remaining))
                continue;
            remaining = insertIntoCompartment(c, remaining, simulate);
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Extracts up to {@code maxAmount} items (strictly clamped to {@code maxStackSize}) from a compartment.
     * When the compartment reaches 0, it becomes completely empty (no phantom reservation).
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
            settleSilent(compartment);
            notifyChanged();
        }
        return result;
    }

    /**
     * Atomically replaces the entire contents of a compartment with a single valid stack (used for swaps).
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
        }
        notifyChanged();
    }

    public int findMatchingCompartment(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return -1;
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (getTotalCount(c) <= 0)
                continue;
            ItemStack template = getTemplate(c);
            if (!template.isEmpty() && ItemStack.isSameItemSameComponents(template, stack))
                return c;
        }
        return -1;
    }

    public boolean hasAnyContents() {
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (getTotalCount(c) > 0)
                return true;
        }
        return false;
    }

    /**
     * Consolidates and sorts all 8 toolbox compartments deterministically within strict per-item
     * compartment caps (1 for unstackables, 4 * maxStackSize for stackables), preserving exact item counts.
     */
    public boolean sortCompartments(SortMode mode) {
        record Entry(ItemStack template, int count) {
        }

        List<Entry> beforeSnapshot = new ArrayList<>(COMPARTMENTS);
        List<Entry> raw = new ArrayList<>(COMPARTMENTS);
        int totalBefore = 0;
        for (int c = 0; c < COMPARTMENTS; c++) {
            int count = getTotalCount(c);
            ItemStack template = getTemplate(c);
            if (count > 0 && !template.isEmpty()) {
                Entry entry = new Entry(template, count);
                beforeSnapshot.add(entry);
                raw.add(entry);
                totalBefore += count;
            } else {
                beforeSnapshot.add(new Entry(ItemStack.EMPTY, 0));
            }
        }
        if (raw.isEmpty())
            return false;

        // Consolidate matching stackable compartments up to each compartment's strict maxCapacityForItem
        List<ItemStack> consolidatedTemplates = new ArrayList<>(COMPARTMENTS);
        List<Integer> consolidatedCounts = new ArrayList<>(COMPARTMENTS);

        for (Entry entry : raw) {
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

        List<Entry> sorted = new ArrayList<>(consolidatedTemplates.size());
        for (int i = 0; i < consolidatedTemplates.size(); i++) {
            sorted.add(new Entry(consolidatedTemplates.get(i), consolidatedCounts.get(i)));
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
        sorted.sort(comp);

        boolean changed = false;
        for (int c = 0; c < COMPARTMENTS; c++) {
            Entry prev = beforeSnapshot.get(c);
            Entry next = c < sorted.size() ? sorted.get(c) : new Entry(ItemStack.EMPTY, 0);
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

        for (int c = 0; c < sorted.size(); c++) {
            Entry e = sorted.get(c);
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

        if (template.isEmpty() || total <= 0)
            return;

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

    // ---- IItemHandlerModifiable facade (8 compartment display slots, each <= maxStackSize) ----

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
