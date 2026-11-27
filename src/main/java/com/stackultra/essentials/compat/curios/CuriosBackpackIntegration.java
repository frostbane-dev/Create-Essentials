package com.stackultra.essentials.compat.curios;

import com.simibubi.create.AllTags;
import com.stackultra.essentials.content.StackSyncHelper;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Isolated Curios runtime bridge. Only ever loaded through {@link CuriosCompat}
 * after verifying {@code ModList.get().isLoaded("curios")}.
 */
public final class CuriosBackpackIntegration {

    private CuriosBackpackIntegration() {
    }

    public static ItemStack getBackBackpack(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .map(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.getItem() instanceof MechanicalBackpackItem
                                || stack.getItem() instanceof MagneticBackpackItem)
                            return stack;
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    public static ItemStack getBackMagneticBackpack(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .map(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack stack = stacks.getStackInSlot(i);
                        if (stack.getItem() instanceof MagneticBackpackItem)
                            return stack;
                    }
                    return ItemStack.EMPTY;
                })
                .orElse(ItemStack.EMPTY);
    }

    public static void collectBackSlots(Player player,
                                        List<ItemStack> candidates,
                                        List<Consumer<ItemStack>> writers) {
        CuriosApi.getCuriosInventory(player)
                .flatMap(handler -> handler.getStacksHandler("back"))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        final int slotIndex = i;
                        candidates.add(stacks.getStackInSlot(i));
                        writers.add(stack -> stacks.setStackInSlot(slotIndex, stack));
                    }
                });
    }

    public static List<ItemStack> getCuriosMagnets(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    handler.getCurios().values().forEach(stacksHandler -> {
                        IDynamicStackHandler inv = stacksHandler.getStacks();
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack stack = inv.getStackInSlot(i);
                            if (stack.getItem() instanceof RotationalMagnetItem)
                                stacks.add(stack);
                        }
                    });
                    return stacks;
                })
                .orElseGet(ArrayList::new);
    }

    public static void collectCuriosMagnetSlots(Player player,
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

    public static List<ItemStack> getCuriosAirSources(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(handler -> {
                    List<ItemStack> stacks = new ArrayList<>();
                    handler.getCurios().values().forEach(stacksHandler -> {
                        IDynamicStackHandler inv = stacksHandler.getStacks();
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack stack = inv.getStackInSlot(i);
                            if (AllTags.AllItemTags.PRESSURIZED_AIR_SOURCES.matches(stack))
                                stacks.add(stack);
                        }
                    });
                    return stacks;
                })
                .orElseGet(ArrayList::new);
    }

    public static boolean isStackInCurios(Player player, ItemStack target) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> {
                    for (var stacksHandler : handler.getCurios().values()) {
                        IDynamicStackHandler stacks = stacksHandler.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            if (stacks.getStackInSlot(i) == target)
                                return true;
                        }
                    }
                    return false;
                })
                .orElse(false);
    }

    public static void syncBackMagneticBackpack(Player player, ItemStack backpack) {
        syncBackBackpack(player, backpack);
    }

    public static void syncBackBackpack(Player player, ItemStack backpack) {
        if (backpack == null || backpack.isEmpty())
            return;
        List<ItemStack> candidates = new ArrayList<>();
        List<Consumer<ItemStack>> writers = new ArrayList<>();
        collectBackSlots(player, candidates, writers);
        int matchIndex = StackSyncHelper.findMatchingIndex(candidates, backpack);
        if (matchIndex >= 0) {
            writers.get(matchIndex).accept(backpack);
        }
    }
}
