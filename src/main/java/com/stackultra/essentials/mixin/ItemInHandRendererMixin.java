package com.stackultra.essentials.mixin;

import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void createessentials$stabilizeMagnetHandItem(CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null)
            return;

        ItemStack main = player.getMainHandItem();
        if (isMagnet(main) && ItemStack.isSameItem(main, this.mainHandItem))
            this.mainHandItem = main;

        ItemStack off = player.getOffhandItem();
        if (isMagnet(off) && ItemStack.isSameItem(off, this.offHandItem))
            this.offHandItem = off;
    }

    private static boolean isMagnet(ItemStack stack) {
        return stack.getItem() instanceof RotationalMagnetItem;
    }
}
