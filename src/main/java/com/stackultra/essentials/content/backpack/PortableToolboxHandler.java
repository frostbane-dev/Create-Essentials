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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-authoritative portable Create Toolbox controller:
 * - Deterministic backpack targeting (Curios -> Chest -> Main hand -> Offhand -> Inventory)
 * - Always targets player hotbar slots (0-8) and NEVER forces items into offhand
 * - 32-item equip and automatic hotbar refill while Restock is ON (preserving links across empty ghost-reserved slots)
 * - Automatic link re-binding when items are moved or sorted between compartments
 * - Safe return-to-compartment with zero loss/duplication
 * - Full Create ExtendoGripItem compatibility (never touches offhand Extendo Grip)
 * - Syncs locked hotbar bitmask, Restock state, and live 8-compartment snapshot to client in real time
 */
@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public final class PortableToolboxHandler {

    record ToolboxLink(int compartment, ItemStack itemTemplate) {
    }

    private record SyncedToolboxState(
            int mask,
            boolean restockActive,
            boolean hasBackpack,
            List<ItemStack> displayItems,
            List<Integer> counts
    ) {
        boolean sameAs(@Nullable SyncedToolboxState other) {
            if (other == null)
                return false;
            if (mask != other.mask || restockActive != other.restockActive || hasBackpack != other.hasBackpack)
                return false;
            if (!counts.equals(other.counts))
                return false;
            if (displayItems.size() != other.displayItems.size())
                return false;
            for (int i = 0; i < displayItems.size(); i++) {
                if (!ItemStack.matches(displayItems.get(i), other.displayItems.get(i)))
                    return false;
            }
            return true;
        }
    }

    private static final Map<UUID, Map<Integer, ToolboxLink>> ACTIVE_LINKS = new ConcurrentHashMap<>();
    private static final Map<UUID, SyncedToolboxState> LAST_SYNCED = new ConcurrentHashMap<>();

    private PortableToolboxHandler() {
    }

    /**
     * Finds the player's active backpack for portable toolbox & keybind operations.
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
        if (backpack.isEmpty()) {
            pushToolboxState(player, ItemStack.EMPTY, null, true);
            return;
        }

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        if (toolbox.getTotalCount(compartment) <= 0) {
            // Client may have been momentarily out of sync; push authoritative state back immediately
            pushToolboxState(player, backpack, toolbox, true);
            return;
        }
        ItemStack template = toolbox.getTemplate(compartment);
        if (template.isEmpty()) {
            pushToolboxState(player, backpack, toolbox, true);
            return;
        }

        // Never overwrite an Extendo Grip or the backpack itself, and never redirect into offhand.
        // If the selected slot holds the backpack itself, fall back to an empty hotbar slot (0-8) if available.
        ItemStack current = player.getInventory().getItem(targetSlot);
        if (isExtendoGrip(current))
            return;
        if (current == backpack) {
            int emptyHotbar = findEmptyHotbarSlot(player.getInventory(), targetSlot);
            if (emptyHotbar < 0)
                return;
            targetSlot = emptyHotbar;
            current = ItemStack.EMPTY;
        }

        int targetCount = PortableToolboxInventory.equipTargetCount(template);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());

        // Case 1: Target hotbar slot already holds the same item -> top up toward min(32, maxStackSize)
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
            pushToolboxState(player, backpack, toolbox, true);
            return;
        }

        // Case 2: Target hotbar slot holds a different item -> try returning to its previous/matching compartment
        // or moving to a free inventory slot before equipping.
        if (!current.isEmpty()) {
            ItemStack toDisplace = current.copy();
            ToolboxLink prevLink = links.get(targetSlot);
            if (prevLink != null && toolbox.canAccept(prevLink.compartment(), toDisplace)) {
                toDisplace = toolbox.insertIntoCompartment(prevLink.compartment(), toDisplace, false);
            }
            if (!toDisplace.isEmpty()) {
                int matchingComp = toolbox.findMatchingCompartment(toDisplace);
                if (matchingComp >= 0 && toolbox.canAccept(matchingComp, toDisplace)) {
                    toDisplace = toolbox.insertIntoCompartment(matchingComp, toDisplace, false);
                }
            }
            if (!toDisplace.isEmpty()) {
                int freeSlot = findEmptyMainInventorySlot(player.getInventory(), targetSlot);
                if (freeSlot == -1) {
                    player.getInventory().setItem(targetSlot, toDisplace);
                    syncBackpackAndPlayer(player, backpack);
                    pushToolboxState(player, backpack, toolbox, true);
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
        pushToolboxState(player, backpack, toolbox, true);
    }

    public static void returnHeldToToolbox(ServerPlayer player, int requestedHotbarSlot) {
        int targetSlot = (requestedHotbarSlot >= 0 && requestedHotbarSlot < Inventory.getSelectionSize())
                ? requestedHotbarSlot
                : player.getInventory().selected;

        ItemStack backpack = findPortableToolboxBackpack(player);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        ToolboxLink link = links != null ? links.remove(targetSlot) : null;

        if (backpack.isEmpty()) {
            pushToolboxState(player, ItemStack.EMPTY, null, true);
            return;
        }

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        ItemStack held = player.getInventory().getItem(targetSlot);
        if (held.isEmpty() || isExtendoGrip(held) || held == backpack) {
            pushToolboxState(player, backpack, toolbox, true);
            return;
        }

        ItemStack remaining = held.copy();
        int compartment = -1;
        if (link != null && toolbox.canAccept(link.compartment(), remaining)) {
            compartment = link.compartment();
        } else {
            compartment = toolbox.findMatchingCompartment(remaining);
        }

        if (compartment < 0) {
            pushToolboxState(player, backpack, toolbox, true);
            return;
        }

        remaining = toolbox.insertIntoCompartment(compartment, remaining, false);
        if (remaining.getCount() != held.getCount() || remaining.isEmpty()) {
            player.getInventory().setItem(targetSlot, remaining);
            syncBackpackAndPlayer(player, backpack);
        }
        pushToolboxState(player, backpack, toolbox, true);
    }

    /**
     * Called immediately after any server-side toolbox mutation (GUI slot click, transfer arrows,
     * sort, menu close, or magnetic backpack suction) to re-bind moved compartment links and push
     * authoritative live state to the client with zero delay.
     */
    public static void onToolboxModified(ServerPlayer player,
                                         @Nullable ItemStack modifiedBackpack,
                                         @Nullable PortableToolboxInventory modifiedToolbox) {
        ItemStack activeBackpack = findPortableToolboxBackpack(player);
        if (activeBackpack.isEmpty()) {
            Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
            if (links != null)
                links.clear();
            pushToolboxState(player, ItemStack.EMPTY, null, true);
            return;
        }

        PortableToolboxInventory liveToolbox = (modifiedToolbox != null && modifiedBackpack == activeBackpack)
                ? modifiedToolbox
                : resolveLiveToolbox(player, activeBackpack);

        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        if (links != null && !links.isEmpty()) {
            rebindMovedLinks(links, liveToolbox);
        }
        pushToolboxState(player, activeBackpack, liveToolbox, true);
    }

    /**
     * Handles an explicit client sync request (sent when opening the Alt radial wheel) so the wheel
     * always displays authoritative, up-to-date server compartment contents and positions.
     */
    public static void handleSyncRequest(ServerPlayer player) {
        ItemStack backpack = findPortableToolboxBackpack(player);
        if (backpack.isEmpty()) {
            pushToolboxState(player, ItemStack.EMPTY, null, true);
            return;
        }
        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        if (links != null && !links.isEmpty()) {
            rebindMovedLinks(links, toolbox);
        }
        pushToolboxState(player, backpack, toolbox, true);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer))
            return;
        if (player.isSpectator())
            return;

        ItemStack backpack = findPortableToolboxBackpack(player);
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());

        if (backpack.isEmpty()) {
            if (links != null && !links.isEmpty())
                links.clear();
            pushToolboxState(serverPlayer, ItemStack.EMPTY, null, false);
            return;
        }

        PortableToolboxInventory toolbox = resolveLiveToolbox(player, backpack);

        if (links == null || links.isEmpty() || player.isCreative()) {
            pushToolboxState(serverPlayer, backpack, toolbox, false);
            return;
        }

        rebindMovedLinks(links, toolbox);

        boolean restockEnabled = backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
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

            ItemStack compFilter = toolbox.getFilter(link.compartment());
            int compCount = toolbox.getTotalCount(link.compartment());

            // Compartment filter was cleared or replaced with a different item -> unlink
            if (compFilter.isEmpty()
                    || !ItemStack.isSameItemSameComponents(compFilter, link.itemTemplate())) {
                it.remove();
                continue;
            }

            // Player manually placed a different item into this hotbar slot -> unlink
            if (!current.isEmpty()
                    && !ItemStack.isSameItemSameComponents(current, link.itemTemplate())) {
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
        pushToolboxState(serverPlayer, backpack, toolbox, false);
    }

    /**
     * Re-binds active hotbar links if the player moved or sorted items between compartments
     * (e.g. moving 256 items from slot 0 to slot 4), preventing stale compartment references.
     */
    static void rebindMovedLinks(Map<Integer, ToolboxLink> links, PortableToolboxInventory toolbox) {
        Iterator<Map.Entry<Integer, ToolboxLink>> it = links.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ToolboxLink> entry = it.next();
            ToolboxLink link = entry.getValue();
            int comp = link.compartment();
            ItemStack compFilter = toolbox.getFilter(comp);
            boolean currentMatches = !compFilter.isEmpty()
                    && ItemStack.isSameItemSameComponents(compFilter, link.itemTemplate());

            if (currentMatches) {
                // If old compartment is now empty (ghost-reserved) while another compartment holds
                // physical items of the same type (e.g. player moved items from slot 1 to slot 5),
                // re-bind to the populated compartment so restock and return follow the moved stack.
                if (toolbox.getTotalCount(comp) <= 0) {
                    int populated = findPopulatedCompartment(toolbox, link.itemTemplate());
                    if (populated >= 0 && populated != comp) {
                        entry.setValue(new ToolboxLink(populated, link.itemTemplate()));
                    }
                }
                continue;
            }

            // Old compartment filter was cleared or replaced -> follow item if it moved elsewhere
            int relocated = findPopulatedCompartment(toolbox, link.itemTemplate());
            if (relocated < 0) {
                relocated = toolbox.findMatchingCompartment(link.itemTemplate());
            }
            if (relocated >= 0) {
                entry.setValue(new ToolboxLink(relocated, link.itemTemplate()));
            } else {
                it.remove();
            }
        }
    }

    private static int findPopulatedCompartment(PortableToolboxInventory toolbox, ItemStack template) {
        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
            if (toolbox.getTotalCount(c) <= 0)
                continue;
            ItemStack t = toolbox.getTemplate(c);
            if (!t.isEmpty() && ItemStack.isSameItemSameComponents(t, template))
                return c;
        }
        return -1;
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            ItemStack backpack = findPortableToolboxBackpack(serverPlayer);
            PortableToolboxInventory toolbox = backpack.isEmpty() ? null : resolveLiveToolbox(serverPlayer, backpack);
            pushToolboxState(serverPlayer, backpack, toolbox, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_LINKS.remove(uuid);
        LAST_SYNCED.remove(uuid);
    }

    public static void notifyRestockToggled(ServerPlayer player, ItemStack backpack) {
        PortableToolboxInventory toolbox = backpack.isEmpty() ? null : resolveLiveToolbox(player, backpack);
        pushToolboxState(player, backpack, toolbox, true);
    }

    private static void pushToolboxState(ServerPlayer player,
                                         ItemStack backpack,
                                         @Nullable PortableToolboxInventory toolbox,
                                         boolean force) {
        Map<Integer, ToolboxLink> links = ACTIVE_LINKS.get(player.getUUID());
        int mask = 0;
        if (links != null) {
            for (int slot : links.keySet()) {
                if (slot >= 0 && slot < Inventory.getSelectionSize()) {
                    mask |= (1 << slot);
                }
            }
        }
        boolean hasBackpack = !backpack.isEmpty();
        boolean restockActive = hasBackpack
                && backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);

        List<ItemStack> displayItems = new ArrayList<>(PortableToolboxInventory.COMPARTMENTS);
        List<Integer> counts = new ArrayList<>(PortableToolboxInventory.COMPARTMENTS);
        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++) {
            if (toolbox != null) {
                ItemStack display = toolbox.getDisplayTemplate(c);
                displayItems.add(display.isEmpty() ? ItemStack.EMPTY : display.copyWithCount(1));
                counts.add(toolbox.getTotalCount(c));
            } else {
                displayItems.add(ItemStack.EMPTY);
                counts.add(0);
            }
        }

        SyncedToolboxState next = new SyncedToolboxState(mask, restockActive, hasBackpack, displayItems, counts);
        SyncedToolboxState prev = LAST_SYNCED.get(player.getUUID());
        if (force || !next.sameAs(prev)) {
            LAST_SYNCED.put(player.getUUID(), next);
            PacketDistributor.sendToPlayer(player,
                    new SyncToolboxLinksPayload(mask, restockActive, hasBackpack, displayItems, counts));
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

    private static int findEmptyHotbarSlot(Inventory inv, int excludedSlot) {
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            if (i != excludedSlot && inv.items.get(i).isEmpty())
                return i;
        }
        return -1;
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
