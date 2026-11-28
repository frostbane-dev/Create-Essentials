package com.stackultra.essentials.client;

import com.mojang.blaze3d.platform.Window;
import com.simibubi.create.content.equipment.toolbox.RadialToolboxMenu;
import com.simibubi.create.content.equipment.toolbox.ToolboxBlockEntity;
import com.simibubi.create.content.equipment.toolbox.ToolboxHandler;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.utility.CreateLang;
import com.stackultra.essentials.content.backpack.BackpackHelper;
import com.stackultra.essentials.content.backpack.PortableToolboxMenu;
import com.stackultra.essentials.network.BackpackToolboxPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;

/**
 * Exact functional and visual clone of Create 6.0.9's {@code ToolboxScreen}
 * ({@link AllGuiTextures#TOOLBOX} + offset {@link AllGuiTextures#PLAYER_INVENTORY}
 * + footer checkmark {@link AllIcons#I_CONFIRM} + central {@link AllIcons#I_TOOLBOX}
 * "Return items to Toolbox" button) for the backpack's portable toolbox.
 */
@OnlyIn(Dist.CLIENT)
public class PortableToolboxScreen extends AbstractSimiContainerScreen<PortableToolboxMenu> {

    protected static final AllGuiTextures BG = AllGuiTextures.TOOLBOX;
    protected static final AllGuiTextures PLAYER = AllGuiTextures.PLAYER_INVENTORY;

    // Exact Create ToolboxScreen header styling (5841956 == 0x592424)
    private static final int TITLE_OFFSET_X = 15;
    private static final int TITLE_OFFSET_Y = 4;
    private static final int TITLE_MAX_WIDTH = 150;
    private static final int TITLE_COLOR = 0x592424;

    // Exact Create ToolboxScreen central button offset relative to BG (x = leftPos + 30, y = topPos)
    private static final int DISPOSE_OFFSET_X = 81;
    private static final int DISPOSE_OFFSET_Y = 69;
    private static final int BUTTON_SIZE = 18;

    private IconButton confirmButton;
    private IconButton disposeButton;
    @Nullable
    private IconButton worldToolboxesButton;
    private List<ToolboxBlockEntity> nearbyWorldToolboxes = Collections.emptyList();
    private boolean cursorCenteredOnOpen = false;

    public PortableToolboxScreen(PortableToolboxMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(30 + BG.getWidth(), BG.getHeight() + PLAYER.getHeight() - 24);
        setWindowOffset(-11, 0);
        super.init();
        clearWidgets();

        int bgX = leftPos + 30;
        int bgY = topPos;

        // Bottom-right checkmark close button (exact Create ToolboxScreen coordinates)
        confirmButton = new IconButton(bgX + BG.getWidth() - 33, bgY + BG.getHeight() - 24, AllIcons.I_CONFIRM);
        confirmButton.withCallback(() -> {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.closeContainer();
            }
        });
        addRenderableWidget(confirmButton);

        // Central "Return items to Toolbox" button (exact Create ToolboxScreen coordinates)
        disposeButton = new IconButton(bgX + DISPOSE_OFFSET_X, bgY + DISPOSE_OFFSET_Y, AllIcons.I_TOOLBOX);
        disposeButton.withCallback(() -> PacketDistributor.sendToServer(BackpackToolboxPayload.disposeAll()));
        disposeButton.setToolTip(CreateLang.translateDirect("toolbox.depositBox"));
        addRenderableWidget(disposeButton);

        // Optional right-side world-toolbox switcher when placed Create Toolboxes are nearby
        nearbyWorldToolboxes = Collections.emptyList();
        worldToolboxesButton = null;
        if (minecraft != null && minecraft.level != null && minecraft.player != null) {
            nearbyWorldToolboxes = ToolboxHandler.getNearest(minecraft.level, minecraft.player, 8);
            if (!nearbyWorldToolboxes.isEmpty()) {
                worldToolboxesButton = new IconButton(bgX + BG.getWidth() + 4, bgY + DISPOSE_OFFSET_Y, AllIcons.I_TOOLBOX);
                worldToolboxesButton.withCallback(this::switchToWorldToolboxes);
                worldToolboxesButton.setToolTip(
                        Component.translatable("createessentials.gui.world_toolboxes", nearbyWorldToolboxes.size()));
                addRenderableWidget(worldToolboxesButton);
            }
        }

        // Center mouse cursor dead-on the central 18x18 "Return items to Toolbox" button when opened
        if (!cursorCenteredOnOpen && minecraft != null) {
            cursorCenteredOnOpen = true;
            Window window = minecraft.getWindow();
            double guiScale = window.getGuiScale();
            double targetX = (bgX + DISPOSE_OFFSET_X + BUTTON_SIZE / 2.0) * guiScale;
            double targetY = (bgY + DISPOSE_OFFSET_Y + BUTTON_SIZE / 2.0) * guiScale;
            GLFW.glfwSetCursorPos(window.getWindow(), targetX, targetY);
        }
    }

    private void switchToWorldToolboxes() {
        if (minecraft == null || minecraft.player == null || nearbyWorldToolboxes.isEmpty())
            return;
        List<ToolboxBlockEntity> boxes = nearbyWorldToolboxes;
        minecraft.player.closeContainer();
        if (boxes.size() == 1) {
            minecraft.setScreen(new RadialToolboxMenu(boxes, RadialToolboxMenu.State.SELECT_ITEM, boxes.get(0)));
        } else {
            minecraft.setScreen(new RadialToolboxMenu(boxes, RadialToolboxMenu.State.SELECT_BOX, null));
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        int bgX = leftPos + imageWidth - BG.getWidth();
        int bgY = topPos;

        // Complete Create AllGuiTextures.TOOLBOX texture (golden header + dark brown body + grey footer + checkmark tab)
        BG.render(graphics, bgX, bgY);

        String truncatedTitle = BackpackHelper.truncateTitle(
                title.getString(), TITLE_MAX_WIDTH, font::width, font::plainSubstrByWidth);
        graphics.drawString(font, truncatedTitle, bgX + TITLE_OFFSET_X, bgY + TITLE_OFFSET_Y, TITLE_COLOR, false);

        // Create's exact offset AllGuiTextures.PLAYER_INVENTORY tucked against the bottom-left of the footer bar
        int invX = leftPos;
        int invY = topPos + imageHeight - PLAYER.getHeight();
        renderPlayerInventory(graphics, invX, invY);
    }

    @Override
    protected void renderSlotContents(GuiGraphics graphics, ItemStack stack, Slot slot, @Nullable String countString) {
        if (menu.isToolboxSlot(slot.index)) {
            int total = menu.getToolboxCount(slot.index);
            if (!stack.isEmpty() && total > 0) {
                graphics.renderItem(stack, slot.x, slot.y);
                graphics.renderItemDecorations(font, stack, slot.x, slot.y, "");
                DarkGuiStyling.drawCompactCount(graphics, font, total, slot.x, slot.y);
            } else if (!stack.isEmpty()) {
                // Exact Create ToolboxScreen empty-reserved display: item icon with "0" count
                graphics.renderItem(stack, slot.x, slot.y);
                graphics.renderItemDecorations(font, stack, slot.x, slot.y, "0");
            }
            return;
        }
        super.renderSlotContents(graphics, stack, slot, countString);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int x, int y) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && menu.isToolboxSlot(hoveredSlot.index)) {
            if (menu.getToolboxCount(hoveredSlot.index) == 0 && hoveredSlot.hasItem()) {
                ItemStack ghost = hoveredSlot.getItem();
                graphics.renderComponentTooltip(font, List.of(
                        ghost.getHoverName().copy().withStyle(ChatFormatting.GRAY),
                        Component.translatable("createessentials.gui.reserved_hint")
                                .withStyle(ChatFormatting.DARK_GRAY)), x, y);
                return;
            }
        }
        super.renderTooltip(graphics, x, y);
    }
}
