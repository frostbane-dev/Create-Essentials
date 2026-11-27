package com.stackultra.essentials.content;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for shared kinetic charging state, safe ItemStack slot sync resolution,
 * and deduplicated filter matching logic.
 */
class CleanupAndHelpersTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void kineticChargeAccumulatesFractionalSpeedAndTriggersFullOnce() {
        KineticChargeHelper.KineticChargeState state = new KineticChargeHelper.KineticChargeState();
        int max = 5;
        int charge = 3;

        // 4 RPM -> +0.4 per tick: tick 1 (0.4), tick 2 (0.8), tick 3 (1.2 -> +1, rem 0.2)
        KineticChargeHelper.TickResult t1 = state.tick(charge, max, 4.0F);
        assertFalse(t1.chargeChanged());
        assertTrue(t1.activelyCharging());
        assertFalse(t1.reachedFull());
        assertEquals(3, t1.nextCharge());

        KineticChargeHelper.TickResult t2 = state.tick(t1.nextCharge(), max, 4.0F);
        assertFalse(t2.chargeChanged());
        assertEquals(3, t2.nextCharge());

        KineticChargeHelper.TickResult t3 = state.tick(t2.nextCharge(), max, 4.0F);
        assertTrue(t3.chargeChanged());
        assertEquals(4, t3.nextCharge());
        assertFalse(t3.reachedFull());

        // Reverse rotation (-20 RPM -> +2.0/tick) finishes to max and triggers reachedFull once
        KineticChargeHelper.TickResult t4 = state.tick(t3.nextCharge(), max, -20.0F);
        assertTrue(t4.chargeChanged());
        assertEquals(max, t4.nextCharge());
        assertTrue(t4.activelyCharging());
        assertTrue(t4.reachedFull());

        // Subsequent tick at max must NOT fire reachedFull again
        KineticChargeHelper.TickResult t5 = state.tick(t4.nextCharge(), max, 20.0F);
        assertFalse(t5.chargeChanged());
        assertFalse(t5.activelyCharging());
        assertFalse(t5.reachedFull());
    }

    @Test
    void kineticChargeSyncFullStateSuppressesSpuriousLoadBurstAndZeroSpeedResetsAccumulator() {
        KineticChargeHelper.KineticChargeState state = new KineticChargeHelper.KineticChargeState();
        state.syncFullState(100, 100);

        KineticChargeHelper.TickResult loadedFull = state.tick(100, 100, 32.0F);
        assertFalse(loadedFull.reachedFull(), "Already-full BE after load/read must not burst again");

        // Partially fill accumulator with 6 RPM (+0.6), then stop (0 RPM) to verify accumulator resets
        state.tick(50, 100, 6.0F);
        KineticChargeHelper.TickResult stopped = state.tick(50, 100, 0.0F);
        assertFalse(stopped.activelyCharging());
        assertFalse(stopped.chargeChanged());

        // Next 6 RPM tick starts fresh from 0.0 -> 0.6 (must not cross 1.0 immediately)
        KineticChargeHelper.TickResult resumed = state.tick(50, 100, 6.0F);
        assertFalse(resumed.chargeChanged());
        assertEquals(50, resumed.nextCharge());
    }

    @Test
    void stackSyncPrefersExactReferenceThenUniqueFallbackAndRefusesAmbiguity() {
        ItemStack first = new ItemStack(Items.COMPASS);
        ItemStack second = new ItemStack(Items.COMPASS);
        ItemStack other = new ItemStack(Items.IRON_INGOT);

        // Pass 1: exact reference wins even with duplicate identical items
        assertEquals(1, StackSyncHelper.findMatchingIndex(List.of(first, second, other), second));

        // Pass 2: unique component match resolves copied stack cleanly
        ItemStack named = new ItemStack(Items.COMPASS);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        ItemStack namedCopy = named.copy();
        assertEquals(1, StackSyncHelper.findMatchingIndex(List.of(first, named, other), namedCopy));

        // Pass 3: mutated copy resolves uniquely when only one stack of that item is carried
        ItemStack mutatedCopy = other.copy();
        mutatedCopy.set(DataComponents.CUSTOM_NAME, Component.literal("Updated"));
        assertEquals(2, StackSyncHelper.findMatchingIndex(List.of(first, second, other), mutatedCopy));

        // Ambiguous: two compasses and a mutated copy matching neither by reference nor components -> refuse (-1)
        ItemStack ambiguousCopy = new ItemStack(Items.COMPASS);
        ambiguousCopy.set(DataComponents.CUSTOM_NAME, Component.literal("Ambiguous"));
        assertEquals(-1, StackSyncHelper.findMatchingIndex(List.of(first, second, other), ambiguousCopy));

        // Empty / null edge cases
        assertEquals(-1, StackSyncHelper.findMatchingIndex(List.of(first), ItemStack.EMPTY));
        assertEquals(-1, StackSyncHelper.findMatchingIndex(List.of(), first));
    }

    @Test
    void filterHelperHandlesEmptyWhitelistAndBlacklistModes() {
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);

        // No filters (or only empty stacks) -> allows everything regardless of blacklist flag
        assertTrue(MagnetFilterHelper.matchesFilter(null, List.of(), diamond, false));
        assertTrue(MagnetFilterHelper.matchesFilter(null, List.of(), diamond, true));
        assertTrue(MagnetFilterHelper.matchesFilter(null, List.of(ItemStack.EMPTY, ItemStack.EMPTY), iron, true));

        // Whitelist mode with diamond filter
        List<ItemStack> filters = List.of(ItemStack.EMPTY, new ItemStack(Items.DIAMOND));
        assertTrue(MagnetFilterHelper.matchesFilter(null, filters, diamond, false));
        assertFalse(MagnetFilterHelper.matchesFilter(null, filters, iron, false));

        // Blacklist mode with diamond filter
        assertFalse(MagnetFilterHelper.matchesFilter(null, filters, diamond, true));
        assertTrue(MagnetFilterHelper.matchesFilter(null, filters, iron, true));

        // ItemStackHandler overload matches list behavior identically
        ItemStackHandler handler = new ItemStackHandler(3);
        assertTrue(MagnetFilterHelper.matchesFilter(null, handler, iron, true));
        handler.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        assertTrue(MagnetFilterHelper.matchesFilter(null, handler, diamond, false));
        assertFalse(MagnetFilterHelper.matchesFilter(null, handler, iron, false));
        assertFalse(MagnetFilterHelper.matchesFilter(null, handler, diamond, true));
        assertTrue(MagnetFilterHelper.matchesFilter(null, handler, iron, true));
    }
}
