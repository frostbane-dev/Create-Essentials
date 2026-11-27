package com.stackultra.essentials.content;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ItemPullHelper {

    private static final double HOLD_DISTANCE_SQ = 0.04D;
    private static final double PULL_SPEED = 0.15D;
    private static final double DAMPENING = 0.85D;
    private static final double FOOT_LIFT = 0.1D;

    private ItemPullHelper() {
    }

    public static Vec3 footTarget(Player player) {
        return new Vec3(player.getX(), player.getY() + FOOT_LIFT, player.getZ());
    }

    /**
     * Returns {@code true} only for alive, non-empty {@link ItemEntity} instances whose
     * pickup delay has expired (protecting freshly player-dropped items and modded
     * {@code setNeverPickUp()} display/pedestal entities).
     */
    public static boolean canPullOrPickup(ItemEntity itemEntity) {
        return itemEntity != null
                && isPickable(itemEntity.isAlive(), itemEntity.hasPickUpDelay(), itemEntity.getItem().isEmpty());
    }

    public static boolean isPickable(boolean alive, boolean hasPickUpDelay, boolean stackEmpty) {
        return alive && !hasPickUpDelay && !stackEmpty;
    }

    public static void pullToFeet(ItemEntity itemEntity, Player player) {
        Vec3 target = footTarget(player);
        Vec3 delta = target.subtract(itemEntity.position());
        if (delta.lengthSqr() < HOLD_DISTANCE_SQ) {
            itemEntity.setDeltaMovement(Vec3.ZERO);
            itemEntity.setPos(target.x, target.y, target.z);
            return;
        }
        Vec3 motion = delta.normalize().scale(PULL_SPEED);
        itemEntity.setDeltaMovement(itemEntity.getDeltaMovement().scale(DAMPENING).add(motion));
        itemEntity.hasImpulse = true;
    }
}
