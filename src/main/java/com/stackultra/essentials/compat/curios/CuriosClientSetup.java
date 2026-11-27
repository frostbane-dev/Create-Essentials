package com.stackultra.essentials.compat.curios;

import com.stackultra.essentials.CreateEssentials;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Safe client setup subscriber containing zero direct Curios class references.
 * Delegates to {@link CuriosClientBridge} only when Curios is installed.
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CreateEssentials.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class CuriosClientSetup {

    private CuriosClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!CuriosCompat.isLoaded())
            return;

        event.enqueueWork(CuriosClientBridge::registerRenderers);
    }
}
