package com.stackultra.essentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MechanicalBackpackItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Shared worn-backpack rendering helper used by both vanilla chestplate armor rendering
 * ({@link BackpackArmorLayer}) and Curios back-slot rendering ({@code BackCurioRenderer}).
 * <p>
 * Renders the backpack upright (top handle / Magnetic top shaft pointing toward the head),
 * facing outward (front flap & Create Toolbox facing away from the player's back while rear
 * straps/gears sit flush against the torso), properly proportioned to the torso, and following
 * {@link HumanoidModel#body} including crouching pitch and chestplate armor offset.
 */
@OnlyIn(Dist.CLIENT)
public final class WornBackpackRenderer {

    public static final float SCALE = 0.85F;
    public static final double OFFSET_Y = 0.355D;
    public static final double OFFSET_Z_BARE = 0.325D;
    public static final double OFFSET_Z_ARMORED = 0.385D;

    private WornBackpackRenderer() {
    }

    public static boolean isBackpack(ItemStack stack) {
        if (stack == null || stack.isEmpty())
            return false;
        return stack.getItem() instanceof MechanicalBackpackItem
                || stack.getItem() instanceof MagneticBackpackItem;
    }

    public static void render(ItemStack stack,
                              LivingEntity entity,
                              HumanoidModel<?> humanoidModel,
                              PoseStack poseStack,
                              MultiBufferSource bufferSource,
                              int light,
                              boolean wearingInChestSlot) {
        if (!isBackpack(stack))
            return;
        if (entity.getPose() == Pose.SLEEPING)
            return;

        boolean hasChestArmorUnderneath = !wearingInChestSlot
                && !entity.getItemBySlot(EquipmentSlot.CHEST).isEmpty();
        double zOffset = hasChestArmorUnderneath ? OFFSET_Z_ARMORED : OFFSET_Z_BARE;

        poseStack.pushPose();
        humanoidModel.body.translateAndRotate(poseStack);

        // Position centered on the upper/mid back, flush against torso (or outer chestplate)
        poseStack.translate(0.0D, OFFSET_Y, zOffset);

        // Rotate 180 deg around X: right-handed flip that brings +Y (top handle/shaft) upward
        // in HumanoidModel space (where +Y points down) AND rotates -Z (front flap/toolbox)
        // to +Z (facing outward away from the player's back) without mirroring geometry.
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.scale(SCALE, SCALE, SCALE);

        // Use ItemDisplayContext.NONE so display.fixed's 0.62 scale does not double-shrink the model
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.NONE,
                light,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                entity.level(),
                entity.getId());

        poseStack.popPose();
    }
}
