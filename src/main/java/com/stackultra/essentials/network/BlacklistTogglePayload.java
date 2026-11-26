package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MagneticBackpackEquipment;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MagneticBackpackMenu;
import com.stackultra.essentials.content.magnet.MagnetEquipment;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import com.stackultra.essentials.content.magnet.RotationalMagnetMenu;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
                // 1. Strict per-item binding when toggled from an open item GUI
                if (player.containerMenu instanceof MagneticBackpackMenu openBackpack
                        && !openBackpack.isBlockEntityContext()
                        && !openBackpack.getBackpackStack().isEmpty()) {
                    ItemStack stack = openBackpack.getBackpackStack();
                    toggle(stack);
                    MagneticBackpackEquipment.syncMagneticBackpack(player, stack);
                    return;
                }
                if (player.containerMenu instanceof RotationalMagnetMenu openMagnet
                        && !openMagnet.isBlockEntityContext()
                        && !openMagnet.getMagnetStack().isEmpty()) {
                    ItemStack stack = openMagnet.getMagnetStack();
                    toggle(stack);
                    MagnetEquipment.syncMagnet(player, stack);
                    return;
                }

                // 2. Context-aware fallback matching keybind priority
                ItemStack target = AllPackets.findToggleTarget(player);
                if (!target.isEmpty()) {
                    toggle(target);
                    if (target.getItem() instanceof MagneticBackpackItem)
                        MagneticBackpackEquipment.syncMagneticBackpack(player, target);
                    else if (target.getItem() instanceof RotationalMagnetItem)
                        MagnetEquipment.syncMagnet(player, target);
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
