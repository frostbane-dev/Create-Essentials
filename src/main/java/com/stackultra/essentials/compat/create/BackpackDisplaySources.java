package com.stackultra.essentials.compat.create;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlockEntity;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nullable;

/**
 * Native Create Display Link sources attached directly to placed Mechanical, Netherite Mechanical,
 * and Magnetic Backpacks (complementing Threshold Switch & Content Observer sources).
 */
public final class BackpackDisplaySources {

    private BackpackDisplaySources() {
    }

    @Nullable
    static IItemHandler resolveBackpackHandler(@Nullable BlockEntity be) {
        if (be instanceof MechanicalBackpackBlockEntity mechBe)
            return mechBe.getItemHandler();
        if (be instanceof MagneticBackpackBlockEntity magBe)
            return magBe.getItemHandler();
        return null;
    }

    /**
     * Computes normalized storage occupancy ({@code 0.0F .. 1.0F}) across all slots of an {@link IItemHandler}.
     */
    public static float computeFillRatio(@Nullable IItemHandler handler) {
        if (handler == null || handler.getSlots() <= 0)
            return 0.0F;
        double sum = 0.0D;
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty())
                continue;
            int max = Math.min(handler.getSlotLimit(i), stack.getMaxStackSize());
            if (max > 0) {
                sum += Math.min(1.0D, stack.getCount() / (double) max);
            }
        }
        return (float) Math.clamp(sum / slots, 0.0D, 1.0D);
    }

    /**
     * Computes total item count across all slots of an {@link IItemHandler}.
     */
    public static int computeTotalItemCount(@Nullable IItemHandler handler) {
        if (handler == null)
            return 0;
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (!stack.isEmpty())
                total += stack.getCount();
        }
        return total;
    }

    /**
     * Computes normalized magnetic charge ratio ({@code 0.0F .. 1.0F}).
     */
    public static float computeChargeRatio(int charge, int maxCharge) {
        if (maxCharge <= 0)
            return 0.0F;
        return Math.clamp(charge / (float) maxCharge, 0.0F, 1.0F);
    }

    public static class FillLevel extends PercentOrProgressBarDisplaySource {

        @Override
        @Nullable
        protected Float getProgress(DisplayLinkContext context) {
            IItemHandler handler = resolveBackpackHandler(context.getSourceBlockEntity());
            if (handler == null)
                return null;
            return computeFillRatio(handler);
        }

        @Override
        protected boolean progressBarActive(DisplayLinkContext context) {
            return context.sourceConfig().getInt("Mode") != 0;
        }

        @Override
        protected String getTranslationKey() {
            return "backpack_fill_level";
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
            super.initConfigurationWidgets(context, builder, isFirstLine);
            if (isFirstLine)
                return;
            builder.addSelectionScrollInput(0, 120, (si, l) -> si
                            .forOptions(CreateLang.translatedOptions("display_source.fill_level", "percent", "progress_bar"))
                            .titled(CreateLang.translateDirect("display_source.fill_level.display")),
                    "Mode");
        }

        @Override
        protected boolean allowsLabeling(DisplayLinkContext context) {
            return true;
        }
    }

    public static class ItemCount extends NumericSingleLineDisplaySource {

        @Override
        protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
            IItemHandler handler = resolveBackpackHandler(context.getSourceBlockEntity());
            if (handler == null)
                return ZERO.copy();
            return Component.literal(String.valueOf(computeTotalItemCount(handler)));
        }

        @Override
        protected String getTranslationKey() {
            return "backpack_item_count";
        }

        @Override
        protected boolean allowsLabeling(DisplayLinkContext context) {
            return true;
        }
    }

    public static class ChargeLevel extends PercentOrProgressBarDisplaySource {

        @Override
        @Nullable
        protected Float getProgress(DisplayLinkContext context) {
            if (!(context.getSourceBlockEntity() instanceof MagneticBackpackBlockEntity magBe))
                return null;
            return computeChargeRatio(magBe.getCharge(), magBe.getMaxCharge());
        }

        @Override
        protected boolean progressBarActive(DisplayLinkContext context) {
            return context.sourceConfig().getInt("Mode") != 0;
        }

        @Override
        protected String getTranslationKey() {
            return "backpack_charge";
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
            super.initConfigurationWidgets(context, builder, isFirstLine);
            if (isFirstLine)
                return;
            builder.addSelectionScrollInput(0, 120, (si, l) -> si
                            .forOptions(CreateLang.translatedOptions("display_source.fill_level", "percent", "progress_bar"))
                            .titled(CreateLang.translateDirect("display_source.fill_level.display")),
                    "Mode");
        }

        @Override
        protected boolean allowsLabeling(DisplayLinkContext context) {
            return true;
        }
    }
}
