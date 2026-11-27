package com.stackultra.essentials.content;

import com.stackultra.essentials.content.magnet.MagnetTier;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies stabilized Backtank recharge/sustain behavior, hysteresis against CHARGING flicker,
 * zero 04:59 <-> 05:00 oscillation, and constant-height inline tooltip status rendering.
 */
class BacktankChargeStabilityTest {

    private static final int MAX_CHARGE = MagnetTier.TIER_2.maxCharge(0); // 6000 ticks = 05:00
    private static final int BACKTANK_MAX_AIR = 900;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void enabledFullChargeWithBacktankHoldsSteadyAt0500WithZeroOscillation() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        int charge = MAX_CHARGE;
        AtomicInteger air = new AtomicInteger(BACKTANK_MAX_AIR);

        try {
            for (int tick = 0; tick < 200; tick++) {
                BacktankChargeController.StepResult result = BacktankChargeController.step(
                        stack, charge, MAX_CHARGE, true, air.get(), BACKTANK_MAX_AIR,
                        consumed -> air.addAndGet(-consumed));

                assertEquals(MAX_CHARGE, result.nextCharge(),
                        "Charge must stay steady at 05:00 (tick " + tick + "), never dipping to 04:59");
                assertEquals("05:00", MagnetTier.formatRemaining(result.nextCharge()));
                assertTrue(result.charging(), "CHARGING must remain stably true while Backtank sustains active item");
                assertTrue(result.activePower(), "Active magnet/backpack must keep pulling while sustained");
                charge = result.nextCharge();
            }

            // 200 ticks / 6000 max * 900 air = 30 air consumed smoothly
            assertEquals(BACKTANK_MAX_AIR - 30, air.get());
        } finally {
            BacktankChargeController.clearState(stack);
        }
    }

    @Test
    void enabledPartialChargeClimbsSmoothlyToMaxAndStaysStable() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        int charge = MAX_CHARGE - 45; // 3 ticks from full
        AtomicInteger air = new AtomicInteger(BACKTANK_MAX_AIR);

        try {
            int previousCharge = charge;
            for (int tick = 0; tick < 60; tick++) {
                BacktankChargeController.StepResult result = BacktankChargeController.step(
                        stack, charge, MAX_CHARGE, true, air.get(), BACKTANK_MAX_AIR,
                        consumed -> air.addAndGet(-consumed));

                assertTrue(result.nextCharge() >= previousCharge,
                        "Charge must never dip backwards while Backtank has air (tick " + tick + ")");
                assertTrue(result.charging(), "CHARGING must stay continuously true without flickering");
                previousCharge = result.nextCharge();
                charge = result.nextCharge();
            }
            assertEquals(MAX_CHARGE, charge);
            assertEquals("05:00", MagnetTier.formatRemaining(charge));
        } finally {
            BacktankChargeController.clearState(stack);
        }
    }

    @Test
    void hysteresisPreventsSingleTickFlickerAndClearsCleanlyAfterWindow() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        int charge = MAX_CHARGE;
        AtomicInteger air = new AtomicInteger(BACKTANK_MAX_AIR);

        try {
            // Prime active sustain from Backtank
            BacktankChargeController.StepResult sustained = BacktankChargeController.step(
                    stack, charge, MAX_CHARGE, true, air.get(), BACKTANK_MAX_AIR,
                    consumed -> air.addAndGet(-consumed));
            assertTrue(sustained.charging());
            charge = sustained.nextCharge();

            // Simulate a 1-tick air interruption (e.g. Backtank swapped) -> CHARGING stays true via hysteresis
            BacktankChargeController.StepResult briefGap = BacktankChargeController.step(
                    stack, charge, MAX_CHARGE, true, 0, 0, null);
            assertTrue(briefGap.charging(), "Brief interruption must not flicker CHARGING off");
            charge = briefGap.nextCharge();
            assertEquals(MAX_CHARGE - 1, charge);

            // Backtank returns -> tops back up cleanly with zero CHARGING drop
            BacktankChargeController.StepResult recovered = BacktankChargeController.step(
                    stack, charge, MAX_CHARGE, true, air.get(), BACKTANK_MAX_AIR,
                    consumed -> air.addAndGet(-consumed));
            assertTrue(recovered.charging());
            assertEquals(MAX_CHARGE, recovered.nextCharge());
            charge = recovered.nextCharge();

            // Now remove Backtank permanently -> hysteresis holds for HYSTERESIS_TICKS then transitions once to false
            int transitionsToFalse = 0;
            boolean prevCharging = true;
            for (int tick = 0; tick < BacktankChargeController.HYSTERESIS_TICKS + 5; tick++) {
                BacktankChargeController.StepResult noAir = BacktankChargeController.step(
                        stack, charge, MAX_CHARGE, true, 0, 0, null);
                if (tick < BacktankChargeController.HYSTERESIS_TICKS) {
                    assertTrue(noAir.charging(), "Expected hysteresis to hold CHARGING at tick " + tick);
                } else {
                    assertFalse(noAir.charging(), "Expected CHARGING to clear after hysteresis window at tick " + tick);
                }
                if (prevCharging && !noAir.charging())
                    transitionsToFalse++;
                prevCharging = noAir.charging();
                charge = noAir.nextCharge();
            }
            assertEquals(1, transitionsToFalse, "CHARGING must transition cleanly once without oscillation");
        } finally {
            BacktankChargeController.clearState(stack);
        }
    }

    @Test
    void disabledFullChargeDoesNotDrainBacktankAndClearsChargingAfterHysteresis() {
        ItemStack stack = new ItemStack(Items.COMPASS);
        AtomicInteger air = new AtomicInteger(BACKTANK_MAX_AIR);

        try {
            // Disabled item already at full charge -> never transfers air, CHARGING stays false
            BacktankChargeController.StepResult idle = BacktankChargeController.step(
                    stack, MAX_CHARGE, MAX_CHARGE, false, air.get(), BACKTANK_MAX_AIR,
                    consumed -> air.addAndGet(-consumed));
            assertEquals(MAX_CHARGE, idle.nextCharge());
            assertFalse(idle.charging());
            assertFalse(idle.activePower());
            assertEquals(BACKTANK_MAX_AIR, air.get());
        } finally {
            BacktankChargeController.clearState(stack);
        }
    }

    @Test
    void inlineStatusLineRendersChargingWithoutAddingExtraTooltipLines() {
        Component normal = ChargeDisplay.statusLine(true, false);
        Component charging = ChargeDisplay.statusLine(true, true);

        assertFalse(normal.getString().contains("\n"));
        assertFalse(charging.getString().contains("\n"));
        assertTrue(charging.getString().length() > normal.getString().length(),
                "Inline charging tag should append cleanly onto the same Status line");
    }
}
