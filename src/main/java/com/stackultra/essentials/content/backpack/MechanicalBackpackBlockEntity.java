package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
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
    private boolean netherite = false;
    private SortMode sortMode = SortMode.NAME;

    public MechanicalBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.MECHANICAL_BACKPACK.get(), pos, state);
        this.inventory = BackpackInventory.forBlockEntity(this::markChanged);
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

    private void markChanged() {
        setChanged();
    }

    public BackpackInventory getInventory() {
        return inventory;
    }

    public IItemHandler getItemHandler() {
        return inventory;
    }

    public void loadFromStack(ItemStack stack) {
        this.netherite = stack.getItem() instanceof NetheriteBackpackItem;
        this.sortMode = stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME);
        inventory.load(stack);
    }

    public void saveToStack(ItemStack stack) {
        inventory.save(stack);
        stack.set(ExtraDataComponents.SORT_MODE, sortMode);
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
        tag.put("Inventory", inventory.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        netherite = tag.getBoolean("Netherite");
        if (tag.contains("SortMode"))
            sortMode = SortMode.byName(tag.getString("SortMode"));
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    public void sendToMenu(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBlockPos(getBlockPos());
        buf.writeNbt(getUpdateTag(buf.registryAccess()));
    }

    public void readClient(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag != null && tag.contains("Netherite"))
            netherite = tag.getBoolean("Netherite");
        if (tag != null && tag.contains("SortMode"))
            sortMode = SortMode.byName(tag.getString("SortMode"));
        if (tag != null && tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return BackpackMenu.create(windowId, inv, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }
}
