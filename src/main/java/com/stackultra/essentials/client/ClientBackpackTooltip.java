package com.stackultra.essentials.client;

import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import com.stackultra.essentials.content.backpack.PortableToolboxInventory;
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

    private static final int TOOLBOX_COLS = PortableToolboxInventory.COMPARTMENTS;
    private static final int TOOLBOX_ROW_W = TOOLBOX_COLS * SLOT_SIZE;
    private static final int SECTION_GAP = 5;

    private static final int PAD = 4;
    private static final int PANEL_W = PAD + GRID_W + PAD;
    private static final int BASE_PANEL_H = PAD + GRID_H + PAD;
    private static final int FULL_PANEL_H = PAD + GRID_H + SECTION_GAP + SLOT_SIZE + PAD;

    private final BackpackTooltipData data;
    private final BackpackTooltipPalette palette;

    public ClientBackpackTooltip(BackpackTooltipData data) {
        this.data = data;
        this.palette = BackpackTooltipPalette.resolve(data.tier(), data.dyeColor(), data.customRgb());
    }

    @Override
    public int getHeight() {
        return (data.hasToolboxData() ? FULL_PANEL_H : BASE_PANEL_H) + 2;
    }

    @Override
    public int getWidth(Font font) {
        return PANEL_W;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        int panelH = data.hasToolboxData() ? FULL_PANEL_H : BASE_PANEL_H;

        // Minimalist beveled dark gray (or per-color tinted) panel matching all mod GUIs
        graphics.fill(x, y, x + PANEL_W, y + panelH, palette.panelOutline());
        graphics.fill(x + 1, y + 1, x + PANEL_W - 1, y + panelH - 1, palette.panelBevelLight());
        graphics.fill(x + 1, y + panelH - 2, x + PANEL_W - 1, y + panelH - 1, palette.panelBevelDark());
        graphics.fill(x + PANEL_W - 2, y + 1, x + PANEL_W - 1, y + panelH - 1, palette.panelBevelDark());
        graphics.fill(x + 2, y + 2, x + PANEL_W - 2, y + panelH - 2, palette.panelBg());

        // 9x3 main storage slot grid
        List<ItemStack> slots = data.slots();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int index = row * COLS + col;
                int itemX = x + PAD + col * SLOT_SIZE + 1;
                int itemY = y + PAD + row * SLOT_SIZE + 1;

                DarkGuiStyling.drawDarkSlot(graphics, itemX, itemY);

                if (index < slots.size()) {
                    ItemStack stack = slots.get(index);
                    if (stack != null && !stack.isEmpty()) {
                        graphics.renderItem(stack, itemX, itemY);
                        graphics.renderItemDecorations(font, stack, itemX, itemY);
                    }
                }
            }
        }

        // Cleanly separated minimalist 8-slot portable toolbox row
        if (data.hasToolboxData()) {
            int dividerY = y + PAD + GRID_H + 2;
            graphics.fill(x + PAD, dividerY, x + PANEL_W - PAD, dividerY + 1, palette.panelBevelDark());
            graphics.fill(x + PAD, dividerY + 1, x + PANEL_W - PAD, dividerY + 2, palette.panelBevelLight());

            int toolboxStartX = x + (PANEL_W - TOOLBOX_ROW_W) / 2;
            int toolboxY = y + PAD + GRID_H + SECTION_GAP;
            List<BackpackTooltipData.ToolboxSlotEntry> tbSlots = data.toolboxSlots();

            for (int c = 0; c < TOOLBOX_COLS; c++) {
                int itemX = toolboxStartX + c * SLOT_SIZE + 1;
                int itemY = toolboxY + 1;
                DarkGuiStyling.drawDarkSlot(graphics, itemX, itemY);

                if (c < tbSlots.size()) {
                    BackpackTooltipData.ToolboxSlotEntry entry = tbSlots.get(c);
                    if (entry.count() > 0 && !entry.displayStack().isEmpty()) {
                        graphics.renderItem(entry.displayStack(), itemX, itemY);
                        graphics.renderItemDecorations(font, entry.displayStack(), itemX, itemY, "");
                        DarkGuiStyling.drawCompactCount(graphics, font, entry.count(), itemX, itemY);
                    } else if (!entry.displayStack().isEmpty()) {
                        DarkGuiStyling.drawDarkGhostItem(graphics, entry.displayStack(), itemX, itemY, 0.40F);
                    }
                }
            }
        }
    }
}
