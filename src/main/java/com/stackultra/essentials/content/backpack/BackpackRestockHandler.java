package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class BackpackRestockHandler {

    record PendingRestock(int hotbarSlot, ItemStack template) {
    }

    private static final Map<UUID, PendingRestock> PENDING_RESTOCK = new ConcurrentHashMap<>();

    private BackpackRestockHandler() {
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;
        if (!(event.getEntity() instanceof Player player))
            return;
        if (player.isCreative() || player.isSpectator() || !player.isAlive())
            return;

        int selectedSlot = player.getInventory().selected;
        if (selectedSlot < 0 || selectedSlot >= Inventory.getSelectionSize())
            return;

        // Defer completely to PortableToolboxHandler when this hotbar slot is linked to a toolbox compartment
        if (PortableToolboxHandler.isHotbarSlotLinked(player.getUUID(), selectedSlot))
            return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || mainHand.getCount() != 1)
            return;

        Item placedItem = event.getPlacedBlock().getBlock().asItem();
        if (placedItem != mainHand.getItem())
            return;

        PENDING_RESTOCK.put(player.getUUID(), new PendingRestock(selectedSlot, mainHand.copyWithCount(1)));
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.isSpectator() || !player.isAlive())
            return;

        PendingRestock pending = PENDING_RESTOCK.remove(player.getUUID());
        if (pending == null)
            return;

        int slot = pending.hotbarSlot();
        if (slot < 0 || slot >= Inventory.getSelectionSize())
            return;

        if (PortableToolboxHandler.isHotbarSlotLinked(player.getUUID(), slot))
            return;

        if (!player.getInventory().getItem(slot).isEmpty())
            return;

        ItemStack backpack = BackpackEquipment.findEquippedBackpack(player);
        if (backpack.isEmpty())
            return;
        if (!backpack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true))
            return;

        BackpackInventory inventory = resolveLiveBackpackInventory(player, backpack);
        ItemStack extracted = extractMatchingRestock(inventory, pending.template());
        if (!extracted.isEmpty()) {
            player.getInventory().setItem(slot, extracted);
            if (backpack.getItem() instanceof MagneticBackpackItem)
                MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
            else
                BackpackEquipment.syncBackpack(player, backpack);
        }
    }

    static ItemStack extractMatchingRestock(BackpackInventory inventory, ItemStack template) {
        if (inventory == null || template == null || template.isEmpty())
            return ItemStack.EMPTY;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack inSlot = inventory.getStackInSlot(i);
            if (inSlot.isEmpty() || !ItemStack.isSameItemSameComponents(inSlot, template))
                continue;
            ItemStack extracted = inventory.extractItem(i, inSlot.getMaxStackSize(), false);
            if (!extracted.isEmpty())
                return extracted;
        }
        return ItemStack.EMPTY;
    }

    private static BackpackInventory resolveLiveBackpackInventory(Player player, ItemStack backpack) {
        if (player.containerMenu instanceof MagneticBackpackMenu openMagnetic
                && openMagnetic.isBackingStack(backpack)
                && openMagnetic.getBackpackInventory() != null) {
            return openMagnetic.getBackpackInventory();
        }
        if (player.containerMenu instanceof BackpackMenu openBackpack
                && openBackpack.isBackingStack(backpack)
                && openBackpack.getBackpackInventory() != null) {
            return openBackpack.getBackpackInventory();
        }
        return BackpackInventory.forStack(backpack);
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING_RESTOCK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        PENDING_RESTOCK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        PENDING_RESTOCK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PENDING_RESTOCK.clear();
    }
}
