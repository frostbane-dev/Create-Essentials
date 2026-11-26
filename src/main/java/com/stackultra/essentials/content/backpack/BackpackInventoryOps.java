package com.stackultra.essentials.content.backpack;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

    public static boolean transferMatchingToStorage(AbstractContainerMenu menu,
                                                    int storageStart, int storageEnd,
                                                    int playerStart, int playerEnd,
                                                    Player player,
                                                    @Nullable ItemStack openBackpackStack) {
        List<ItemStack> storageSnapshot = snapshotNonEmpty(menu, storageStart, storageEnd);
        if (storageSnapshot.isEmpty())
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
            if (!matchesAny(source, storageSnapshot))
                continue;

            if (moveStackToRange(menu, sourceSlot, storageStart, storageEnd))
                movedAny = true;
        }
        return movedAny;
    }

    public static boolean transferMatchingToInventory(AbstractContainerMenu menu,
                                                      int storageStart, int storageEnd,
                                                      int playerStart, int playerEnd,
                                                      Player player) {
        List<ItemStack> playerSnapshot = snapshotNonEmpty(menu, playerStart, playerEnd);
        if (playerSnapshot.isEmpty())
            return false;

        boolean movedAny = false;
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

    public static boolean sortStorage(AbstractContainerMenu menu,
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
                    .comparing((ItemStack s) -> displayName(s))
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
     * Handles Create-style toolbox compartment click interactions safely on the server.
     * Supports: full-stack insert (LMB), single-item insert (RMB), stack extract (empty LMB),
     * half-stack extract (empty RMB), shift-click transfer to player inventory, and clearing
     * an empty reserved filter slot on click.
     */
    public static boolean handleToolboxSlotClick(AbstractContainerMenu menu,
                                                 PortableToolboxInventory toolbox,
                                                 int compartment,
                                                 int button,
                                                 ClickType clickType,
                                                 Player player,
                                                 int playerSlotStart,
                                                 int playerSlotEnd) {
        if (compartment < 0 || compartment >= PortableToolboxInventory.COMPARTMENTS)
            return false;

        if (clickType == ClickType.QUICK_MOVE) {
            if (toolbox.getTotalCount(compartment) == 0)
                return toolbox.clearReservation(compartment);
            ItemStack filter = toolbox.getFilter(compartment);
            int maxExtract = filter.isEmpty() ? 64 : Math.max(1, filter.getMaxStackSize());
            ItemStack extracted = toolbox.extractFromCompartment(compartment, maxExtract, false);
            if (extracted.isEmpty())
                return false;
            ItemStack remaining = insertIntoPlayerSlots(menu, extracted, playerSlotStart, playerSlotEnd);
            if (!remaining.isEmpty())
                toolbox.insertIntoCompartment(compartment, remaining, false);
            return remaining.getCount() != extracted.getCount();
        }

        if (clickType != ClickType.PICKUP)
            return false;

        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
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

        // Empty cursor: extract or clear empty reservation
        int total = toolbox.getTotalCount(compartment);
        if (total == 0)
            return toolbox.clearReservation(compartment);

        ItemStack filter = toolbox.getFilter(compartment);
        int maxStack = filter.isEmpty() ? 64 : Math.max(1, filter.getMaxStackSize());
        int amount = button == 1 ? Math.max(1, Math.min(total, maxStack) / 2) : maxStack;
        ItemStack extracted = toolbox.extractFromCompartment(compartment, amount, false);
        if (!extracted.isEmpty()) {
            menu.setCarried(extracted);
            return true;
        }
        return false;
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
