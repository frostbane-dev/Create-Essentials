package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MagneticBackpackEquipment;
import com.stackultra.essentials.content.magnet.MagnetEquipment;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlacklistTogglePayload(BlockPos pos, boolean toggleEquippedFirst) implements CustomPacketPayload {

    public static final Type<BlacklistTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "blacklist_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BlacklistTogglePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BlacklistTogglePayload::pos,
                    ByteBufCodecs.BOOL, BlacklistTogglePayload::toggleEquippedFirst,
                    BlacklistTogglePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlacklistTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;

            if (payload.toggleEquippedFirst()) {
                ItemStack backpack = MagneticBackpackEquipment.findCarriedMagneticBackpack(player);
                if (!backpack.isEmpty()) {
                    toggle(backpack);
                    MagneticBackpackEquipment.syncMagneticBackpack(player, backpack);
                    return;
                }

                ItemStack magnet = MagnetEquipment.findEquippedMagnet(player);
                if (!magnet.isEmpty()) {
                    toggle(magnet);
                    if (player.getItemBySlot(EquipmentSlot.CHEST) == magnet)
                        player.setItemSlot(EquipmentSlot.CHEST, magnet);
                    return;
                }
            }

            if (player.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0D)
                return;
            if (player.level().getBlockEntity(payload.pos()) instanceof MagneticBackpackBlockEntity backpack)
                backpack.toggleBlacklist();
            else if (player.level().getBlockEntity(payload.pos()) instanceof RotationalMagnetBlockEntity magnet)
                magnet.toggleBlacklist();
        });
    }

    private static void toggle(ItemStack stack) {
        stack.set(ExtraDataComponents.BLACKLIST,
                !stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
    }
}
