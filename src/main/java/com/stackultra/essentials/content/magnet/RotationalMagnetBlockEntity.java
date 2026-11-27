package com.stackultra.essentials.content.magnet;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.KineticChargeHelper;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

public class RotationalMagnetBlockEntity extends KineticBlockEntity
        implements IHaveGoggleInformation, MenuProvider {

    private final MagnetTier tier;
    private final ItemStackHandler filterInventory;
    private final KineticChargeHelper.KineticChargeState kineticCharge = new KineticChargeHelper.KineticChargeState();
    private int charge = 0;
    private boolean enabled = true;
    private boolean blacklist = false;
    private int capacityEnchantLevel = 0;

    public RotationalMagnetBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.ROTATIONAL_MAGNET.get(), pos, state);
        this.tier = state.getBlock() instanceof RotationalMagnetBlock block
                ? block.getTier() : MagnetTier.TIER_1;
        this.filterInventory = new ItemStackHandler(RotationalMagnetItem.FILTER_SLOTS) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                sendData();
            }
        };
    }

    public MagnetTier getTier() {
        return tier;
    }

    public ItemStackHandler getFilterInventory() {
        return filterInventory;
    }

    public int getMaxCharge() {
        return tier.maxCharge(capacityEnchantLevel);
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
        if (level == null || level.isClientSide)
            return;

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
        if (this.enabled == enabled)
            return;
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

    public void setFilters(List<ItemStack> filters) {
        for (int i = 0; i < RotationalMagnetItem.FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY);
        setChanged();
        sendData();
    }

    public void loadFromStack(ItemStack stack) {
        setCapacityEnchantLevel(RotationalMagnetItem.getCapacityLevel(stack));
        setCharge(stack.getOrDefault(AllDataComponents.CHARGE, getMaxCharge()));
        setEnabled(stack.getOrDefault(AllDataComponents.ENABLED, true));
        setBlacklist(stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
        kineticCharge.syncFullState(charge, getMaxCharge());
        List<ItemStack> filters = RotationalMagnetItem.getFilters(stack);
        for (int i = 0; i < RotationalMagnetItem.FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i) : ItemStack.EMPTY);
    }

    public void saveToStack(ItemStack stack) {
        stack.set(AllDataComponents.CHARGE, charge);
        stack.set(AllDataComponents.ENABLED, enabled);
        stack.set(ExtraDataComponents.BLACKLIST, blacklist);
        List<ItemStack> filters = new ArrayList<>(RotationalMagnetItem.FILTER_SLOTS);
        for (int i = 0; i < RotationalMagnetItem.FILTER_SLOTS; i++)
            filters.add(filterInventory.getStackInSlot(i).copy());
        RotationalMagnetItem.setFilters(stack, filters);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        tag.putInt("Charge", charge);
        tag.putBoolean("Enabled", enabled);
        tag.putBoolean("Blacklist", blacklist);
        tag.putInt("CapacityEnchantLevel", capacityEnchantLevel);
        tag.put("Filters", filterInventory.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        capacityEnchantLevel = Math.max(0, tag.getInt("CapacityEnchantLevel"));
        charge = Math.clamp(tag.getInt("Charge"), 0, getMaxCharge());
        enabled = tag.getBoolean("Enabled");
        blacklist = tag.getBoolean("Blacklist");
        kineticCharge.syncFullState(charge, getMaxCharge());
        if (tag.contains("Filters"))
            filterInventory.deserializeNBT(registries, tag.getCompound("Filters"));
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
        return RotationalMagnetMenu.create(windowId, inv, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.blockName(getBlockState()).style(ChatFormatting.GOLD).forGoggles(tooltip);
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        KineticChargeHelper.appendGoggleTooltip(tooltip, enabled, getSpeed(), charge, getMaxCharge());
        return true;
    }
}
