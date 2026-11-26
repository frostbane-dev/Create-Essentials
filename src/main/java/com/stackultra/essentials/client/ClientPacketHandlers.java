package com.stackultra.essentials.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientPacketHandlers {

    private ClientPacketHandlers() {
    }

    public static void handleToolboxLinks(int lockedHotbarMask, boolean restockActive) {
        PortableToolboxOverlay.updateState(lockedHotbarMask, restockActive);
    }
}
