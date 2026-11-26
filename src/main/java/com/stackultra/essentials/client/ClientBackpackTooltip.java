package com.stackultra.essentials.client;

import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
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

    private static final int PAD = 3;
    private static final int HEADER_H = 13;
    private static final int DIVIDER_H = 1;
    private static final int GRID_TOP_GAP = 2;

    private static final int PANEL_W = PAD + GRID_W + PAD;
    private static final int PANEL_H = 1 + HEADER_H + DIVIDER_H + GRID_TOP_GAP + GRID_H + PAD;

    private static final int BAR_W = 46;
    private static final int BAR_H = 5;

    private final BackpackTooltipData data;
    private final BackpackTooltipPalette palette;
    private final Component occupancyText;

    public ClientBackpackTooltip(BackpackTooltipData data) {
        this.data = data;
        this.palette = BackpackTooltipPalette.resolve(data.tier(), data.dyeColor(), data.customRgb());
        this.occupancyText = Component.translatable("createessentials.tooltip.backpack_slots",
                data.occupiedSlots(), BackpackTooltipData.SLOT_COUNT);
    }

    @Override
    public int getHeight() {
        return PANEL_H + 3;
    }

    @Override
    public int getWidth(Font font) {
        int headerNeeded = font.width(occupancyText) + BAR_W + 16;
        return Math.max(PANEL_W, headerNeeded);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int w = getWidth(font);
        int h = PANEL_H;

        // Neutral dark panel interior
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, DarkGuiStyling.COLOR_PANEL);

        // Tinted header strip & horizontal divider
        int headerBottom = y + 1 + HEADER_H;
        graphics.fill(x + 1, y + 1, x + w - 1, headerBottom, palette.headerBg());
        graphics.fill(x + 1, headerBottom, x + w - 1, headerBottom + DIVIDER_H, palette.divider());

        // Color-tinted 1px outer accent border
        graphics.fill(x, y, x + w, y + 1, palette.borderStart());
        graphics.fill(x, y + h - 1, x + w, y + h, palette.borderEnd());
        graphics.fillGradient(x, y, x + 1, y + h, palette.borderStart(), palette.borderEnd());
        graphics.fillGradient(x + w - 1, y, x + w, y + h, palette.borderStart(), palette.borderEnd());

        // Occupancy text on left of header
        graphics.drawString(font, occupancyText, x + 5, y + 4, palette.headerAccent(), true);

        // Occupancy progress bar on right of header
        int barX = x + w - BAR_W - 5;
        int barY = y + 5;
        graphics.fill(barX - 1, barY - 1, barX + BAR_W + 1, barY + BAR_H + 1, DarkGuiStyling.COLOR_BORDER_DARK);
        graphics.fill(barX, barY, barX + BAR_W, barY + BAR_H, DarkGuiStyling.COLOR_SLOT);
        int fillW = Math.round(BAR_W * (data.occupiedSlots() / (float) BackpackTooltipData.SLOT_COUNT));
        if (fillW > 0) {
            graphics.fill(barX, barY, barX + Math.min(BAR_W, fillW), barY + BAR_H, palette.headerAccent());
        }

        // 9x3 storage slot grid (centered if panel widened for long translations)
        int gridBoxX = x + (w - GRID_W) / 2;
        int gridBoxY = headerBottom + DIVIDER_H + GRID_TOP_GAP;
        List<ItemStack> slots = data.slots();

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                int itemX = gridBoxX + col * SLOT_SIZE + 1;
                int itemY = gridBoxY + row * SLOT_SIZE + 1;

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
