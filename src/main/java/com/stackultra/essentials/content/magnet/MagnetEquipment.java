package com.stackultra.essentials.content.magnet;

import com.stackultra.essentials.content.StackSyncHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class MagnetEquipment {

    private MagnetEquipment() {
    }

    public static List<ItemStack> getEquippedMagnets(Player player) {
        List<ItemStack> magnets = new ArrayList<>(2);

        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof RotationalMagnetItem)
            magnets.add(main);

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RotationalMagnetItem && offhand != main)
            magnets.add(offhand);

        Inventory inventory = player.getInventory();
        for (int i = 0; i < Inventory.getSelectionSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() instanceof RotationalMagnetItem && !containsRef(magnets, stack))
                magnets.add(stack);
        }

        return magnets;
    }

    public static List<ItemStack> getCarriedMagnets(Player player) {
        List<ItemStack> magnets = new ArrayList<>();

        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof RotationalMagnetItem)
                magnets.add(stack);
        }

        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof RotationalMagnetItem)
            magnets.add(offhand);

        if (ModList.get().isLoaded("curios")) {
            try {
                magnets.addAll(getCuriosMagnets(player));
            } catch (Throwable ignored) {
            }
        }

        return magnets;
    }

    public static ItemStack findCuriosMagnet(Player player) {
        if (!ModList.get().isLoaded("curios"))
            return ItemStack.EMPTY;
        try {
            List<ItemStack> curios = getCuriosMagnets(player);
            return curios.isEmpty() ? ItemStack.EMPTY : curios.get(0);
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static ItemStack findEquippedMagnet(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof RotationalMagnetItem)
            return main;

        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof RotationalMagnetItem)
            return off;

        ItemStack curio = findCuriosMagnet(player);
        if (!curio.isEmpty())
            return curio;

        for (ItemStack magnet : getEquippedMagnets(player)) {
            if (!magnet.isEmpty())
                return magnet;
        }
        return ItemStack.EMPTY;
    }

    public static ItemStack findTargetPrecisionMagnet(Player player) {
        ItemStack main = player.getMainHandItem();
        if (isPrecisionMagnet(main))
            return main;

        ItemStack off = player.getOffhandItem();
        if (isPrecisionMagnet(off))
            return off;

        if (ModList.get().isLoaded("curios")) {
            try {
                for (ItemStack stack : getCuriosMagnets(player)) {
                    if (isPrecisionMagnet(stack))
                        return stack;
                }
            } catch (Throwable ignored) {
            }
        }

        for (ItemStack stack : player.getInventory().items) {
            if (isPrecisionMagnet(stack))
                return stack;
        }

        return ItemStack.EMPTY;
    }

    public static void syncMagnet(Player player, ItemStack magnet) {
        if (magnet == null || magnet.isEmpty())
            return;

        // Fast path: exact reference identity on live inventory / hand slots
        if (player.getMainHandItem() == magnet) {
            player.setItemInHand(InteractionHand.MAIN_HAND, magnet);
            broadcastIfServer(player);
            return;
        }

        if (player.getOffhandItem() == magnet) {
            player.setItemInHand(InteractionHand.OFF_HAND, magnet);
            broadcastIfServer(player);
            return;
        }

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            if (inventory.items.get(i) == magnet) {
                inventory.setItem(i, magnet);
                broadcastIfServer(player);
                return;
            }
        }

        // Unified fallback across every unique physical magnet slot (inventory.items already includes main hand)
        List<ItemStack> candidates = new ArrayList<>();
        List<Consumer<ItemStack>> writers = new ArrayList<>();

        candidates.add(player.getOffhandItem());
        writers.add(stack -> player.setItemInHand(InteractionHand.OFF_HAND, stack));

        for (int i = 0; i < inventory.items.size(); i++) {
            final int slotIndex = i;
            candidates.add(inventory.items.get(i));
            writers.add(stack -> inventory.setItem(slotIndex, stack));
        }

        if (ModList.get().isLoaded("curios")) {
            try {
                collectCuriosMagnetSlots(player, candidates, writers);
            } catch (Throwable ignored) {
            }
        }

        int matchIndex = StackSyncHelper.findMatchingIndex(candidates, magnet);
        if (matchIndex >= 0) {
            writers.get(matchIndex).accept(magnet);
        }
        broadcastIfServer(player);
    }

    private static void broadcastIfServer(Player player) {
        if (player.level().isClientSide())
            return;
        player.containerMenu.broadcastChanges();
        if (player.containerMenu != player.inventoryMenu)
            player.inventoryMenu.broadcastChanges();
    }

    private static boolean isPrecisionMagnet(ItemStack stack) {
        return stack.getItem() instanceof RotationalMagnetItem item && item.getTier().hasFilterSlots();
    }

    private static boolean containsRef(List<ItemStack> list, ItemStack target) {
        for (ItemStack stack : list) {
            if (stack == target)
                return true;
        }
        return false;
    }

    private static List<ItemStack> getCuriosMagnets(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    handler.getCurios().values().forEach(stacksHandler -> {
                        for (int i = 0; i < stacksHandler.getSlots(); i++) {
                            ItemStack stack = stacksHandler.getStacks().getStackInSlot(i);
                            if (stack.getItem() instanceof RotationalMagnetItem)
                                stacks.add(stack);
                        }
                    });
                    return stacks;
                })
                .orElseGet(ArrayList::new);
    }

    private static void collectCuriosMagnetSlots(Player player,
                                                 List<ItemStack> candidates,
                                                 List<Consumer<ItemStack>> writers) {
        CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
            handler.getCurios().values().forEach(stacksHandler -> {
                IDynamicStackHandler stacks = stacksHandler.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    final int slotIndex = i;
                    candidates.add(stacks.getStackInSlot(i));
                    writers.add(stack -> stacks.setStackInSlot(slotIndex, stack));
                }
            });
        });
    }
}
