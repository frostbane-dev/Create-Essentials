package com.stackultra.essentials.content;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackHelper;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents visual ghost-duplication when dropping active magnets or backpacks with {@code Q}.
 * <p>
 * Root cause in vanilla Minecraft:
 * <ol>
 *   <li>On the client, {@code LocalPlayer.drop()} optimistically clears the selected hotbar slot
 *       and sends {@code ServerboundPlayerActionPacket(DROP_ITEM)}.</li>
 *   <li>Because active magnets and magnetic backpacks mutate {@code CHARGE} on server ticks (and
 *       backpacks sync on restock/toolbox ticks), an in-flight {@code ClientboundContainerSetSlotPacket}
 *       from the server can arrive on the client <em>after</em> {@code LocalPlayer.drop()} cleared
 *       the slot, repopulating the client hotbar slot with a non-empty copy.</li>
 *   <li>When the server subsequently processes {@code DROP_ITEM} in {@code ServerPlayer.drop()},
 *       vanilla calls {@code containerMenu.setRemoteSlot(slot, EMPTY)} silently <em>without</em>
 *       sending a {@code ClientboundContainerSetSlotPacket}. Because server {@code slots} and
 *       {@code remoteSlots} now both hold {@code EMPTY}, {@code broadcastChanges()} never corrects
 *       the client, leaving a permanent non-interactable visual ghost copy in the hotbar.</li>
 * </ol>
 * <p>
 * Marking server-side {@code onDroppedByPlayer} calls and sending an authoritative
 * {@code ClientboundContainerSetSlotPacket} for the dropped hotbar slot on {@link ItemTossEvent}
 * guarantees (via TCP/Netty FIFO ordering) that the client receives the authoritative slot state
 * strictly after any in-flight tick packet.
 */
@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public final class DropSyncHandler {

    @FunctionalInterface
    interface SlotPacketSender {
        void send(int containerId, int stateId, int slotIndex, ItemStack stack);
    }

    private static final Map<UUID, Integer> PENDING_HOTBAR_DROP = new ConcurrentHashMap<>();

    private DropSyncHandler() {
    }

    public static boolean isDropSyncedItem(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && (stack.getItem() instanceof RotationalMagnetItem || BackpackHelper.isBackpack(stack));
    }

    /**
     * Called from {@code Item.onDroppedByPlayer} (invoked exclusively by {@code ServerPlayer.drop(boolean)}
     * immediately before {@code removeFromSelected}, silent {@code setRemoteSlot}, and {@code ItemTossEvent}).
     */
    public static void markHotbarDropPending(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer))
            return;
        int selected = serverPlayer.getInventory().selected;
        if (selected >= 0 && selected < Inventory.getSelectionSize()) {
            PENDING_HOTBAR_DROP.put(serverPlayer.getUUID(), selected);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onItemToss(ItemTossEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer serverPlayer))
            return;
        Integer droppedHotbarSlot = PENDING_HOTBAR_DROP.remove(serverPlayer.getUUID());
        if (droppedHotbarSlot == null)
            return;
        syncHotbarSlotToClient(serverPlayer, droppedHotbarSlot);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer serverPlayer))
            return;
        Integer staleSlot = PENDING_HOTBAR_DROP.remove(serverPlayer.getUUID());
        if (staleSlot != null) {
            syncHotbarSlotToClient(serverPlayer, staleSlot);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_HOTBAR_DROP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PENDING_HOTBAR_DROP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PENDING_HOTBAR_DROP.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_HOTBAR_DROP.clear();
    }

    private static void syncHotbarSlotToClient(ServerPlayer player, int hotbarSlot) {
        if (player.connection == null)
            return;
        Inventory inventory = player.getInventory();
        AbstractContainerMenu targetMenu = player.containerMenu;
        if (resolveHotbarMenuSlot(targetMenu, inventory, hotbarSlot) < 0) {
            targetMenu = player.inventoryMenu;
        }
        syncDroppedHotbarSlot(targetMenu, inventory, hotbarSlot,
                (containerId, stateId, slotIndex, stack) -> player.connection.send(
                        new ClientboundContainerSetSlotPacket(containerId, stateId, slotIndex, stack)));
    }

    static int resolveHotbarMenuSlot(AbstractContainerMenu menu, Inventory inventory, int hotbarSlot) {
        if (menu == null || inventory == null || hotbarSlot < 0 || hotbarSlot >= Inventory.getSelectionSize())
            return -1;
        OptionalInt found = menu.findSlot(inventory, hotbarSlot);
        return found.isPresent() ? found.getAsInt() : -1;
    }

    static boolean syncDroppedHotbarSlot(AbstractContainerMenu menu,
                                         Inventory inventory,
                                         int hotbarSlot,
                                         SlotPacketSender sender) {
        int slotIndex = resolveHotbarMenuSlot(menu, inventory, hotbarSlot);
        if (slotIndex < 0 || sender == null)
            return false;
        ItemStack authoritative = inventory.getItem(hotbarSlot);
        menu.setRemoteSlot(slotIndex, authoritative);
        sender.send(menu.containerId, menu.incrementStateId(), slotIndex, authoritative.copy());
        return true;
    }
}
