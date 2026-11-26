package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record OpenMagnetPayload() implements CustomPacketPayload {

    public static final Type<OpenMagnetPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "open_magnet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMagnetPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenMagnetPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
