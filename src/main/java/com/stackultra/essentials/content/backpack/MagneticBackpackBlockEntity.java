package com.stackultra.essentials.content.backpack;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.BlockItemComponentHelper;
import com.stackultra.essentials.content.KineticChargeHelper;
import com.stackultra.essentials.content.MagnetFilterHelper;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class MagneticBackpackBlockEntity extends KineticBlockEntity
        implements IHaveGoggleInformation, MenuProvider {

    private final BackpackInventory inventory;
    private final PortableToolboxInventory toolboxInventory;
    private final ItemStackHandler filterInventory;
    private final KineticChargeHelper.KineticChargeState kineticCharge = new KineticChargeHelper.KineticChargeState();
    private final BlockItemComponentHelper.PreservedComponents preservedComponents =
            new BlockItemComponentHelper.PreservedComponents();
    private int charge = 0;
    private boolean enabled = true;
    private boolean blacklist = false;
    private SortMode sortMode = SortMode.NAME;
    private boolean restockEnabled = true;
    private int capacityEnchantLevel = 0;

    public MagneticBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.MAGNETIC_BACKPACK.get(), pos, state);
        if (state.hasProperty(MagneticBackpackBlock.COLOR)) {
            DyeColor stateColor = state.getValue(MagneticBackpackBlock.COLOR);
            if (stateColor != DyeColor.BROWN) {
                this.preservedComponents.setBackpackColorIfAbsent(stateColor);
            }
        }
        this.inventory = BackpackInventory.forBlockEntity(this::markChanged);
        this.toolboxInventory = PortableToolboxInventory.forBlockEntity(this::markChanged);
        this.filterInventory = new ItemStackHandler(MagneticBackpackItem.FILTER_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sendData();
            }
        };
    }

    private void markChanged() {
        setChanged();
        sendData();
    }

    public BackpackInventory getInventory() {
        return inventory;
    }

    public PortableToolboxInventory getToolboxInventory() {
        return toolboxInventory;
    }

    @javax.annotation.Nullable
    public DyeColor getBackpackColor() {
        return preservedComponents.getBackpackColor();
    }

    public IItemHandler getItemHandler() {
        return inventory;
    }

    public int getMaxCharge() {
        return MagnetTier.TIER_2.maxCharge(capacityEnchantLevel);
    }

    public int getCapacityEnchantLevel() {
        return capacityEnchantLevel;
    }

    public void setCapacityEnchantLevel(int capacityEnchantLevel) {
        this.capacityEnchantLevel = Math.max(0, capacityEnchantLevel);
        if (charge > getMaxCharge())
            charge = getMaxCharge();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        KineticChargeHelper.TickResult result = kineticCharge.tick(charge, getMaxCharge(), getSpeed());
        if (result.chargeChanged()) {
            charge = result.nextCharge();
            setChanged();
            sendData();
        }
        if (result.activelyCharging()) {
            KineticChargeHelper.spawnChargingParticles(level, getBlockPos());
        }
        if (result.reachedFull()) {
            KineticChargeHelper.spawnFullChargeParticles(level, getBlockPos());
        }
    }

    public int getCharge() {
        return charge;
    }

    public void setCharge(int value) {
        this.charge = Math.clamp(value, 0, getMaxCharge());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        setChanged();
        sendData();
    }

    public void toggleEnabled() {
        setEnabled(!enabled);
    }

    public boolean isBlacklist() {
        return blacklist;
    }

    public void setBlacklist(boolean blacklist) {
        if (this.blacklist == blacklist)
            return;
        this.blacklist = blacklist;
        setChanged();
        sendData();
    }

    public void toggleBlacklist() {
        setBlacklist(!blacklist);
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
        sendData();
    }

    public boolean isRestockEnabled() {
        return restockEnabled;
    }

    public void setRestockEnabled(boolean restockEnabled) {
        if (this.restockEnabled == restockEnabled)
            return;
        this.restockEnabled = restockEnabled;
        setChanged();
        sendData();
    }

    public ItemStackHandler getFilterInventory() {
        return filterInventory;
    }

    public void setFilters(List<ItemStack> filters) {
        for (int i = 0; i < MagneticBackpackItem.FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY);
        setChanged();
        sendData();
    }

    public boolean test(ItemStack stack) {
        return MagnetFilterHelper.matchesFilter(level, filterInventory, stack, blacklist);
    }

    public void loadFromStack(ItemStack stack) {
        preservedComponents.loadFromStack(stack);
        inventory.load(stack);
        toolboxInventory.load(stack);
        setCapacityEnchantLevel(MagneticBackpackItem.getCapacityLevel(stack));
        setCharge(stack.getOrDefault(AllDataComponents.CHARGE, getMaxCharge()));
        setEnabled(stack.getOrDefault(AllDataComponents.ENABLED, true));
        setBlacklist(stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
        setSortMode(stack.getOrDefault(ExtraDataComponents.SORT_MODE, SortMode.NAME));
        setRestockEnabled(stack.getOrDefault(ExtraDataComponents.TOOLBOX_RESTOCK, true));
        kineticCharge.syncFullState(charge, getMaxCharge());
        List<ItemStack> filters = MagneticBackpackItem.getFilters(stack);
        for (int i = 0; i < MagneticBackpackItem.FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i) : ItemStack.EMPTY);
    }

    public void saveToStack(ItemStack stack) {
        preservedComponents.saveToStack(stack);
        inventory.save(stack);
        toolboxInventory.save(stack);
        stack.set(AllDataComponents.CHARGE, charge);
        stack.set(AllDataComponents.ENABLED, enabled);
        stack.set(ExtraDataComponents.BLACKLIST, blacklist);
        stack.set(ExtraDataComponents.SORT_MODE, sortMode);
        stack.set(ExtraDataComponents.TOOLBOX_RESTOCK, restockEnabled);
        List<ItemStack> filters = new ArrayList<>(MagneticBackpackItem.FILTER_SLOTS);
        for (int i = 0; i < MagneticBackpackItem.FILTER_SLOTS; i++)
            filters.add(filterInventory.getStackInSlot(i).copy());
        MagneticBackpackItem.setFilters(stack, filters);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AllBlockEntities.MAGNETIC_BACKPACK.get(),
                (be, side) -> be.getItemHandler());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Charge", charge);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Blacklist", blacklist);
        tag.putString("SortMode", sortMode.getSerializedName());
        tag.putBoolean("Restock", restockEnabled);
        tag.putInt("CapacityEnchantLevel", capacityEnchantLevel);
        tag.put("Filters", filterInventory.serializeNBT(registries));
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Toolbox", toolboxInventory.serializeNBT(registries));
        preservedComponents.writeToTag(tag, registries);
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        capacityEnchantLevel = Math.max(0, tag.getInt("CapacityEnchantLevel"));
        charge = Math.clamp(tag.getInt("Charge"), 0, getMaxCharge());
        enabled = tag.getBoolean("Enabled");
        blacklist = tag.getBoolean("Blacklist");
        if (tag.contains("SortMode"))
            sortMode = SortMode.byName(tag.getString("SortMode"));
        if (tag.contains("Restock"))
            restockEnabled = tag.getBoolean("Restock");
        kineticCharge.syncFullState(charge, getMaxCharge());
        if (tag.contains("Filters"))
            filterInventory.deserializeNBT(registries, tag.getCompound("Filters"));
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        if (tag.contains("Toolbox"))
            toolboxInventory.deserializeNBT(registries, tag.getCompound("Toolbox"));
        preservedComponents.readFromTag(tag, registries);
        super.read(tag, registries, clientPacket);
    }

    @Override
    public void sendToMenu(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBlockPos(getBlockPos());
        buf.writeNbt(getUpdateTag(buf.registryAccess()));
    }

    @Override
    public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player player) {
        return MagneticBackpackMenu.create(windowId, inv, this);
    }

    @Override
    public Component getDisplayName() {
        Component customName = preservedComponents.getCustomName();
        if (customName != null)
            return customName;
        return BackpackColorHelper.coloredName(
                preservedComponents.getBackpackColor(),
                Component.translatable(getBlockState().getBlock().getDescriptionId()));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.blockName(getBlockState()).style(ChatFormatting.GOLD).forGoggles(tooltip);
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        KineticChargeHelper.appendGoggleTooltip(tooltip, enabled, getSpeed(), charge, getMaxCharge());
        return true;
    }
}
