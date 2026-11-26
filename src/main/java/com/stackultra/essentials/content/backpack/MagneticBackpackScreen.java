package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllItems;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.stackultra.essentials.client.GuiStyling;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class MagneticBackpackScreen extends AbstractSimiContainerScreen<MagneticBackpackMenu> {

    private static final ResourceLocation SLOT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/slot");

    private static final int FILTER_X = -21;
    private static final int FILTER_Y = 18;
    private static final int STORAGE_X = 8;
    private static final int STORAGE_Y = 18;
    private static final int PLAYER_Y = 84;
    private static final int HOTBAR_Y = 142;
    private static final int LABEL_Y = 72;

    private static final int TAB_WIDTH = 25;
    private static final int TAB_TOP = 16;
    private static final int TAB_HEIGHT = 56;

    public MagneticBackpackScreen(MagneticBackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void init() {
        setWindowSize(174, 166);
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTicks, int mouseX, int mouseY) {
        drawPanel(graphics);

        for (int i = 0; i < MagneticBackpackMenu.FILTER_SLOTS; i++)
            graphics.blitSprite(SLOT_SPRITE, leftPos + FILTER_X - 1, topPos + FILTER_Y + i * 18 - 1, 18, 18);

        ItemStack ghostFilter = AllItems.FILTER.asStack();
        for (int i = 0; i < MagneticBackpackMenu.FILTER_SLOTS; i++) {
            if (menu.slots.get(MagneticBackpackMenu.STORAGE_SLOTS + i).hasItem())
                continue;
            GuiStyling.drawGhostItem(graphics, ghostFilter, leftPos + FILTER_X, topPos + FILTER_Y + i * 18);
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + STORAGE_Y + row * 18 - 1, 18, 18);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + PLAYER_Y + row * 18 - 1, 18, 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            graphics.blitSprite(SLOT_SPRITE, leftPos + STORAGE_X + col * 18 - 1, topPos + HOTBAR_Y - 1, 18, 18);
        }
    }

    private void drawPanel(GuiGraphics g) {
        int mx = leftPos;
        int my = topPos;
        int mw = imageWidth;
        int mh = imageHeight;
        int tx = mx - TAB_WIDTH;
        int ty = my + TAB_TOP;
        int tb = ty + TAB_HEIGHT;

        g.fill(mx, my, mx + mw, my + mh, GuiStyling.COLOR_PANEL);
        g.fill(tx, ty, mx, tb, GuiStyling.COLOR_PANEL);

        g.fill(mx + mw - 2, my + 1, mx + mw - 1, my + mh - 1, GuiStyling.COLOR_BEVEL_DARK);
        g.fill(mx + 1, my + mh - 2, mx + mw - 1, my + mh - 1, GuiStyling.COLOR_BEVEL_DARK);
        g.fill(tx + 1, tb - 2, mx, tb - 1, GuiStyling.COLOR_BEVEL_DARK);

        g.fill(mx + 1, my + 1, mx + mw - 1, my + 2, GuiStyling.COLOR_BEVEL_LIGHT);
        g.fill(mx + 1, my + 1, mx + 2, ty, GuiStyling.COLOR_BEVEL_LIGHT);
        g.fill(tx + 1, ty + 1, mx, ty + 2, GuiStyling.COLOR_BEVEL_LIGHT);
        g.fill(tx + 1, ty + 1, tx + 2, tb - 1, GuiStyling.COLOR_BEVEL_LIGHT);
        g.fill(mx + 1, tb, mx + 2, my + mh - 1, GuiStyling.COLOR_BEVEL_LIGHT);

        g.fill(mx, my, mx + mw, my + 1, GuiStyling.COLOR_BORDER);
        g.fill(mx + mw - 1, my, mx + mw, my + mh, GuiStyling.COLOR_BORDER);
        g.fill(mx, my + mh - 1, mx + mw, my + mh, GuiStyling.COLOR_BORDER);
        g.fill(mx, my, mx + 1, ty + 1, GuiStyling.COLOR_BORDER);
        g.fill(mx, tb - 1, mx + 1, my + mh, GuiStyling.COLOR_BORDER);
        g.fill(tx, ty, mx, ty + 1, GuiStyling.COLOR_BORDER);
        g.fill(tx, ty, tx + 1, tb, GuiStyling.COLOR_BORDER);
        g.fill(tx, tb - 1, mx, tb, GuiStyling.COLOR_BORDER);
    }

    @Override
    protected void renderForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        String titleText = title.getString();
        if (font.width(titleText) > imageWidth - 32)
            titleText = font.plainSubstrByWidth(titleText, imageWidth - 32 - font.width("...")) + "...";
        graphics.drawString(font, titleText, leftPos + 8, topPos + 6, GuiStyling.COLOR_TEXT, false);

        graphics.drawString(font, playerInventoryTitle, leftPos + STORAGE_X, topPos + LABEL_Y, GuiStyling.COLOR_TEXT, false);

        super.renderForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
