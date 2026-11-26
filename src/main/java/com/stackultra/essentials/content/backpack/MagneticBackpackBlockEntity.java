package com.stackultra.essentials.content.backpack;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.ChargeDisplay;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
    private final ItemStackHandler filterInventory;
    private int charge = 0;
    private boolean enabled = true;
    private boolean blacklist = false;
    private int capacityEnchantLevel = 0;
    private boolean wasFull;
    private float chargeAccumulator = 0F;

    public MagneticBackpackBlockEntity(BlockPos pos, BlockState state) {
        super(AllBlockEntities.MAGNETIC_BACKPACK.get(), pos, state);
        this.inventory = BackpackInventory.forBlockEntity(this::markChanged);
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

        int maxCharge = getMaxCharge();
        if (getSpeed() != 0 && charge < maxCharge) {
            chargeAccumulator += Math.abs(getSpeed()) / 10.0F;
            int add = (int) chargeAccumulator;
            if (add > 0) {
                charge = Math.min(maxCharge, charge + add);
                chargeAccumulator -= add;
                setChanged();
                sendData();
            }
            spawnChargingParticles();
        } else {
            chargeAccumulator = 0F;
        }

        if (charge >= maxCharge) {
            if (!wasFull) {
                wasFull = true;
                spawnFullChargeParticles();
            }
        } else {
            wasFull = false;
        }
    }

    private void spawnChargingParticles() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double cx = getBlockPos().getX() + 0.5;
        double cy = getBlockPos().getY() + 0.5;
        double cz = getBlockPos().getZ() + 0.5;
        for (int i = 0; i < 2; i++) {
            double yaw = serverLevel.random.nextDouble() * Math.PI * 2.0;
            double pitch = (serverLevel.random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            double r = 0.8;
            double x = cx + dx * r;
            double y = cy + dy * r;
            double z = cz + dz * r;
            serverLevel.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, -dx * 0.15, -dy * 0.15, -dz * 0.15, 1.0);
        }
    }

    private void spawnFullChargeParticles() {
        if (!(level instanceof ServerLevel serverLevel))
            return;
        double cx = getBlockPos().getX() + 0.5;
        double cy = getBlockPos().getY() + 0.5;
        double cz = getBlockPos().getZ() + 0.5;
        for (int i = 0; i < 24; i++) {
            double yaw = serverLevel.random.nextDouble() * Math.PI * 2.0;
            double pitch = (serverLevel.random.nextDouble() - 0.5) * Math.PI;
            double dx = Math.cos(yaw) * Math.cos(pitch);
            double dy = Math.sin(pitch);
            double dz = Math.sin(yaw) * Math.cos(pitch);
            double r = 0.55;
            double x = cx + dx * r;
            double y = cy + dy * r;
            double z = cz + dz * r;
            serverLevel.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, dx * 0.25, dy * 0.25, dz * 0.25, 1.0);
        }
        serverLevel.playSound(null, cx, cy, cz, SoundEvents.NOTE_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.2F);
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
        boolean any = false;
        boolean matches = false;
        for (int i = 0; i < MagneticBackpackItem.FILTER_SLOTS; i++) {
            ItemStack filter = filterInventory.getStackInSlot(i);
            if (filter.isEmpty())
                continue;
            any = true;
            if (FilterItemStack.of(filter).test(level, stack)) {
                matches = true;
                break;
            }
        }
        if (!any)
            return true;
        return blacklist ? !matches : matches;
    }

    public void loadFromStack(ItemStack stack) {
        inventory.load(stack);
        setCapacityEnchantLevel(MagneticBackpackItem.getCapacityLevel(stack));
        setCharge(stack.getOrDefault(AllDataComponents.CHARGE, getMaxCharge()));
        setEnabled(stack.getOrDefault(AllDataComponents.ENABLED, true));
        setBlacklist(stack.getOrDefault(ExtraDataComponents.BLACKLIST, false));
        wasFull = charge >= getMaxCharge();
        List<ItemStack> filters = MagneticBackpackItem.getFilters(stack);
        for (int i = 0; i < MagneticBackpackItem.FILTER_SLOTS; i++)
            filterInventory.setStackInSlot(i, i < filters.size() ? filters.get(i) : ItemStack.EMPTY);
    }

    public void saveToStack(ItemStack stack) {
        inventory.save(stack);
        stack.set(AllDataComponents.CHARGE, charge);
        stack.set(AllDataComponents.ENABLED, enabled);
        stack.set(ExtraDataComponents.BLACKLIST, blacklist);
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
        tag.putInt("CapacityEnchantLevel", capacityEnchantLevel);
        tag.put("Filters", filterInventory.serializeNBT(registries));
        tag.put("Inventory", inventory.serializeNBT(registries));
        super.write(tag, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        capacityEnchantLevel = Math.max(0, tag.getInt("CapacityEnchantLevel"));
        charge = Math.clamp(tag.getInt("Charge"), 0, getMaxCharge());
        enabled = tag.getBoolean("Enabled");
        blacklist = tag.getBoolean("Blacklist");
        wasFull = charge >= getMaxCharge();
        if (tag.contains("Filters"))
            filterInventory.deserializeNBT(registries, tag.getCompound("Filters"));
        if (tag.contains("Inventory"))
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
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
        return Component.translatable(getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        CreateLang.blockName(getBlockState()).style(ChatFormatting.GOLD).forGoggles(tooltip);
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        CreateLang.builder()
                .add(Component.translatable(enabled
                        ? "createessentials.gui.enabled"
                        : "createessentials.gui.disabled"))
                .style(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)
                .forGoggles(tooltip, 1);

        int max = getMaxCharge();
        if (getSpeed() != 0 && charge < max) {
            CreateLang.builder()
                    .add(ChargeDisplay.chargingLine())
                    .forGoggles(tooltip, 1);
        }

        CreateLang.builder()
                .add(ChargeDisplay.charge(charge, max))
                .forGoggles(tooltip, 1);
        return true;
    }
}
