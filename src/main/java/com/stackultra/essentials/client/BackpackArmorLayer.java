package com.stackultra.essentials.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stackultra.essentials.compat.curios.CuriosCompat;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renders Mechanical, Netherite Mechanical, and Magnetic Backpacks on the wearer's back
 * when equipped in the vanilla chestplate armor slot (players and armor stands).
 * Guards against double-rendering when a player also has a backpack equipped in the Curios back slot.
 */
@OnlyIn(Dist.CLIENT)
public class BackpackArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {

    public BackpackArmorLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack,
                       MultiBufferSource bufferSource,
                       int packedLight,
                       T entity,
                       float limbSwing,
                       float limbSwingAmount,
                       float partialTicks,
                       float ageInTicks,
                       float netHeadYaw,
                       float headPitch) {
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (!WornBackpackRenderer.isBackpack(chest))
            return;

        // Avoid double-rendering if Curios back slot is already rendering a backpack on this player
        if (entity instanceof Player player && !CuriosCompat.getBackBackpack(player).isEmpty())
            return;

        WornBackpackRenderer.render(chest, entity, getParentModel(), poseStack, bufferSource, packedLight, true);
    }
}
