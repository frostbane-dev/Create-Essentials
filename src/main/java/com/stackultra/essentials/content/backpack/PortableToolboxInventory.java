package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

/**
 * Codec-safe 8-compartment portable Create Toolbox inventory.
 * <p>
 * Each compartment stores up to {@value #STACKS_PER_COMPARTMENT} separate backing {@link ItemStack}s
 * (up to 256 items for 64-stack items, 64 for 16-stack items, 4 for unstackables) plus 1 reserved
 * filter {@link ItemStack}. No individual backing stack ever exceeds its item's {@code maxStackSize},
 * completely avoiding Minecraft 1.21.1's {@code ItemStack.CODEC} count-99 serialization limit.
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

    public void load(ItemStack stack) {
        for (int i = 0; i < BACKING_SLOTS; i++)
            stacks.set(i, ItemStack.EMPTY);
        for (int i = 0; i < COMPARTMENTS; i++)
            filters.set(i, ItemStack.EMPTY);

        if (stack == null || stack.isEmpty())
            return;

        ItemContainerContents contents = stack.get(ExtraDataComponents.TOOLBOX_CONTENTS);
        if (contents != null)
            contents.copyInto(stacks);

        ItemContainerContents filterContents = stack.get(ExtraDataComponents.TOOLBOX_FILTERS);
        if (filterContents != null)
            filterContents.copyInto(filters);

        // Enforce filter consistency: any populated compartment must have a matching filter
        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
            ItemStack first = stacks.get(c * STACKS_PER_COMPARTMENT);
            if (!first.isEmpty() && filters.get(c).isEmpty()) {
                filters.set(c, first.copyWithCount(1));
            } else if (!filters.get(c).isEmpty() && filters.get(c).getCount() != 1) {
                filters.set(c, filters.get(c).copyWithCount(1));
            }
        }
    }

    public void save(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return;
        stack.set(ExtraDataComponents.TOOLBOX_CONTENTS, ItemContainerContents.fromItems(stacks));
        stack.set(ExtraDataComponents.TOOLBOX_FILTERS, ItemContainerContents.fromItems(filters));
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
        for (int i = 0; i < COMPARTMENTS; i++)
            filters.set(i, ItemStack.EMPTY);

        if (tag == null)
            return;
        if (tag.contains("Items"))
            ContainerHelper.loadAllItems(tag.getCompound("Items"), stacks, registries);
        if (tag.contains("Filters"))
            ContainerHelper.loadAllItems(tag.getCompound("Filters"), filters, registries);

        for (int c = 0; c < COMPARTMENTS; c++) {
            settleSilent(c);
            ItemStack first = stacks.get(c * STACKS_PER_COMPARTMENT);
            if (!first.isEmpty() && filters.get(c).isEmpty())
                filters.set(c, first.copyWithCount(1));
        }
    }

    public ItemContainerContents getFiltersComponent() {
        return ItemContainerContents.fromItems(filters);
    }

    public void applyClientFilters(ItemContainerContents filterContents) {
        for (int i = 0; i < COMPARTMENTS; i++)
            filters.set(i, ItemStack.EMPTY);
        if (filterContents != null)
            filterContents.copyInto(filters);
    }

    public ItemStack getFilter(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return ItemStack.EMPTY;
        return filters.get(compartment);
    }

    public boolean isReservedEmpty(int compartment) {
        return compartment >= 0 && compartment < COMPARTMENTS
                && !filters.get(compartment).isEmpty()
                && getTotalCount(compartment) == 0;
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
        int maxStack = template.isEmpty() ? 64 : Math.max(1, template.getMaxStackSize());
        return maxStack * STACKS_PER_COMPARTMENT;
    }

    public boolean canAccept(int compartment, ItemStack stack) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return false;
        if (stack == null || stack.isEmpty())
            return false;
        if (BackpackHelper.isNestedContainer(stack))
            return false;
        ItemStack filter = filters.get(compartment);
        if (filter.isEmpty())
            return getTotalCount(compartment) == 0;
        return ItemStack.isSameItemSameComponents(filter, stack);
    }

    public ItemStack insertIntoCompartment(int compartment, ItemStack stack, boolean simulate) {
        if (!canAccept(compartment, stack))
            return stack;

        ItemStack remaining = stack.copy();
        int original = remaining.getCount();
        int base = compartment * STACKS_PER_COMPARTMENT;
        int maxStack = Math.max(1, remaining.getMaxStackSize());

        // Pass 1: fill existing non-full sub-stacks
        if (remaining.isStackable()) {
            for (int i = 0; i < STACKS_PER_COMPARTMENT && !remaining.isEmpty(); i++) {
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

        // Pass 2: fill empty sub-slots
        for (int i = 0; i < STACKS_PER_COMPARTMENT && !remaining.isEmpty(); i++) {
            if (!stacks.get(base + i).isEmpty())
                continue;
            int move = Math.min(maxStack, remaining.getCount());
            if (!simulate)
                stacks.set(base + i, remaining.copyWithCount(move));
            remaining.shrink(move);
        }

        if (!simulate && remaining.getCount() != original) {
            if (filters.get(compartment).isEmpty())
                filters.set(compartment, stack.copyWithCount(1));
            settleSilent(compartment);
            notifyChanged();
        }

        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Inserts into compartments that already have a matching filter (populated OR empty reserved).
     * Never claims unreserved empty compartments.
     */
    public ItemStack insertIntoMatchingCompartment(ItemStack stack, boolean simulate) {
        if (stack == null || stack.isEmpty() || BackpackHelper.isNestedContainer(stack))
            return stack;

        ItemStack remaining = stack.copy();
        for (int c = 0; c < COMPARTMENTS && !remaining.isEmpty(); c++) {
            ItemStack filter = filters.get(c);
            if (filter.isEmpty())
                continue;
            if (!ItemStack.isSameItemSameComponents(filter, remaining))
                continue;
            remaining = insertIntoCompartment(c, remaining, simulate);
        }
        return remaining.isEmpty() ? ItemStack.EMPTY : remaining;
    }

    /**
     * Extracts up to {@code maxAmount} items (clamped to item's {@code maxStackSize}) from a compartment.
     * Preserves the compartment's reserved filter even when count reaches 0.
     */
    public ItemStack extractFromCompartment(int compartment, int maxAmount, boolean simulate) {
        if (compartment < 0 || compartment >= COMPARTMENTS || maxAmount <= 0)
            return ItemStack.EMPTY;

        int base = compartment * STACKS_PER_COMPARTMENT;
        ItemStack template = ItemStack.EMPTY;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++) {
            if (!stacks.get(base + i).isEmpty()) {
                template = stacks.get(base + i);
                break;
            }
        }
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

    public boolean clearReservation(int compartment) {
        if (compartment < 0 || compartment >= COMPARTMENTS)
            return false;
        if (getTotalCount(compartment) > 0 || filters.get(compartment).isEmpty())
            return false;
        filters.set(compartment, ItemStack.EMPTY);
        notifyChanged();
        return true;
    }

    public int findMatchingCompartment(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return -1;
        for (int c = 0; c < COMPARTMENTS; c++) {
            ItemStack filter = filters.get(c);
            if (!filter.isEmpty() && ItemStack.isSameItemSameComponents(filter, stack))
                return c;
        }
        return -1;
    }

    public boolean hasAnyContentsOrFilters() {
        for (int c = 0; c < COMPARTMENTS; c++) {
            if (!filters.get(c).isEmpty() || getTotalCount(c) > 0)
                return true;
        }
        return false;
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
                if (template.isEmpty())
                    template = sub.copyWithCount(1);
                total += sub.getCount();
            }
        }
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++)
            stacks.set(base + i, ItemStack.EMPTY);

        if (template.isEmpty() || total <= 0)
            return;

        int maxStack = Math.max(1, template.getMaxStackSize());
        int remaining = total;
        for (int i = 0; i < STACKS_PER_COMPARTMENT && remaining > 0; i++) {
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
        if (slot < 0 || slot >= COMPARTMENTS)
            return;
        int base = slot * STACKS_PER_COMPARTMENT;
        for (int i = 0; i < STACKS_PER_COMPARTMENT; i++)
            stacks.set(base + i, ItemStack.EMPTY);
        if (stack != null && !stack.isEmpty() && !BackpackHelper.isNestedContainer(stack)) {
            int maxStack = Math.max(1, stack.getMaxStackSize());
            stacks.set(base, stack.copyWithCount(Math.min(maxStack, stack.getCount())));
            filters.set(slot, stack.copyWithCount(1));
        }
        notifyChanged();
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
