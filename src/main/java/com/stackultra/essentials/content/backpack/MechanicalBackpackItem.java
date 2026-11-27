package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.DropSyncHandler;
import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

public class MechanicalBackpackItem extends BlockItem implements Equipable {

    public MechanicalBackpackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean onDroppedByPlayer(ItemStack item, Player player) {
        DropSyncHandler.markHotbarDropPending(player);
        return super.onDroppedByPlayer(item, player);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            if (level.isClientSide)
                return InteractionResultHolder.success(stack);

            PortableToolboxInventory tb = PortableToolboxInventory.forStack(stack);
            if (player.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> BackpackMenu.create(id, inv, stack),
                            stack.getHoverName()),
                    buf -> BackpackMenu.writeItemOpenData(buf, player, stack, tb)).isPresent()) {
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
    public Holder<SoundEvent> getEquipSound() {
        return SoundEvents.ARMOR_EQUIP_LEATHER;
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    protected BackpackTooltipData.Tier getTooltipTier() {
        return BackpackTooltipData.Tier.MECHANICAL;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return BackpackTooltipData.fromStack(stack, getTooltipTier());
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        BackpackTooltipData.appendEmptyHint(stack, tooltip);
    }
}
