package com.stackultra.essentials.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Zero-height tooltip width guard ensuring magnet and magnetic backpack hover tooltips
 * always reserve sufficient horizontal breathing room so "Charging..." and charge readouts
 * never clip, overflow, or touch the right tooltip border.
 */
public record TooltipWidthGuard(int minWidth) implements TooltipComponent {

    @OnlyIn(Dist.CLIENT)
    public static class Client implements ClientTooltipComponent {
        private final int minWidth;

        public Client(TooltipWidthGuard guard) {
            this.minWidth = Math.max(0, guard.minWidth());
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public int getWidth(Font font) {
            return minWidth;
        }
    }
}
