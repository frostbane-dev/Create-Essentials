package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.BlockItemComponentHelper;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;

public class MechanicalBackpackBlockEntity extends BlockEntity implements MenuProvider {

    private final BackpackInventory inventory;
    private final PortableToolboxInventory toolboxInventory;
    private final BlockItemComponentHelper.PreservedComponents preservedComponents =
            new BlockItemComponentHelper.PreservedComponents();
    private boolean netherite;
    private SortMode sortMode = SortMode.NAME;
    private boolean restockEnabled = true;

    public MechanicalBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.MECHANICAL_BACKPACK.get(), pos, state);
        this.netherite = state.hasProperty(MechanicalBackpackBlock.NETHERITE)
                && state.getValue(MechanicalBackpackBlock.NETHERITE);
        if (state.hasProperty(MechanicalBackpackBlock.COLOR)) {
            DyeColor stateColor = state.getValue(MechanicalBackpackBlock.COLOR);
            if (stateColor != DyeColor.BROWN) {
                this.preservedComponents.setBackpackColorIfAbsent(stateColor);
            }
        }
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
        markChanged();
    }

    public boolean isRestockEnabled() {
        return restockEnabled;
    }

    public void setRestockEnabled(boolean restockEnabled) {
        if (this.restockEnabled == restockEnabled)
            return;
        this.restockEnabled = restockEnabled;
        markChanged();
    }

    public void syncToClients() {
        markChanged();
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
        preservedComponents.loadFromStack(stack);
        inventory.load(stack);
        toolboxInventory.load(stack);
    }

    public void saveToStack(ItemStack stack) {
        preservedComponents.saveToStack(stack);
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
        preservedComponents.writeToTag(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
        preservedComponents.readFromTag(tag, registries);
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
        preservedComponents.readFromTag(tag, registries);
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return BackpackMenu.create(windowId, inv, this);
    }

    @Override
    public Component getDisplayName() {
        Component customName = preservedComponents.getCustomName();
        if (customName != null)
            return customName;
        Component baseName = Component.translatable(netherite
                ? "item.createessentials.netherite_backpack"
                : getBlockState().getBlock().getDescriptionId());
        return BackpackColorHelper.coloredName(preservedComponents.getBackpackColor(), baseName);
    }
}
