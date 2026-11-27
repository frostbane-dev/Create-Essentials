package com.stackultra.essentials.content;

import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Shared kinetic charging progression, particle/chime feedback, and Goggles tooltip lines
 * for placed Create: Essentials kinetic chargers (magnets and magnetic backpacks).
 */
public final class KineticChargeHelper {

    public static final float SPEED_DIVISOR = 10.0F;

    public record TickResult(int nextCharge, boolean chargeChanged, boolean activelyCharging, boolean reachedFull) {
    }

    public static final class KineticChargeState {
        private float chargeAccumulator = 0F;
        private boolean wasFull = false;

        public void syncFullState(int charge, int maxCharge) {
            this.wasFull = charge >= maxCharge;
        }

        public TickResult tick(int currentCharge, int maxCharge, float speed) {
            int safeMax = Math.max(0, maxCharge);
            int charge = Math.clamp(currentCharge, 0, safeMax);
            boolean chargeChanged = false;
            boolean activelyCharging = false;

            if (speed != 0 && charge < safeMax) {
                activelyCharging = true;
                chargeAccumulator += Math.abs(speed) / SPEED_DIVISOR;
                int add = (int) chargeAccumulator;
                if (add > 0) {
                    charge = Math.min(safeMax, charge + add);
                    chargeAccumulator -= add;
                    chargeChanged = true;
                }
            } else {
                chargeAccumulator = 0F;
            }

            boolean reachedFull = false;
            if (charge >= safeMax) {
                if (!wasFull) {
                    wasFull = true;
                    reachedFull = true;
                }
            } else {
                wasFull = false;
            }

            return new TickResult(charge, chargeChanged, activelyCharging, reachedFull);
        }
    }

    private KineticChargeHelper() {
    }

    public static void spawnChargingParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (int i = 0; i < 2; i++) {
            double yaw = serverLevel.random.nextDouble() * Math.PI * 2.0;
            double pitch = (serverLevel.random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            double r = 0.8;
            double x = cx + dx * r;
            double y = cy + dy * r;
            double z = cz + dz * r;
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, -dx * 0.15, -dy * 0.15, -dz * 0.15, 1.0);
        }
    }

    public static void spawnFullChargeParticles(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double cx = pos.getX() + 0.5;
        double cy = pos.getY() + 0.5;
        double cz = pos.getZ() + 0.5;
        for (int i = 0; i < 24; i++) {
            double yaw = serverLevel.random.nextDouble() * Math.PI * 2.0;
            double pitch = (serverLevel.random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            double r = 0.55;
            double x = cx + dx * r;
            double y = cy + dy * r;
            double z = cz + dz * r;
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, dx * 0.25, dy * 0.25, dz * 0.25, 1.0);
        }
        serverLevel.playSound(null, cx, cy, cz, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    public static void appendGoggleTooltip(List<Component> tooltip,
                                           boolean enabled,
                                           float speed,
                                           int charge,
                                           int maxCharge) {
        CreateLang.builder()
                .add(Component.translatable(enabled
                        ? "createessentials.gui.enabled"
                        : "createessentials.gui.disabled"))
                .style(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        if (speed != 0 && charge < maxCharge) {
            CreateLang.builder()
                    .add(ChargeDisplay.chargingLine())
                    .forGoggles(tooltip, 1);
        }

        CreateLang.builder()
                .add(ChargeDisplay.charge(charge, maxCharge))
                .forGoggles(tooltip, 1);
    }
}
