package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenBackpackPayload() implements CustomPacketPayload {

    public static final Type<OpenBackpackPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "open_backpack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBackpackPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenBackpackPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
