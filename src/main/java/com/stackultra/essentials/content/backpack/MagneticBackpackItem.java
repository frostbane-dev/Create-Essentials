package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllEnchantments;
import com.stackultra.essentials.content.ChargeDisplay;
import com.stackultra.essentials.content.GogglesHelper;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MagneticBackpackItem extends BlockItem implements Equipable {

    public static final int BAR_COLOR = 0xFFFFFF;
    public static final int FILTER_SLOTS = 3;

    public MagneticBackpackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide)
                return InteractionResultHolder.success(stack);

            PortableToolboxInventory tb = PortableToolboxInventory.forStack(stack);
            player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> MagneticBackpackMenu.create(id, inv, stack),
                            stack.getHoverName()),
                    buf -> {
                        buf.writeBoolean(false);
                        ItemContainerContents.STREAM_CODEC.encode(buf,
                                stack.getOrDefault(AllDataComponents.BACKPACK_FILTERS, ItemContainerContents.EMPTY));
                        buf.writeBoolean(stack.getOrDefault(AllDataComponents.ENABLED, true));
                        buf.writeVarInt(getCharge(stack));
                        buf.writeVarInt(getMaxCharge(stack));
                        for (int c = 0; c < PortableToolboxInventory.COMPARTMENTS; c++)
                            buf.writeVarInt(tb.getTotalCount(c));
                    });
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
        if (GogglesHelper.isViewerWearingGoggles()) {
            boolean enabled = stack.getOrDefault(AllDataComponents.ENABLED, true);
            boolean charging = stack.getOrDefault(AllDataComponents.CHARGING, false);

            tooltip.add(Component.translatable("createessentials.tooltip.status", ChargeDisplay.status(enabled))
                    .withStyle(ChatFormatting.GRAY));
            if (charging)
                tooltip.add(ChargeDisplay.chargingLine());
            tooltip.add(ChargeDisplay.charge(getCharge(stack), getMaxCharge(stack)));
        }
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
        return enchantment.is(AllEnchantments.CAPACITY) || super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Mth.clamp(getCharge(stack) / (float) getMaxCharge(stack), 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    public int getMaxCharge(ItemStack stack) {
        return MagnetTier.TIER_2.maxCharge(getCapacityLevel(stack));
    }

    public int getCharge(ItemStack stack) {
        return Math.min(stack.getOrDefault(AllDataComponents.CHARGE, 0), getMaxCharge(stack));
    }

    public static int getCapacityLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getTagEnchantments();
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(AllEnchantments.CAPACITY))
                return enchantments.getLevel(enchantment);
        }
        return 0;
    }

    public static NonNullList<ItemStack> getFilterSlots(ItemStack stack) {
        ItemContainerContents contents =
                stack.getOrDefault(AllDataComponents.BACKPACK_FILTERS, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> slots = NonNullList.withSize(FILTER_SLOTS, ItemStack.EMPTY);
        contents.copyInto(slots);
        return slots;
    }

    public static List<ItemStack> getFilters(ItemStack stack) {
        NonNullList<ItemStack> slots = getFilterSlots(stack);
        List<ItemStack> filters = new ArrayList<>(FILTER_SLOTS);
        for (ItemStack entry : slots) {
            if (!entry.isEmpty())
                filters.add(entry.copy());
        }
        return filters;
    }

    public static void setFilters(ItemStack stack, List<ItemStack> filters) {
        List<ItemStack> slots = new ArrayList<>(FILTER_SLOTS);
        for (int i = 0; i < FILTER_SLOTS; i++)
            slots.add(i < filters.size() ? filters.get(i).copy() : ItemStack.EMPTY);
        stack.set(AllDataComponents.BACKPACK_FILTERS, ItemContainerContents.fromItems(slots));
    }
}
