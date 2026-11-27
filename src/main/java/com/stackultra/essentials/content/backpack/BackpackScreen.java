package com.stackultra.essentials.content.backpack;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class BackpackScreen extends AbstractBackpackScreen<BackpackMenu> {

    private static final int WINDOW_HEIGHT = 213;
    private static final int DIVIDER_STORAGE_Y = 74;
    private static final int TOOLBOX_TITLE_Y = 81;
    private static final int RESTOCK_BTN_Y = 78;
    private static final int DIVIDER_PLAYER_Y = 115;

    public BackpackScreen(BackpackMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected int getWindowHeight() {
        return WINDOW_HEIGHT;
    }

    @Override
    protected int getStorageDividerY() {
        return DIVIDER_STORAGE_Y;
    }

    @Override
    protected int getToolboxTitleY() {
        return TOOLBOX_TITLE_Y;
    }

    @Override
    protected int getRestockButtonY() {
        return RESTOCK_BTN_Y;
    }

    @Override
    protected int getPlayerDividerY() {
        return DIVIDER_PLAYER_Y;
    }

    @Override
    public List<Rect2i> getExtraAreas() {
        return List.of();
    }
}
