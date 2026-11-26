package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.compat.curios.CuriosBackpackIntegration;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative portable Create Toolbox controller:
 * - Deterministic backpack targeting (Curios -> Chest -> Main hand -> Offhand -> Inventory)
 * - 32-item equip and automatic hand refill while Restock is ON
 * - Safe return-to-compartment with zero loss/duplication
 * - Full Create ExtendoGripItem hand preservation
 */
@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public final class PortableToolboxHandler {

    public static final int OFFHAND_SLOT = -1;

    private record ToolboxLink(int compartment, ItemStack filterTemplate) {
    }

    private static final Map<UUID, Map<Integer, ToolboxLink>> ACTIVE_LINKS = new ConcurrentHashMap<>();

    private PortableToolboxHandler() {
    }

    /**
     * Finds the player's active backpack for portable toolbox operations.
     * Priority: Curios back slot -> Chest armor -> Main hand -> Offhand -> Player inventory.
     */
    public static ItemStack findPortableToolboxBackpack(Player player) {
        if (ModList.get().isLoaded("curios")) {
            try {
                ItemStack curio = CuriosBackpackIntegration.getBackBackpack(player);
                if (BackpackHelper.isBackpack(curio))
                    return curio;
            } catch (Throwable ignored) {
            }
        }

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (BackpackHelper.isBackpack(chest))
            return chest;

        ItemStack main = player.getMainHandItem();
        if (BackpackHelper.isBackpack(main))
            return main;

        ItemStack off = player.getOffhandItem();
        if (BackpackHelper.isBackpack(off))
            return off;

        for (ItemStack stack : player.getInventory().items) {
            if (BackpackHelper.isBackpack(stack))
                return stack;
        }

        return ItemStack.EMPTY;
    }

    public static boolean isExtendoGrip(ItemStack stack) {
        return stack != null && !stack.isEmpty() && AllItems.EXTENDO_GRIP.isIn(stack);
    }

    public static void equipFromCompartment(ServerPlayer player, int compartment, int requestedHotbarSlot) {
        if (compartment < 0 || compartment >= PortableToolboxInventory.COMPARTMENTS)
            return;
        if (requestedHotbarSlot < 0 || requestedHotbarSlot >= Inventory.getSelectionSize())
            requestedHotbarSlot = player.getInventory().selected;

        ItemStack backpack = findPortableToolboxBackpack(player);
        if (backpack.isEmpty())
            return;

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        ItemStack filter = toolbox.getFilter(compartment);
        if (filter.isEmpty())
            return;

        int targetSlot = resolveExtendoSafeSlot(player, requestedHotbarSlot, backpack);
        if (targetSlot == Integer.MIN_VALUE)
            return;

        ItemStack current = getSlotStack(player, targetSlot);
        if (isExtendoGrip(current) || current == backpack)
            return;

        int targetCount = PortableToolboxInventory.equipTargetCount(filter);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

        // Case 1: Target slot already holds the same item -> top up toward 32
        if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, filter)) {
            int needed = targetCount - current.getCount();
            if (needed > 0) {
                ItemStack pulled = toolbox.extractFromCompartment(compartment, needed, false);
                if (!pulled.isEmpty()) {
                    current.grow(pulled.getCount());
                    setSlotStack(player, targetSlot, current);
                }
            }
            links.put(targetSlot, new ToolboxLink(compartment, filter.copyWithCount(1)));
            syncBackpackAndPlayer(player, backpack);
            return;
        }

        // Case 2: Target slot holds a different item -> try returning to its old compartment or move to inventory
        if (!current.isEmpty()) {
            ItemStack toDisplace = current.copy();
            ToolboxLink prevLink = links.get(targetSlot);
            if (prevLink != null && toolbox.canAccept(prevLink.compartment(), toDisplace)) {
                toDisplace = toolbox.insertIntoCompartment(prevLink.compartment(), toDisplace, false);
            }
            if (!toDisplace.isEmpty()) {
                int freeSlot = findEmptyMainInventorySlot(player.getInventory());
                if (freeSlot == -1) {
                    // Restore any partial return? Only displace if there's room; if partial returned, keep remainder
                    setSlotStack(player, targetSlot, toDisplace);
                    syncBackpackAndPlayer(player, backpack);
                    return;
                }
                player.getInventory().setItem(freeSlot, toDisplace);
            }
            setSlotStack(player, targetSlot, ItemStack.EMPTY);
        }

        // Extract up to 32 items (or link empty slot if compartment is reserved at 0)
        ItemStack pulled = toolbox.extractFromCompartment(compartment, targetCount, false);
        if (!pulled.isEmpty()) {
            setSlotStack(player, targetSlot, pulled);
        }
        links.put(targetSlot, new ToolboxLink(compartment, filter.copyWithCount(1)));
        syncBackpackAndPlayer(player, backpack);
    }

    public static void returnHeldToToolbox(ServerPlayer player, int requestedHotbarSlot) {
        if (requestedHotbarSlot < 0 || requestedHotbarSlot >= Inventory.getSelectionSize())
            requestedHotbarSlot = player.getInventory().selected;

        ItemStack backpack = findPortableToolboxBackpack(player);
        if (backpack.isEmpty())
            return;

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        int targetSlot = resolveExtendoSafeReturnSlot(player, requestedHotbarSlot, backpack);
        if (targetSlot == Integer.MIN_VALUE)
            return;

        ItemStack held = getSlotStack(player, targetSlot);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        ToolboxLink link = links != null ? links.remove(targetSlot) : null;

        if (held.isEmpty() || isExtendoGrip(held) || held == backpack)
            return;

        ItemStack remaining = held.copy();
        int compartment = -1;
        if (link != null && toolbox.canAccept(link.compartment(), remaining)) {
            compartment = link.compartment();
        } else {
            compartment = toolbox.findMatchingCompartment(remaining);
        }

        if (compartment < 0)
            return;

        remaining = toolbox.insertIntoCompartment(compartment, remaining, false);
        if (remaining.getCount() != held.getCount() || remaining.isEmpty()) {
            setSlotStack(player, targetSlot, remaining);
            syncBackpackAndPlayer(player, backpack);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer))
            return;
        if (player.isCreative() || player.isSpectator())
            return;

        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        if (links == null || links.isEmpty())
            return;

        ItemStack backpack = findPortableToolboxBackpack(player);
        if (backpack.isEmpty())
            return;

        if (!backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true))
            return;

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        boolean anyChanged = false;

        Iterator<Map.Entry<Integer, ToolboxLink>> it = links.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ToolboxLink> entry = it.next();
            int slotKey = entry.getKey();
            ToolboxLink link = entry.getValue();

            ItemStack current = getSlotStack(player, slotKey);
            if (isExtendoGrip(current) || current == backpack) {
                it.remove();
                continue;
            }

            ItemStack activeFilter = toolbox.getFilter(link.compartment());
            if (activeFilter.isEmpty()
                    || !ItemStack.isSameItemSameComponents(activeFilter, link.filterTemplate())) {
                it.remove();
                continue;
            }

            if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, activeFilter)) {
                // Player manually swapped another item into this slot -> unlink cleanly
                it.remove();
                continue;
            }

            int targetCount = PortableToolboxInventory.equipTargetCount(activeFilter);
            int currentCount = current.isEmpty() ? 0 : current.getCount();
            int needed = targetCount - currentCount;
            if (needed <= 0)
                continue;

            ItemStack pulled = toolbox.extractFromCompartment(link.compartment(), needed, false);
            if (pulled.isEmpty())
                continue;

            if (current.isEmpty()) {
                setSlotStack(player, slotKey, pulled);
            } else {
                current.grow(pulled.getCount());
                setSlotStack(player, slotKey, current);
            }
            anyChanged = true;
        }

        if (anyChanged) {
            syncBackpackAndPlayer(serverPlayer, backpack);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE_LINKS.remove(event.getEntity().getUUID());
    }

    private static PortableToolboxInventory resolveLiveToolbox(Player player, ItemStack backpack) {
        if (player.containerMenu instanceof IBackpackMenu openMenu
                && openMenu.isBackingStack(backpack)
                && openMenu.getToolboxInventory() != null) {
            return openMenu.getToolboxInventory();
        }
        return PortableToolboxInventory.forStack(backpack);
    }

    private static int resolveExtendoSafeSlot(Player player, int requestedHotbarSlot, ItemStack backpack) {
        ItemStack requested = player.getInventory().getItem(requestedHotbarSlot);
        if (!isExtendoGrip(requested) && requested != backpack)
            return requestedHotbarSlot;

        // If main hand holds an Extendo Grip (or the backpack itself), route safely to offhand if free of Extendo Grip
        ItemStack offhand = player.getOffhandItem();
        if (!isExtendoGrip(offhand) && offhand != backpack)
            return OFFHAND_SLOT;

        // Dual Extendo Grips: pick first empty hotbar slot without touching either grip
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack candidate = player.getInventory().getItem(i);
            if (candidate.isEmpty())
                return i;
        }
        return Integer.MIN_VALUE;
    }

    private static int resolveExtendoSafeReturnSlot(Player player, int requestedHotbarSlot, ItemStack backpack) {
        ItemStack requested = player.getInventory().getItem(requestedHotbarSlot);
        if (!isExtendoGrip(requested) && requested != backpack && !requested.isEmpty())
            return requestedHotbarSlot;

        ItemStack offhand = player.getOffhandItem();
        if (!isExtendoGrip(offhand) && offhand != backpack && !offhand.isEmpty())
            return OFFHAND_SLOT;

        return isExtendoGrip(requested) ? Integer.MIN_VALUE : requestedHotbarSlot;
    }

    private static ItemStack getSlotStack(Player player, int slotKey) {
        return slotKey == OFFHAND_SLOT ? player.getOffhandItem() : player.getInventory().getItem(slotKey);
    }

    private static void setSlotStack(Player player, int slotKey, ItemStack stack) {
        if (slotKey == OFFHAND_SLOT) {
            player.setItemInHand(InteractionHand.OFF_HAND, stack);
        } else {
            player.getInventory().setItem(slotKey, stack);
        }
    }

    private static int findEmptyMainInventorySlot(Inventory inv) {
        for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
            if (inv.items.get(i).isEmpty())
                return i;
        }
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (inv.items.get(i).isEmpty())
                return i;
        }
        return -1;
    }

    private static void syncBackpackAndPlayer(ServerPlayer player, ItemStack backpack) {
        if (backpack.getItem() instanceof MagneticBackpackItem)
            MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
        else
            BackpackEquipment.syncBackpack(player, backpack);

        player.containerMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu)
            player.inventoryMenu.broadcastChanges();
    }
}
