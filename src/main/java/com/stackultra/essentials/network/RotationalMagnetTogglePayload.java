package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RotationalMagnetTogglePayload(BlockPos pos, boolean toggleEquippedFirst, boolean fromOpenMenu)
        implements CustomPacketPayload {

    public static final Type<RotationalMagnetTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "rotational_magnet_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RotationalMagnetTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RotationalMagnetTogglePayload::pos,
                    ByteBufCodecs.BOOL, RotationalMagnetTogglePayload::toggleEquippedFirst,
                    ByteBufCodecs.BOOL, RotationalMagnetTogglePayload::fromOpenMenu,
                    RotationalMagnetTogglePayload::new);

    public static RotationalMagnetTogglePayload forKeybind(BlockPos targetedPos) {
        return new RotationalMagnetTogglePayload(targetedPos, true, false);
    }

    public static RotationalMagnetTogglePayload forOpenItemMenu() {
        return new RotationalMagnetTogglePayload(BlockPos.ZERO, true, true);
    }

    public static RotationalMagnetTogglePayload forBlockEntity(BlockPos pos) {
        return new RotationalMagnetTogglePayload(pos, false, true);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
