package com.stackultra.essentials.compat.curios;

import com.stackultra.essentials.registry.AllItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

/**
 * Isolated client-side Curios renderer registration bridge.
 * Only loaded by {@link CuriosClientSetup} when {@link CuriosCompat#isLoaded()} is {@code true}.
 */
@OnlyIn(Dist.CLIENT)
final class CuriosClientBridge {

    private CuriosClientBridge() {
    }

    static void registerRenderers() {
        CuriosRendererRegistry.register(AllItems.MECHANICAL_BACKPACK.get(), BackCurioRenderer::new);
        CuriosRendererRegistry.register(AllItems.NETHERITE_BACKPACK.get(), BackCurioRenderer::new);
        CuriosRendererRegistry.register(AllItems.MAGNETIC_BACKPACK.get(), BackCurioRenderer::new);
    }
}
