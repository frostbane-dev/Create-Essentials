package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackEquipment;
import com.stackultra.essentials.content.backpack.BackpackMenu;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MagneticBackpackEquipment;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MagneticBackpackMenu;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import com.stackultra.essentials.content.magnet.MagnetEquipment;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import com.stackultra.essentials.content.magnet.RotationalMagnetMenu;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID)
public class AllPackets {

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                RotationalMagnetTogglePayload.TYPE,
                RotationalMagnetTogglePayload.STREAM_CODEC,
                AllPackets::handleToggle);
        registrar.playToServer(
                OpenBackpackPayload.TYPE,
                OpenBackpackPayload.STREAM_CODEC,
                AllPackets::handleOpenBackpack);
        registrar.playToServer(
                OpenMagnetPayload.TYPE,
                OpenMagnetPayload.STREAM_CODEC,
                AllPackets::handleOpenMagnet);
        registrar.playToServer(
                BlacklistTogglePayload.TYPE,
                BlacklistTogglePayload.STREAM_CODEC,
                BlacklistTogglePayload::handle);
        registrar.playToServer(
                BackpackActionPayload.TYPE,
                BackpackActionPayload.STREAM_CODEC,
                BackpackActionPayload::handle);
        registrar.playToServer(
                BackpackToolboxPayload.TYPE,
                BackpackToolboxPayload.STREAM_CODEC,
                BackpackToolboxPayload::handle);
        registrar.playToClient(
                SyncToolboxFiltersPayload.TYPE,
                SyncToolboxFiltersPayload.STREAM_CODEC,
                SyncToolboxFiltersPayload::handle);
    }

    private static void handleToggle(RotationalMagnetTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            if (payload.toggleEquippedFirst()) {
                // 1. Strict per-item binding when toggled from an open item GUI
                if (player.containerMenu instanceof MagneticBackpackMenu openBackpack
                        && !openBackpack.isBlockEntityContext()
                        && !openBackpack.getBackpackStack().isEmpty()) {
                    toggleMagneticStack(player, openBackpack.getBackpackStack());
                    return;
                }
                if (player.containerMenu instanceof RotationalMagnetMenu openMagnet
                        && !openMagnet.isBlockEntityContext()
                        && !openMagnet.getMagnetStack().isEmpty()) {
                    toggleMagnetStack(player, openMagnet.getMagnetStack());
                    return;
                }

                // 2. Context-aware keybind targeting: Main hand -> Offhand -> Curios/Equipped -> Inventory
                ItemStack target = findToggleTarget(player);
                if (!target.isEmpty()) {
                    if (target.getItem() instanceof MagneticBackpackItem) {
                        toggleMagneticStack(player, target);
                    } else if (target.getItem() instanceof RotationalMagnetItem) {
                        toggleMagnetStack(player, target);
                    }
                    return;
                }
            }

            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D)
                return;
            if (player.level().getBlockEntity(payload.pos()) instanceof MagneticBackpackBlockEntity magneticBackpack) {
                magneticBackpack.toggleEnabled();
                sendToggleStatus(player, magneticBackpack.getDisplayName(), magneticBackpack.isEnabled());
                return;
            }
            if (player.level().getBlockEntity(payload.pos()) instanceof RotationalMagnetBlockEntity magnetBlock) {
                magnetBlock.toggleEnabled();
                sendToggleStatus(player, magnetBlock.getDisplayName(), magnetBlock.isEnabled());
            }
        });
    }

    static ItemStack findToggleTarget(ServerPlayer player) {
        // Priority 1: Main hand
        ItemStack main = player.getMainHandItem();
        if (isToggleableMagnetOrBackpack(main))
            return main;

        // Priority 2: Offhand
        ItemStack off = player.getOffhandItem();
        if (isToggleableMagnetOrBackpack(off))
            return off;

        // Priority 3: Equipped (Curios / Chest slot)
        ItemStack equippedBackpack = MagneticBackpackEquipment.findEquippedMagneticBackpack(player);
        if (!equippedBackpack.isEmpty())
            return equippedBackpack;

        ItemStack curiosMagnet = MagnetEquipment.findCuriosMagnet(player);
        if (!curiosMagnet.isEmpty())
            return curiosMagnet;

        // Priority 4: Rest of inventory fallback
        for (ItemStack stack : player.getInventory().items) {
            if (isToggleableMagnetOrBackpack(stack))
                return stack;
        }

        return ItemStack.EMPTY;
    }

    private static boolean isToggleableMagnetOrBackpack(ItemStack stack) {
        return stack.getItem() instanceof MagneticBackpackItem
                || stack.getItem() instanceof RotationalMagnetItem;
    }

    private static void toggleMagneticStack(ServerPlayer player, ItemStack magnetic) {
        boolean enabled = !magnetic.getOrDefault(AllDataComponents.ENABLED, true);
        magnetic.set(AllDataComponents.ENABLED, enabled);
        MagneticBackpackEquipment.syncMagneticBackpack(player, magnetic);
        sendToggleStatus(player, magnetic.getHoverName(), enabled);
    }

    private static void toggleMagnetStack(ServerPlayer player, ItemStack magnet) {
        boolean enabled = !magnet.getOrDefault(AllDataComponents.ENABLED, true);
        magnet.set(AllDataComponents.ENABLED, enabled);
        MagnetEquipment.syncMagnet(player, magnet);
        sendToggleStatus(player, magnet.getHoverName(), enabled);
    }

    private static void handleOpenMagnet(OpenMagnetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            ItemStack magnet = MagnetEquipment.findTargetPrecisionMagnet(player);
            if (magnet.isEmpty() || !(magnet.getItem() instanceof RotationalMagnetItem item))
                return;
            if (!item.getTier().hasFilterSlots())
                return;

            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> RotationalMagnetMenu.create(id, inv, magnet),
                            magnet.getHoverName()),
                    buf -> {
                        buf.writeBoolean(false);
                        ItemContainerContents.STREAM_CODEC.encode(buf,
                                magnet.getOrDefault(AllDataComponents.MAGNET_FILTERS, ItemContainerContents.EMPTY));
                        buf.writeBoolean(magnet.getOrDefault(AllDataComponents.ENABLED, true));
                        buf.writeVarInt(item.getCharge(magnet));
                        buf.writeVarInt(item.getMaxCharge(magnet));
                    });
        });
    }

    private static void sendToggleStatus(ServerPlayer player, Component name, boolean enabled) {
        player.displayClientMessage(Component.translatable(enabled
                        ? "createessentials.message.toggled_on"
                        : "createessentials.message.toggled_off", name)
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }

    private static ItemStack findMechanicalBackpack(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof MechanicalBackpackItem)
            return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof MechanicalBackpackItem)
            return off;
        ItemStack equipped = BackpackEquipment.findEquippedBackpack(player);
        if (!equipped.isEmpty() && equipped.getItem() instanceof MechanicalBackpackItem)
            return equipped;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MechanicalBackpackItem)
                return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void handleOpenBackpack(OpenBackpackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            ItemStack magnetic = MagneticBackpackEquipment.findCarriedMagneticBackpack(player);
            if (!magnetic.isEmpty()) {
                MagneticBackpackItem magneticItem = (MagneticBackpackItem) magnetic.getItem();
                player.openMenu(new SimpleMenuProvider(
                                (id, inv, p) -> MagneticBackpackMenu.create(id, inv, magnetic),
                                magnetic.getHoverName()),
                        buf -> {
                            buf.writeBoolean(false);
                            ItemContainerContents.STREAM_CODEC.encode(buf,
                                    magnetic.getOrDefault(AllDataComponents.BACKPACK_FILTERS, ItemContainerContents.EMPTY));
                            buf.writeBoolean(magnetic.getOrDefault(AllDataComponents.ENABLED, true));
                            buf.writeVarInt(magneticItem.getCharge(magnetic));
                            buf.writeVarInt(magneticItem.getMaxCharge(magnetic));
                            ItemContainerContents.STREAM_CODEC.encode(buf,
                                    magnetic.getOrDefault(ExtraDataComponents.TOOLBOX_FILTERS, ItemContainerContents.EMPTY));
                        });
                return;
            }

            ItemStack backpack = findMechanicalBackpack(player);
            if (backpack.isEmpty())
                return;

            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> BackpackMenu.create(id, inv, backpack),
                            backpack.getHoverName()),
                    buf -> {
                        buf.writeBoolean(false);
                        ItemContainerContents.STREAM_CODEC.encode(buf,
                                backpack.getOrDefault(ExtraDataComponents.TOOLBOX_FILTERS, ItemContainerContents.EMPTY));
                    });
        });
    }
}
