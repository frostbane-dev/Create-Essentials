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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
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
    }

    private static void handleToggle(RotationalMagnetTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            if (payload.toggleEquippedFirst()) {
                ItemStack magnetic = (player.containerMenu instanceof MagneticBackpackMenu openMenu
                        && !openMenu.isBlockEntityContext() && !openMenu.getBackpackStack().isEmpty())
                        ? openMenu.getBackpackStack()
                        : MagneticBackpackEquipment.findCarriedMagneticBackpack(player);
                if (!magnetic.isEmpty()) {
                    boolean enabled = !magnetic.getOrDefault(AllDataComponents.ENABLED, true);
                    magnetic.set(AllDataComponents.ENABLED, enabled);
                    MagneticBackpackEquipment.syncMagneticBackpack(player, magnetic);
                    sendToggleStatus(player, magnetic.getHoverName(), enabled);
                    return;
                }

                for (ItemStack magnet : MagnetEquipment.getEquippedMagnets(player)) {
                    if (!(magnet.getItem() instanceof RotationalMagnetItem))
                        continue;
                    boolean enabled = !magnet.getOrDefault(AllDataComponents.ENABLED, true);
                    magnet.set(AllDataComponents.ENABLED, enabled);
                    if (player.getItemBySlot(EquipmentSlot.CHEST) == magnet)
                        player.setItemSlot(EquipmentSlot.CHEST, magnet);
                    sendToggleStatus(player, magnet.getHoverName(), enabled);
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

    private static void handleOpenMagnet(OpenMagnetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            ItemStack magnet = MagnetEquipment.findEquippedMagnet(player);
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

    private static ItemStack findMagneticBackpack(ServerPlayer player) {
        ItemStack equipped = MagneticBackpackEquipment.findCarriedMagneticBackpack(player);
        if (!equipped.isEmpty())
            return equipped;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof MagneticBackpackItem)
                return stack;
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack findMechanicalBackpack(ServerPlayer player) {
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

            ItemStack magnetic = findMagneticBackpack(player);
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
                        });
                return;
            }

            ItemStack backpack = findMechanicalBackpack(player);
            if (backpack.isEmpty())
                return;

            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> BackpackMenu.create(id, inv, backpack),
                            backpack.getHoverName()),
                    buf -> buf.writeBoolean(false));
        });
    }
}
