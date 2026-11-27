package com.stackultra.essentials.network;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.client.ClientPacketHandlers;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Server-to-client authoritative sync of the active portable backpack toolbox state:
 * locked hotbar slot bitmask (0-8), Restock toggle, and all 8 compartment display items
 * (physical item or reserved ghost filter) with exact counts (0-256).
 */
public record SyncToolboxLinksPayload(
        int lockedHotbarMask,
        boolean restockActive,
        boolean hasBackpack,
        List<ItemStack> displayItems,
        List<Integer> counts
) implements CustomPacketPayload {

    public static final Type<SyncToolboxLinksPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "sync_toolbox_links"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncToolboxLinksPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SyncToolboxLinksPayload::lockedHotbarMask,
                    ByteBufCodecs.BOOL, SyncToolboxLinksPayload::restockActive,
                    ByteBufCodecs.BOOL, SyncToolboxLinksPayload::hasBackpack,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(PortableToolboxInventory.COMPARTMENTS)),
                    SyncToolboxLinksPayload::displayItems,
                    ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(PortableToolboxInventory.COMPARTMENTS)),
                    SyncToolboxLinksPayload::counts,
                    SyncToolboxLinksPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SyncToolboxLinksPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketHandlers.handleToolboxState(
                payload.lockedHotbarMask(),
                payload.restockActive(),
                payload.hasBackpack(),
                payload.displayItems(),
                payload.counts()));
    }
}
