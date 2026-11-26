package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-to-client sync of which player hotbar slots (0-8) are currently locked to
 * a portable backpack toolbox compartment, plus whether Restock is currently active.
 */
public record SyncToolboxLinksPayload(int lockedHotbarMask, boolean restockActive) implements CustomPacketPayload {

    public static final Type<SyncToolboxLinksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "sync_toolbox_links"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncToolboxLinksPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncToolboxLinksPayload::lockedHotbarMask,
                    ByteBufCodecs.BOOL, SyncToolboxLinksPayload::restockActive,
                    SyncToolboxLinksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncToolboxLinksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToolboxLinks(
                payload.lockedHotbarMask(), payload.restockActive()));
    }
}
