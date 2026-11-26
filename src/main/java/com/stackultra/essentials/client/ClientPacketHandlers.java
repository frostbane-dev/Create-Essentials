package com.stackultra.essentials.client;

import com.stackultra.essentials.content.backpack.IBackpackMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleToolboxFilters(int containerId, ItemContainerContents filters) {
        Player player = Minecraft.getInstance().player;
        if (player == null)
            return;
        if (player.containerMenu.containerId == containerId && player.containerMenu instanceof IBackpackMenu menu) {
            menu.applyClientToolboxFilters(filters);
        }
    }
}
