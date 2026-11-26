package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;

public class BackpackInventory extends ItemStackHandler {

    public static final int SLOTS = 27;

    private final ItemStack boundStack;
    private final Runnable onChanged;

    public BackpackInventory() {
        this(null, null);
    }

    public BackpackInventory(Runnable onChanged) {
        this(null, onChanged);
    }

    private BackpackInventory(ItemStack boundStack, Runnable onChanged) {
        super(SLOTS);
        this.boundStack = boundStack;
        this.onChanged = onChanged;
        if (boundStack != null)
            load(boundStack);
    }

    public static BackpackInventory forStack(ItemStack stack) {
        return new BackpackInventory(stack, null);
    }

    public static BackpackInventory forBlockEntity(Runnable onChanged) {
        return new BackpackInventory(onChanged);
    }

    public void load(ItemStack stack) {
        ItemContainerContents contents = stack.getOrDefault(AllDataComponents.BACKPACK_INVENTORY, ItemContainerContents.EMPTY);
        contents.copyInto(this.stacks);
    }

    public void save(ItemStack stack) {
        stack.set(AllDataComponents.BACKPACK_INVENTORY, ItemContainerContents.fromItems(this.stacks));
    }

    public NonNullList<ItemStack> getStacks() {
        return stacks;
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (boundStack != null)
            save(boundStack);
        if (onChanged != null)
            onChanged.run();
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return !BackpackHelper.isNestedContainer(stack)
                && super.isItemValid(slot, stack);
    }
}
