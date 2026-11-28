package com.stackultra.essentials.client;

import com.mojang.datafixers.util.Either;
import com.simibubi.create.AllKeys;
import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.GogglesHelper;
import com.stackultra.essentials.content.backpack.BackpackScreen;
import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import com.stackultra.essentials.content.backpack.MagneticBackpackItem;
import com.stackultra.essentials.content.backpack.MagneticBackpackRenderer;
import com.stackultra.essentials.content.backpack.MagneticBackpackScreen;
import com.stackultra.essentials.content.backpack.MagneticBackpackVisual;
import com.stackultra.essentials.content.backpack.PortableToolboxHandler;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import com.stackultra.essentials.content.magnet.RotationalMagnetRenderer;
import com.stackultra.essentials.content.magnet.RotationalMagnetScreen;
import com.stackultra.essentials.content.magnet.RotationalMagnetVisual;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import com.stackultra.essentials.network.OpenBackpackPayload;
import com.stackultra.essentials.network.OpenMagnetPayload;
import com.stackultra.essentials.network.RotationalMagnetTogglePayload;
import com.stackultra.essentials.registry.AllBlockEntities;
import com.stackultra.essentials.registry.AllMenus;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = CreateEssentials.MOD_ID, value = Dist.CLIENT)
public class ClientEvents {

    private static final int CHARGE_TOOLTIP_RIGHT_PADDING = 6;

    static {
        AllPartialModels.init();
        GogglesHelper.registerViewerCheck(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.player != null && GogglesHelper.isWearingGoggles(minecraft.player);
        });
    }

    public static void registerConfigScreen(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
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
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer.builder(AllBlockEntities.ROTATIONAL_MAGNET.get())
                    .factory(RotationalMagnetVisual::new)
                    .apply();
            SimpleBlockEntityVisualizer.builder(AllBlockEntities.MAGNETIC_BACKPACK.get())
                    .factory(MagneticBackpackVisual::new)
                    .apply();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AllBlockEntities.ROTATIONAL_MAGNET.get(), RotationalMagnetRenderer::new);
        event.registerBlockEntityRenderer(AllBlockEntities.MAGNETIC_BACKPACK.get(), MagneticBackpackRenderer::new);
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(AllMenus.ROTATIONAL_MAGNET.get(), RotationalMagnetScreen::new);
        event.register(AllMenus.BACKPACK.get(), BackpackScreen::new);
        event.register(AllMenus.MAGNETIC_BACKPACK.get(), MagneticBackpackScreen::new);
        event.register(AllMenus.PORTABLE_TOOLBOX.get(), PortableToolboxScreen::new);
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
        event.register(TooltipWidthGuard.class, TooltipWidthGuard.Client::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(
                VanillaGuiLayers.HOTBAR,
                ResourceLocation.fromNamespaceAndPath(CreateEssentials.MOD_ID, "portable_toolbox_hotbar"),
                PortableToolboxOverlay.OVERLAY);
    }

    @SubscribeEvent
    public static void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        PortableToolboxOverlay.clear();
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty())
            return;
        if (!(stack.getItem() instanceof RotationalMagnetItem || stack.getItem() instanceof MagneticBackpackItem))
            return;

        Font font = Minecraft.getInstance().font;
        if (font == null)
            return;

        int maxTextWidth = 0;
        for (Either<FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent> element : event.getTooltipElements()) {
            int w = element.map(font::width, component -> 0);
            if (w > maxTextWidth)
                maxTextWidth = w;
        }
        if (maxTextWidth <= 0)
            return;

        int safeWidth = maxTextWidth + CHARGE_TOOLTIP_RIGHT_PADDING;
        int currentMax = event.getMaxWidth();
        if (currentMax > 0 && currentMax < safeWidth) {
            int screenLimit = Math.max(currentMax, event.getScreenWidth() - 16);
            event.setMaxWidth(Math.min(screenLimit, safeWidth));
        }
        event.getTooltipElements().add(Either.right(new TooltipWidthGuard(safeWidth)));
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
            PacketDistributor.sendToServer(RotationalMagnetTogglePayload.forKeybind(target));
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

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onKeyInput(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS)
            return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null)
            return;
        if (minecraft.gameMode == null || minecraft.gameMode.getPlayerMode() == GameType.SPECTATOR)
            return;
        if (!AllKeys.TOOLBELT.doesModifierAndCodeMatch(event.getKey()))
            return;

        ItemStack backpack = PortableToolboxHandler.findPortableToolboxBackpack(minecraft.player);
        if (backpack.isEmpty())
            return;

        PacketDistributor.sendToServer(BackpackToolboxPayload.openScreen());
    }
}
