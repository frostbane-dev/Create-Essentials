package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.IBackpackMenu;
import com.stackultra.essentials.content.backpack.SortMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import javax.annotation.Nullable;

public record BackpackActionPayload(int actionId, int sortModeId) implements CustomPacketPayload {

    public enum Action {
        TRANSFER_TO_STORAGE,
        TRANSFER_TO_INVENTORY,
        SORT,
        SET_SORT_MODE;

        private static final Action[] VALUES = values();

        @Nullable
        public static Action fromId(int id) {
            if (id < 0 || id >= VALUES.length)
                return null;
            return VALUES[id];
        }
    }

    public static final Type<BackpackActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "backpack_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, BackpackActionPayload::actionId,
                    ByteBufCodecs.VAR_INT, BackpackActionPayload::sortModeId,
                    BackpackActionPayload::new);

    public static BackpackActionPayload transferToStorage() {
        return new BackpackActionPayload(Action.TRANSFER_TO_STORAGE.ordinal(), 0);
    }

    public static BackpackActionPayload transferToInventory() {
        return new BackpackActionPayload(Action.TRANSFER_TO_INVENTORY.ordinal(), 0);
    }

    public static BackpackActionPayload sort() {
        return new BackpackActionPayload(Action.SORT.ordinal(), 0);
    }

    public static BackpackActionPayload setSortMode(SortMode mode) {
        return new BackpackActionPayload(Action.SET_SORT_MODE.ordinal(), mode.ordinal());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BackpackActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            if (!(player.containerMenu instanceof IBackpackMenu menu))
                return;
            if (!player.containerMenu.stillValid(player))
                return;

            Action action = Action.fromId(payload.actionId());
            if (action == null)
                return;

            switch (action) {
                case TRANSFER_TO_STORAGE -> menu.transferMatchingToStorage(player);
                case TRANSFER_TO_INVENTORY -> menu.transferMatchingToInventory(player);
                case SORT -> menu.sortStorage(player);
                case SET_SORT_MODE -> {
                    SortMode mode = SortMode.fromIdStrict(payload.sortModeId());
                    if (mode == null)
                        return;
                    menu.setSortMode(mode, player);
                }
            }
        });
    }
}
