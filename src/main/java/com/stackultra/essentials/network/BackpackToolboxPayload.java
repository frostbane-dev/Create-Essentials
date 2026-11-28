package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackSounds;
import com.stackultra.essentials.content.backpack.IBackpackMenu;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import com.stackultra.essentials.content.backpack.PortableToolboxMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record BackpackToolboxPayload(int actionId, int compartment, int hotbarSlot) implements CustomPacketPayload {

    public enum Action {
        EQUIP_COMPARTMENT,
        RETURN_HELD,
        TOGGLE_RESTOCK,
        REQUEST_SYNC,
        OPEN_SCREEN,
        DISPOSE_ALL;

        private static final Action[] VALUES = values();

        @Nullable
        public static Action fromId(int id) {
            if (id < 0 || id >= VALUES.length)
                return null;
            return VALUES[id];
        }
    }

    public static final Type<BackpackToolboxPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "backpack_toolbox"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackToolboxPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BackpackToolboxPayload::actionId,
                    ByteBufCodecs.VAR_INT, BackpackToolboxPayload::compartment,
                    ByteBufCodecs.VAR_INT, BackpackToolboxPayload::hotbarSlot,
                    BackpackToolboxPayload::new);

    public static BackpackToolboxPayload equip(int compartment, int hotbarSlot) {
        return new BackpackToolboxPayload(Action.EQUIP_COMPARTMENT.ordinal(), compartment, hotbarSlot);
    }

    public static BackpackToolboxPayload returnHeld(int hotbarSlot) {
        return new BackpackToolboxPayload(Action.RETURN_HELD.ordinal(), 0, hotbarSlot);
    }

    public static BackpackToolboxPayload toggleRestock() {
        return new BackpackToolboxPayload(Action.TOGGLE_RESTOCK.ordinal(), 0, 0);
    }

    public static BackpackToolboxPayload requestSync() {
        return new BackpackToolboxPayload(Action.REQUEST_SYNC.ordinal(), 0, 0);
    }

    public static BackpackToolboxPayload openScreen() {
        return new BackpackToolboxPayload(Action.OPEN_SCREEN.ordinal(), 0, 0);
    }

    public static BackpackToolboxPayload disposeAll() {
        return new BackpackToolboxPayload(Action.DISPOSE_ALL.ordinal(), 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackpackToolboxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            Action action = Action.fromId(payload.actionId());
            if (action == null)
                return;

            switch (action) {
                case EQUIP_COMPARTMENT -> {
                    if (payload.compartment() < 0 || payload.compartment() >= PortableToolboxInventory.COMPARTMENTS)
                        return;
                    PortableToolboxHandler.equipFromCompartment(player, payload.compartment(), payload.hotbarSlot());
                }
                case RETURN_HELD -> PortableToolboxHandler.returnHeldToToolbox(player, payload.hotbarSlot());
                case TOGGLE_RESTOCK -> {
                    if (!(player.containerMenu instanceof IBackpackMenu menu))
                        return;
                    if (!player.containerMenu.stillValid(player))
                        return;
                    menu.setRestockEnabled(!menu.isRestockEnabled(), player);
                }
                case REQUEST_SYNC -> PortableToolboxHandler.handleSyncRequest(player);
                case OPEN_SCREEN -> {
                    ItemStack backpack = PortableToolboxHandler.findPortableToolboxBackpack(player);
                    if (backpack.isEmpty())
                        return;
                    PortableToolboxInventory tb = PortableToolboxInventory.forStack(backpack);
                    if (player.openMenu(new SimpleMenuProvider(
                                    (id, inv, p) -> PortableToolboxMenu.create(id, inv, backpack),
                                    backpack.getHoverName()),
                            buf -> PortableToolboxMenu.writeOpenData(buf, player, backpack, tb)).isPresent()) {
                        BackpackSounds.playOpenSound(player);
                    }
                }
                case DISPOSE_ALL -> {
                    if (player.containerMenu instanceof PortableToolboxMenu toolboxMenu
                            && toolboxMenu.stillValid(player)) {
                        toolboxMenu.disposeMatchingFromPlayer(player);
                    }
                }
            }
        });
    }
}
