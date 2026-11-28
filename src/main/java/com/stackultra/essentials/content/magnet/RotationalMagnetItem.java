package com.stackultra.essentials.content.magnet;

import com.stackultra.essentials.content.DropSyncHandler;
import com.stackultra.essentials.content.MagneticEquipmentHelper;
import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class RotationalMagnetItem extends BlockItem {

    public static final int BAR_COLOR = MagneticEquipmentHelper.BAR_COLOR;
    public static final int FILTER_SLOTS = MagneticEquipmentHelper.FILTER_SLOTS;

    private final MagnetTier tier;

    public RotationalMagnetItem(MagnetTier tier, Block block, Properties properties) {
        super(block, properties);
        this.tier = tier;
    }

    public MagnetTier getTier() {
        return tier;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getCharge(stack) == 0)
            return Component.translatable("item.createessentials." + tier.id() + ".empty");
        return super.getName(stack);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        DropSyncHandler.markHotbarDropPending(player);
        return super.onDroppedByPlayer(item, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown() && tier.hasFilterSlots()) {
            if (level.isClientSide)
                return InteractionResultHolder.success(stack);

            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> RotationalMagnetMenu.create(id, inv, stack),
                            stack.getHoverName()),
                    buf -> RotationalMagnetMenu.writeItemOpenData(buf, player, stack, this));
            return InteractionResultHolder.consume(stack);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MagneticEquipmentHelper.appendGogglesChargeTooltip(stack, getCharge(stack), getMaxCharge(stack), tooltip);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(AllDataComponents.ENABLED, true) || super.isFoil(stack);
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return MagneticEquipmentHelper.supportsCapacity(enchantment) || super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return MagneticEquipmentHelper.getBarWidth(getCharge(stack), getMaxCharge(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    public int getMaxCharge(ItemStack stack) {
        return tier.maxCharge(getCapacityLevel(stack));
    }

    public int getCharge(ItemStack stack) {
        return MagneticEquipmentHelper.getCharge(stack, getMaxCharge(stack));
    }

    public static int getCapacityLevel(ItemStack stack) {
        return MagneticEquipmentHelper.getCapacityLevel(stack);
    }

    public static NonNullList<ItemStack> getFilterSlots(ItemStack stack) {
        return MagneticEquipmentHelper.getFilterSlots(stack, AllDataComponents.MAGNET_FILTERS, FILTER_SLOTS);
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        return MagneticEquipmentHelper.getFilters(stack, AllDataComponents.MAGNET_FILTERS, FILTER_SLOTS);
    }

    public static void setFilters(ItemStack stack, List<ItemStack> filters) {
        MagneticEquipmentHelper.setFilters(stack, AllDataComponents.MAGNET_FILTERS, FILTER_SLOTS, filters);
    }
}
