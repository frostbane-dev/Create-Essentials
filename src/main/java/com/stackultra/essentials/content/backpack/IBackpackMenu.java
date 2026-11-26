package com.stackultra.essentials.content.backpack;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public interface IBackpackMenu {

    SortMode getSortMode();

    void setSortMode(SortMode mode, Player player);

    void transferMatchingToStorage(Player player);

    void transferMatchingToInventory(Player player);

    void sortStorage(Player player);

    boolean isRestockEnabled();

    void setRestockEnabled(boolean enabled, Player player);

    int getToolboxCount(int compartment);

    @Nullable
    PortableToolboxInventory getToolboxInventory();

    boolean isBackingStack(ItemStack stack);
}
