package com.stackultra.essentials.client;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.GogglesHelper;
import com.stackultra.essentials.content.backpack.BackpackScreen;
import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import com.stackultra.essentials.content.backpack.MagneticBackpackScreen;
import com.stackultra.essentials.content.magnet.RotationalMagnetScreen;
import com.stackultra.essentials.network.OpenBackpackPayload;
import com.stackultra.essentials.network.OpenMagnetPayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
import com.stackultra.essentials.registry.AllMenus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    static {
        GogglesHelper.registerViewerCheck(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null && GogglesHelper.isWearingGoggles(minecraft.player);
        });
    }

    public static final KeyMapping TOGGLE_ROTATIONAL_MAGNET = new KeyMapping(
            "key.createessentials.toggle_rotational_magnet",
            GLFW.GLFW_KEY_G,
            "key.categories.createessentials");

    public static final KeyMapping OPEN_ROTATIONAL_MAGNET = new KeyMapping(
            "key.createessentials.open_rotational_magnet",
            GLFW.GLFW_KEY_R,
            "key.categories.createessentials");

    public static final KeyMapping OPEN_BACKPACK = new KeyMapping(
            "key.createessentials.open_backpack",
            GLFW.GLFW_KEY_B,
            "key.categories.createessentials");

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AllMenus.ROTATIONAL_MAGNET.get(), RotationalMagnetScreen::new);
        event.register(AllMenus.BACKPACK.get(), BackpackScreen::new);
        event.register(AllMenus.MAGNETIC_BACKPACK.get(), MagneticBackpackScreen::new);
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_ROTATIONAL_MAGNET);
        event.register(OPEN_ROTATIONAL_MAGNET);
        event.register(OPEN_BACKPACK);
    }

    @SubscribeEvent
    public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BackpackTooltipData.class, ClientBackpackTooltip::new);
    }

    @SubscribeEvent
    public static void onTooltipColor(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;
        BackpackTooltipPalette palette = BackpackTooltipPalette.resolveForStack(stack);
        if (palette == null)
            return;
        event.setBackgroundStart(palette.tooltipBgStart());
        event.setBackgroundEnd(palette.tooltipBgEnd());
        event.setBorderStart(palette.borderStart());
        event.setBorderEnd(palette.borderEnd());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (TOGGLE_ROTATIONAL_MAGNET.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
                continue;
            BlockPos target = minecraft.hitResult instanceof BlockHitResult hitResult
                    ? hitResult.getBlockPos()
                    : minecraft.player.blockPosition();
            PacketDistributor.sendToServer(new RotationalMagnetTogglePayload(target, true));
        }
        while (OPEN_ROTATIONAL_MAGNET.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
                continue;
            PacketDistributor.sendToServer(new OpenMagnetPayload());
        }
        while (OPEN_BACKPACK.consumeClick()) {
            if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
                continue;
            PacketDistributor.sendToServer(new OpenBackpackPayload());
        }
    }
}
