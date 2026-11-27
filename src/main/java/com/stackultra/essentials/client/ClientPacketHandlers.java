package com.stackultra.essentials.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleToolboxState(int lockedHotbarMask,
                                          boolean restockActive,
                                          boolean hasBackpack,
                                          List<ItemStack> displayItems,
                                          List<Integer> counts) {
        PortableToolboxOverlay.updateState(lockedHotbarMask, restockActive, hasBackpack, displayItems, counts);
    }
}
