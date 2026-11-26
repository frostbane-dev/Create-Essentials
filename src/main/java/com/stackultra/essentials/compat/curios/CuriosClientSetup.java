package com.stackultra.essentials.compat.curios;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.registry.AllItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = CreateEssentials.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class CuriosClientSetup {

    private CuriosClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (!ModList.get().isLoaded("curios"))
            return;

        event.enqueueWork(() -> {
            CuriosRendererRegistry.register(AllItems.MECHANICAL_BACKPACK.get(), BackCurioRenderer::new);
            CuriosRendererRegistry.register(AllItems.NETHERITE_BACKPACK.get(), BackCurioRenderer::new);
            CuriosRendererRegistry.register(AllItems.MAGNETIC_BACKPACK.get(), BackCurioRenderer::new);
        });
    }
}
