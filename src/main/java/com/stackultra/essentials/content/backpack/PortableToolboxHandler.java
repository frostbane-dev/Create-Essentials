package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.compat.curios.CuriosBackpackIntegration;
import com.stackultra.essentials.network.SyncToolboxLinksPayload;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative portable Create Toolbox controller:
 * - Deterministic backpack targeting (Curios -> Chest -> Main hand -> Offhand -> Inventory)
 * - Always targets the player's active hotbar slot (0-8) and NEVER forces items into offhand
 * - 32-item equip and automatic hotbar refill while Restock is ON
 * - Safe return-to-compartment with zero loss/duplication
 * - Full Create ExtendoGripItem compatibility (never touches offhand Extendo Grip)
 * - Syncs locked hotbar slot bitmask to client for Create-style hotbar HUD indicator
 */
@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public final class PortableToolboxHandler {

    private record ToolboxLink(int compartment, ItemStack itemTemplate) {
    }

    private record SyncedLinkState(int mask, boolean restockActive) {
    }

    private static final Map<UUID, Map<Integer, ToolboxLink>> ACTIVE_LINKS = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncedLinkState> LAST_SYNCED = new ConcurrentHashMap<>();

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
        int targetSlot = (requestedHotbarSlot >= 0 && requestedHotbarSlot < Inventory.getSelectionSize())
                ? requestedHotbarSlot
                : player.getInventory().selected;

        ItemStack backpack = findPortableToolboxBackpack(player);
        if (backpack.isEmpty())
            return;

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        if (toolbox.getTotalCount(compartment) <= 0)
            return;
        ItemStack template = toolbox.getTemplate(compartment);
        if (template.isEmpty())
            return;

        // Strictly target the selected hotbar slot (0-8); never overwrite an Extendo Grip or the backpack itself,
        // and never redirect into offhand (offhand Extendo Grip remains completely untouched).
        ItemStack current = player.getInventory().getItem(targetSlot);
        if (isExtendoGrip(current) || current == backpack)
            return;

        int targetCount = PortableToolboxInventory.equipTargetCount(template);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

        // Case 1: Selected hotbar slot already holds the same item -> top up toward min(32, maxStackSize)
        if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, template)) {
            int needed = targetCount - current.getCount();
            if (needed > 0) {
                ItemStack pulled = toolbox.extractFromCompartment(compartment, needed, false);
                if (!pulled.isEmpty()) {
                    current.grow(pulled.getCount());
                    player.getInventory().setItem(targetSlot, current);
                }
            }
            links.put(targetSlot, new ToolboxLink(compartment, template.copyWithCount(1)));
            syncBackpackAndPlayer(player, backpack);
            pushLinkState(player, backpack, true);
            return;
        }

        // Case 2: Selected hotbar slot holds a different item -> try returning to its previous compartment
        // or moving to a free inventory slot before equipping.
        if (!current.isEmpty()) {
            ItemStack toDisplace = current.copy();
            ToolboxLink prevLink = links.get(targetSlot);
            if (prevLink != null && toolbox.canAccept(prevLink.compartment(), toDisplace)) {
                toDisplace = toolbox.insertIntoCompartment(prevLink.compartment(), toDisplace, false);
            }
            if (!toDisplace.isEmpty()) {
                int freeSlot = findEmptyMainInventorySlot(player.getInventory(), targetSlot);
                if (freeSlot == -1) {
                    player.getInventory().setItem(targetSlot, toDisplace);
                    syncBackpackAndPlayer(player, backpack);
                    pushLinkState(player, backpack, true);
                    return;
                }
                player.getInventory().setItem(freeSlot, toDisplace);
            }
            player.getInventory().setItem(targetSlot, ItemStack.EMPTY);
        }

        ItemStack pulled = toolbox.extractFromCompartment(compartment, targetCount, false);
        if (!pulled.isEmpty()) {
            player.getInventory().setItem(targetSlot, pulled);
            links.put(targetSlot, new ToolboxLink(compartment, template.copyWithCount(1)));
        } else {
            links.remove(targetSlot);
        }
        syncBackpackAndPlayer(player, backpack);
        pushLinkState(player, backpack, true);
    }

    public static void returnHeldToToolbox(ServerPlayer player, int requestedHotbarSlot) {
        int targetSlot = (requestedHotbarSlot >= 0 && requestedHotbarSlot < Inventory.getSelectionSize())
                ? requestedHotbarSlot
                : player.getInventory().selected;

        ItemStack backpack = findPortableToolboxBackpack(player);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        ToolboxLink link = links != null ? links.remove(targetSlot) : null;

        if (backpack.isEmpty()) {
            pushLinkState(player, ItemStack.EMPTY, true);
            return;
        }

        ItemStack held = player.getInventory().getItem(targetSlot);
        if (held.isEmpty() || isExtendoGrip(held) || held == backpack) {
            pushLinkState(player, backpack, true);
            return;
        }

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        ItemStack remaining = held.copy();
        int compartment = -1;
        if (link != null && toolbox.canAccept(link.compartment(), remaining)) {
            compartment = link.compartment();
        } else {
            compartment = toolbox.findMatchingCompartment(remaining);
        }

        if (compartment < 0) {
            pushLinkState(player, backpack, true);
            return;
        }

        remaining = toolbox.insertIntoCompartment(compartment, remaining, false);
        if (remaining.getCount() != held.getCount() || remaining.isEmpty()) {
            player.getInventory().setItem(targetSlot, remaining);
            syncBackpackAndPlayer(player, backpack);
        }
        pushLinkState(player, backpack, true);
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
        if (backpack.isEmpty()) {
            links.clear();
            pushLinkState(serverPlayer, ItemStack.EMPTY, false);
            return;
        }

        boolean restockEnabled = backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        boolean anyItemsChanged = false;

        Iterator<Map.Entry<Integer, ToolboxLink>> it = links.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ToolboxLink> entry = it.next();
            int slotKey = entry.getKey();
            ToolboxLink link = entry.getValue();

            if (slotKey < 0 || slotKey >= Inventory.getSelectionSize()) {
                it.remove();
                continue;
            }

            ItemStack current = player.getInventory().getItem(slotKey);
            if (isExtendoGrip(current) || current == backpack) {
                it.remove();
                continue;
            }

            ItemStack compTemplate = toolbox.getTemplate(link.compartment());
            int compCount = toolbox.getTotalCount(link.compartment());

            // Compartment was replaced with a different item -> unlink
            if (!compTemplate.isEmpty()
                    && !ItemStack.isSameItemSameComponents(compTemplate, link.itemTemplate())) {
                it.remove();
                continue;
            }

            // Player manually placed a different item into this hotbar slot -> unlink
            if (!current.isEmpty()
                    && !ItemStack.isSameItemSameComponents(current, link.itemTemplate())) {
                it.remove();
                continue;
            }

            // Both the hotbar slot and the linked compartment are now empty -> unlink cleanly
            if (current.isEmpty() && compCount <= 0) {
                it.remove();
                continue;
            }

            if (!restockEnabled || compCount <= 0)
                continue;

            int targetCount = PortableToolboxInventory.equipTargetCount(link.itemTemplate());
            int currentCount = current.isEmpty() ? 0 : current.getCount();
            int needed = targetCount - currentCount;
            if (needed <= 0)
                continue;

            ItemStack pulled = toolbox.extractFromCompartment(link.compartment(), needed, false);
            if (pulled.isEmpty())
                continue;

            if (current.isEmpty()) {
                player.getInventory().setItem(slotKey, pulled);
            } else {
                current.grow(pulled.getCount());
                player.getInventory().setItem(slotKey, current);
            }
            anyItemsChanged = true;
        }

        if (anyItemsChanged) {
            syncBackpackAndPlayer(serverPlayer, backpack);
        }
        pushLinkState(serverPlayer, backpack, false);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack backpack = findPortableToolboxBackpack(serverPlayer);
            pushLinkState(serverPlayer, backpack, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_LINKS.remove(uuid);
        LAST_SYNCED.remove(uuid);
    }

    public static void notifyRestockToggled(ServerPlayer player, ItemStack backpack) {
        pushLinkState(player, backpack, true);
    }

    private static void pushLinkState(ServerPlayer player, ItemStack backpack, boolean force) {
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        int mask = 0;
        if (links != null) {
            for (int slot : links.keySet()) {
                if (slot >= 0 && slot < Inventory.getSelectionSize()) {
                    mask |= (1 << slot);
                }
            }
        }
        boolean restockActive = !backpack.isEmpty()
                && backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        SyncedLinkState next = new SyncedLinkState(mask, restockActive);
        SyncedLinkState prev = LAST_SYNCED.get(player.getUUID());
        if (force || !next.equals(prev)) {
            LAST_SYNCED.put(player.getUUID(), next);
            PacketDistributor.sendToPlayer(player, new SyncToolboxLinksPayload(mask, restockActive));
        }
    }

    private static PortableToolboxInventory resolveLiveToolbox(Player player, ItemStack backpack) {
        if (player.containerMenu instanceof IBackpackMenu openMenu
                && openMenu.isBackingStack(backpack)
                && openMenu.getToolboxInventory() != null) {
            return openMenu.getToolboxInventory();
        }
        return PortableToolboxInventory.forStack(backpack);
    }

    private static int findEmptyMainInventorySlot(Inventory inv, int excludedSlot) {
        for (int i = Inventory.getSelectionSize(); i < inv.items.size(); i++) {
            if (inv.items.get(i).isEmpty())
                return i;
        }
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (i != excludedSlot && inv.items.get(i).isEmpty())
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
