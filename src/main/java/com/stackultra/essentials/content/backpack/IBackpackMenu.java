package com.stackultra.essentials.content.backpack;

import net.minecraft.world.entity.player.Player;

public interface IBackpackMenu {

    SortMode getSortMode();

    void setSortMode(SortMode mode, Player player);

    void transferMatchingToStorage(Player player);

    void transferMatchingToInventory(Player player);

    void sortStorage(Player player);
}
