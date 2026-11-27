package com.stackultra.essentials.content;

import com.stackultra.essentials.content.magnet.MagnetTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class ChargeDisplay {

    private ChargeDisplay() {
    }

    public static int percent(int charge, int max) {
        if (max <= 0)
            return 0;
        return (int) Math.round(charge * 100.0 / max);
    }

    public static Component status(boolean enabled) {
        return Component.translatable(enabled
                        ? "createessentials.gui.enabled"
                        : "createessentials.gui.disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    /**
     * Renders the item hover status line with an inline {@code (Charging)} tag when actively
     * recharging/sustained so the tooltip line count and vertical height never shift.
     */
    public static Component statusLine(boolean enabled, boolean charging) {
        var line = Component.translatable("createessentials.tooltip.status", status(enabled))
                .withStyle(ChatFormatting.GRAY);
        if (charging) {
            line.append(Component.literal(" "))
                    .append(Component.translatable("createessentials.tooltip.charging_tag")
                            .withStyle(ChatFormatting.AQUA));
        }
        return line;
    }

    public static Component chargingLine() {
        return Component.translatable("createessentials.tooltip.charging")
                .withStyle(ChatFormatting.AQUA);
    }

    public static Component charge(int charge, int max) {
        Component remaining = Component.literal(MagnetTier.formatRemaining(charge))
                .withStyle(ChatFormatting.WHITE);
        Component capacity = Component.literal(MagnetTier.formatRemaining(max))
                .withStyle(ChatFormatting.GOLD);
        Component percentage = Component.literal(percent(charge, max) + "%")
                .withStyle(ChatFormatting.AQUA);
        return Component.translatable("createessentials.tooltip.charge", remaining, capacity, percentage)
                .withStyle(ChatFormatting.GRAY);
    }
}
