package com.stackultra.essentials.content;

import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntConsumer;

/**
 * Stabilized Backtank recharge/drain controller for carried magnets and magnetic backpacks.
 * <p>
 * Guarantees:
 * <ul>
 *   <li>When an item is enabled and a Backtank with air is available, the Backtank directly sustains
 *       the 1-tick operational drain in the same tick (and tops up below-max items), keeping charge
 *       steady at {@code max} ({@code 05:00 / 05:00}) with zero {@code 04:59 <-> 05:00} flickering.</li>
 *   <li>Uses per-stack fractional air remainders (never leaking across players/items) and a
 *       {@link #HYSTERESIS_TICKS}-tick hysteresis window so the {@code CHARGING} flag never oscillates.</li>
 * </ul>
 */
public final class BacktankChargeController {

    public static final int BACKTANK_RECHARGE_PER_TICK = 15;
    public static final int HYSTERESIS_TICKS = 25;

    private static final class StackChargeState {
        double airRemainder = 0D;
        int hysteresisTicks = 0;
    }

    public record StepResult(int nextCharge, boolean charging, boolean activePower, int airConsumed) {
    }

    private static final Map<ItemStack, StackChargeState> STATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private BacktankChargeController() {
    }

    /**
     * Advances a single server tick of Backtank recharge, active sustain, or operational drain
     * for the given item stack.
     *
     * @param stack           the carried magnet or magnetic backpack stack (used as identity key)
     * @param currentCharge   current charge in ticks ({@code 0..maxCharge})
     * @param maxCharge       maximum charge in ticks
     * @param enabled         whether the magnet suction is currently turned ON
     * @param availableAir    total available Backtank air across the player's air sources
     * @param referenceMaxAir reference capacity of a single Backtank (for proportional air cost)
     * @param airConsumer     callback invoked with the integer air units to consume (if {@code > 0})
     * @return stabilized next charge, charging state, whether suction may run this tick, and air consumed
     */
    public static StepResult step(ItemStack stack,
                                  int currentCharge,
                                  int maxCharge,
                                  boolean enabled,
                                  int availableAir,
                                  int referenceMaxAir,
                                  IntConsumer airConsumer) {
        if (maxCharge <= 0)
            return new StepResult(0, false, false, 0);

        StackChargeState state = STATES.computeIfAbsent(stack, k -> new StackChargeState());
        int charge = Math.clamp(currentCharge, 0, maxCharge);
        boolean transferred = false;
        int consumed = 0;

        boolean hasAir = availableAir > 0 && referenceMaxAir > 0;
        boolean canOperate = enabled && (charge > 0 || hasAir);

        if (hasAir) {
            if (charge < maxCharge) {
                int before = charge;
                charge = Math.min(maxCharge, charge + BACKTANK_RECHARGE_PER_TICK);
                int chargeDelta = charge - before;
                // Account for both the refill delta and active sustain (if enabled)
                int airChargeUnits = enabled ? (chargeDelta + 1) : chargeDelta;
                consumed = consumeProportionalAir(state, airChargeUnits, maxCharge, availableAir, referenceMaxAir, airConsumer);
                transferred = true;
            } else if (enabled) {
                // Already at 100% charge and enabled: sustain directly from Backtank so charge holds steady at max
                consumed = consumeProportionalAir(state, 1, maxCharge, availableAir, referenceMaxAir, airConsumer);
                transferred = true;
            }
        } else if (enabled && charge > 0) {
            charge = Math.max(0, charge - 1);
        }

        boolean charging;
        if (transferred) {
            state.hysteresisTicks = HYSTERESIS_TICKS;
            charging = true;
        } else if (state.hysteresisTicks > 0) {
            state.hysteresisTicks--;
            charging = true;
        } else {
            charging = false;
        }

        return new StepResult(Math.clamp(charge, 0, maxCharge), charging, canOperate && charge > 0, consumed);
    }

    private static int consumeProportionalAir(StackChargeState state,
                                              int chargeUnits,
                                              int maxCharge,
                                              int availableAir,
                                              int referenceMaxAir,
                                              IntConsumer airConsumer) {
        if (chargeUnits <= 0 || availableAir <= 0 || maxCharge <= 0 || referenceMaxAir <= 0)
            return 0;
        state.airRemainder += (chargeUnits / (double) maxCharge) * referenceMaxAir;
        int consume = Math.min((int) state.airRemainder, availableAir);
        if (consume > 0) {
            state.airRemainder -= consume;
            if (airConsumer != null)
                airConsumer.accept(consume);
        }
        return consume;
    }

    public static void clearState(ItemStack stack) {
        STATES.remove(stack);
    }
}
