package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.client.ClientPacketHandlers;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncToolboxFiltersPayload(int containerId, ItemContainerContents filters) implements CustomPacketPayload {

    public static final Type<SyncToolboxFiltersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "sync_toolbox_filters"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncToolboxFiltersPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncToolboxFiltersPayload::containerId,
                    ItemContainerContents.STREAM_CODEC, SyncToolboxFiltersPayload::filters,
                    SyncToolboxFiltersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncToolboxFiltersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToolboxFilters(payload.containerId(), payload.filters()));
    }
}
