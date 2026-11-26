package com.stackultra.essentials.content;

import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.player.Player;

import java.util.function.BooleanSupplier;

public final class GogglesHelper {

    private static BooleanSupplier viewerCheck = () -> false;

    private GogglesHelper() {
    }

    public static void registerViewerCheck(BooleanSupplier check) {
        viewerCheck = check;
    }

    public static boolean isViewerWearingGoggles() {
        return viewerCheck.getAsBoolean();
    }

    public static boolean isWearingGoggles(Player player) {
        return GogglesItem.isWearingGoggles(player);
    }
}
