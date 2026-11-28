package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.DropSyncHandler;
import com.stackultra.essentials.content.MagneticEquipmentHelper;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

public class MagneticBackpackItem extends BlockItem implements Equipable {

    public static final int BAR_COLOR = MagneticEquipmentHelper.BAR_COLOR;
    public static final int FILTER_SLOTS = MagneticEquipmentHelper.FILTER_SLOTS;

    public MagneticBackpackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        DropSyncHandler.markHotbarDropPending(player);
        return super.onDroppedByPlayer(item, player);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && !player.isShiftKeyDown()) {
            return use(context.getLevel(), player, context.getHand()).getResult();
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide)
                return InteractionResultHolder.success(stack);

            PortableToolboxInventory tb = PortableToolboxInventory.forStack(stack);
            if (player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> MagneticBackpackMenu.create(id, inv, stack),
                            stack.getHoverName()),
                    buf -> MagneticBackpackMenu.writeItemOpenData(buf, player, stack, this, tb)).isPresent()) {
                BackpackSounds.playOpenSound(player);
            }
            return InteractionResultHolder.consume(stack);
        }
        return swapWithEquipmentSlot(this, level, player, hand);
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.CHEST;
    }

    @Override
    public Component getName(ItemStack stack) {
        if (getCharge(stack) == 0)
            return Component.translatable("item.createessentials.magnetic_backpack.empty");
        return super.getName(stack);
    }

    @Override
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_NETHERITE;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return BackpackTooltipData.fromStack(stack, BackpackTooltipData.Tier.MAGNETIC);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        MagneticEquipmentHelper.appendGogglesChargeTooltip(stack, getCharge(stack), getMaxCharge(stack), tooltip);
        BackpackTooltipData.appendEmptyHint(stack, tooltip);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.getOrDefault(AllDataComponents.ENABLED, true) || super.isFoil(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !ItemStack.isSameItem(oldStack, newStack);
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
        return MagnetTier.TIER_2.maxCharge(getCapacityLevel(stack));
    }

    public int getCharge(ItemStack stack) {
        return MagneticEquipmentHelper.getCharge(stack, getMaxCharge(stack));
    }

    public static int getCapacityLevel(ItemStack stack) {
        return MagneticEquipmentHelper.getCapacityLevel(stack);
    }

    public static NonNullList<ItemStack> getFilterSlots(ItemStack stack) {
        return MagneticEquipmentHelper.getFilterSlots(stack, AllDataComponents.BACKPACK_FILTERS, FILTER_SLOTS);
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        return MagneticEquipmentHelper.getFilters(stack, AllDataComponents.BACKPACK_FILTERS, FILTER_SLOTS);
    }

    public static void setFilters(ItemStack stack, List<ItemStack> filters) {
        MagneticEquipmentHelper.setFilters(stack, AllDataComponents.BACKPACK_FILTERS, FILTER_SLOTS, filters);
    }
}
