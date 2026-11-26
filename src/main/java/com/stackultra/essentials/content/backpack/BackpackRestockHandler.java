package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.CreateEssentials;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class BackpackRestockHandler {

    private static final Map<Player, Item> PENDING_RESTOCK = new WeakHashMap<>();

    private BackpackRestockHandler() {
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide())
            return;
        if (!(event.getEntity() instanceof Player player))
            return;
        if (player.isCreative() || player.isSpectator())
            return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || mainHand.getCount() != 1)
            return;

        Item placedItem = event.getPlacedBlock().getBlock().asItem();
        if (placedItem != mainHand.getItem())
            return;

        PENDING_RESTOCK.put(player, placedItem);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide())
            return;

        Item placedItem = PENDING_RESTOCK.remove(player);
        if (placedItem == null)
            return;

        if (!player.getMainHandItem().isEmpty())
            return;

        ItemStack backpack = BackpackEquipment.findEquippedBackpack(player);
        if (backpack.isEmpty())
            return;

        BackpackInventory inventory = BackpackInventory.forStack(backpack);
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack inSlot = inventory.getStackInSlot(i);
            if (inSlot.isEmpty() || inSlot.getItem() != placedItem)
                continue;
            ItemStack extracted = inventory.extractItem(i, inSlot.getMaxStackSize(), false);
            if (!extracted.isEmpty()) {
                player.setItemInHand(InteractionHand.MAIN_HAND, extracted);
                BackpackEquipment.syncBackpack(player, backpack);
                break;
            }
        }
    }
}
