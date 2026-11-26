package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

public class MechanicalBackpackBlockEntity extends BlockEntity implements MenuProvider {

    private final BackpackInventory inventory;
    private final PortableToolboxInventory toolboxInventory;
    private boolean netherite = false;
    private SortMode sortMode = SortMode.NAME;
    private boolean restockEnabled = true;

    public MechanicalBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.MECHANICAL_BACKPACK.get(), pos, state);
        this.inventory = BackpackInventory.forBlockEntity(this::markChanged);
        this.toolboxInventory = PortableToolboxInventory.forBlockEntity(this::markChanged);
    }

    public boolean isNetherite() {
        return netherite;
    }

    public SortMode getSortMode() {
        return sortMode;
    }

    public void setSortMode(SortMode sortMode) {
        SortMode resolved = sortMode != null ? sortMode : SortMode.NAME;
        if (this.sortMode == resolved)
            return;
        this.sortMode = resolved;
        setChanged();
    }

    public boolean isRestockEnabled() {
        return restockEnabled;
    }

    public void setRestockEnabled(boolean restockEnabled) {
        if (this.restockEnabled == restockEnabled)
            return;
        this.restockEnabled = restockEnabled;
        setChanged();
    }

    private void markChanged() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public BackpackInventory getInventory() {
        return inventory;
    }

    public PortableToolboxInventory getToolboxInventory() {
        return toolboxInventory;
    }

    public IItemHandler getItemHandler() {
        return inventory;
    }

    public void loadFromStack(ItemStack stack) {
        this.netherite = stack.getItem() instanceof NetheriteBackpackItem;
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        this.restockEnabled = stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true);
        inventory.load(stack);
        toolboxInventory.load(stack);
    }

    public void saveToStack(ItemStack stack) {
        inventory.save(stack);
        toolboxInventory.save(stack);
        stack.set(ExtraDataComponents.SORT_MODE, sortMode);
        stack.set(ExtraDataComponents.TOOLBOX_RESTOCK, restockEnabled);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AllBlockEntities.MECHANICAL_BACKPACK.get(),
                (be, side) -> be.getItemHandler());
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Netherite", netherite);
        tag.putString("SortMode", sortMode.getSerializedName());
        tag.putBoolean("Restock", restockEnabled);
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Toolbox", toolboxInventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        netherite = tag.getBoolean("Netherite");
        if (tag.contains("SortMode"))
            sortMode = SortMode.byName(tag.getString("SortMode"));
        if (tag.contains("Restock"))
            restockEnabled = tag.getBoolean("Restock");
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Toolbox"))
            toolboxInventory.deserializeNBT(registries, tag.getCompound("Toolbox"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public void sendToMenu(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBlockPos(getBlockPos());
        buf.writeNbt(getUpdateTag(buf.registryAccess()));
    }

    public void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag == null)
            return;
        if (tag.contains("Netherite"))
            netherite = tag.getBoolean("Netherite");
        if (tag.contains("SortMode"))
            sortMode = SortMode.byName(tag.getString("SortMode"));
        if (tag.contains("Restock"))
            restockEnabled = tag.getBoolean("Restock");
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Toolbox"))
            toolboxInventory.deserializeNBT(registries, tag.getCompound("Toolbox"));
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return BackpackMenu.create(windowId, inv, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(netherite
                ? "item.createessentials.netherite_backpack"
                : getBlockState().getBlock().getDescriptionId());
    }
}
