package com.stackultra.essentials.client;

import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class ClientBackpackTooltip implements ClientTooltipComponent {

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int SLOT_SIZE = 18;
    private static final int GRID_W = COLS * SLOT_SIZE;
    private static final int GRID_H = ROWS * SLOT_SIZE;

    private static final int PAD = 4;
    private static final int PANEL_W = PAD + GRID_W + PAD;
    private static final int PANEL_H = PAD + GRID_H + PAD;

    private final BackpackTooltipData data;
    private final BackpackTooltipPalette palette;

    public ClientBackpackTooltip(BackpackTooltipData data) {
        this.data = data;
        this.palette = BackpackTooltipPalette.resolve(data.tier(), data.dyeColor(), data.customRgb());
    }

    @Override
    public int getHeight() {
        return PANEL_H + 2;
    }

    @Override
    public int getWidth(Font font) {
        return PANEL_W;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        // Minimalist beveled dark gray (or color-tinted) panel matching all mod GUIs
        graphics.fill(x, y, x + PANEL_W, y + PANEL_H, palette.panelOutline());
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, palette.panelBevelLight());
        graphics.fill(x + 1, y + PANEL_H - 2, x + PANEL_W - 1, y + PANEL_H - 1, palette.panelBevelDark());
        graphics.fill(x + PANEL_W - 2, y + 1, x + PANEL_W - 1, y + PANEL_H - 1, palette.panelBevelDark());
        graphics.fill(x + 2, y + 2, x + PANEL_W - 2, y + PANEL_H - 2, palette.panelBg());

        // Crisp 9x3 storage slot grid
        List<ItemStack> slots = data.slots();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                int itemX = x + PAD + col * SLOT_SIZE + 1;
                int itemY = y + PAD + row * SLOT_SIZE + 1;

                DarkGuiStyling.drawSlot(graphics, itemX, itemY);

                if (index < slots.size()) {
                    ItemStack stack = slots.get(index);
                    if (stack != null && !stack.isEmpty()) {
                        graphics.renderItem(stack, itemX, itemY);
                        graphics.renderItemDecorations(font, stack, itemX, itemY);
                    }
                }
            }
        }
    }
}
