package com.stackultra.essentials.client;

import com.stackultra.essentials.content.backpack.BackpackTooltipData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public record BackpackTooltipPalette(
        int panelBg,
        int panelBevelLight,
        int panelBevelDark,
        int panelOutline,
        int borderStart,
        int borderEnd,
        int tooltipBgStart,
        int tooltipBgEnd
) {

    private static final int DEFAULT_OUTLINE = DarkGuiStyling.COLOR_BORDER_DARK;
    private static final int DEFAULT_BG_START = 0xF01C1C1C;
    private static final int DEFAULT_BG_END = 0xF0141414;

    private static final BackpackTooltipPalette UNPAINTED_DEFAULT = new BackpackTooltipPalette(
            DarkGuiStyling.COLOR_PANEL,
            DarkGuiStyling.COLOR_BEVEL_LIGHT,
            DarkGuiStyling.COLOR_BEVEL_DARK,
            DEFAULT_OUTLINE,
            0xFF6E6E6E,
            0xFF3A3A3A,
            DEFAULT_BG_START,
            DEFAULT_BG_END);

    private static final Map<DyeColor, BackpackTooltipPalette> DYE_PALETTES = new EnumMap<>(DyeColor.class);

    static {
        putDye(DyeColor.WHITE, 0xFF7D7D85, 0xFFA6A6B0, 0xFF4C4C54, 0xFFD0D0DA, 0xFF7A7A85);
        putDye(DyeColor.ORANGE, 0xFF784D2B, 0xFFA86F40, 0xFF4A2E17, 0xFFD9873B, 0xFF7D481B);
        putDye(DyeColor.MAGENTA, 0xFF6E3A6B, 0xFF9C5798, 0xFF422040, 0xFFC75EC0, 0xFF732E6E);
        putDye(DyeColor.LIGHT_BLUE, 0xFF365E78, 0xFF5389AB, 0xFF1E394A, 0xFF62B5E8, 0xFF2B648A);
        putDye(DyeColor.YELLOW, 0xFF75682D, 0xFFA69546, 0xFF473E18, 0xFFD9C04A, 0xFF806C1F);
        putDye(DyeColor.LIME, 0xFF4B6E2C, 0xFF6FA144, 0xFF2C4218, 0xFF7FD147, 0xFF3E751F);
        putDye(DyeColor.PINK, 0xFF7A4B5C, 0xFFAB6E84, 0xFF4A2B36, 0xFFE386A8, 0xFF87445D);
        putDye(DyeColor.GRAY, 0xFF4A4A4F, 0xFF6E6E75, 0xFF2B2B30, 0xFF85858F, 0xFF484850);
        putDye(DyeColor.LIGHT_GRAY, 0xFF66666E, 0xFF8E8E99, 0xFF3D3D42, 0xFFB0B0BA, 0xFF63636B);
        putDye(DyeColor.CYAN, 0xFF2D5E63, 0xFF478C94, 0xFF18383B, 0xFF4BBAC2, 0xFF22666E);
        putDye(DyeColor.PURPLE, 0xFF533975, 0xFF7957A6, 0xFF301F47, 0xFF9C66DE, 0xFF512B82);
        putDye(DyeColor.BLUE, 0xFF334778, 0xFF4F6AA8, 0xFF1D2947, 0xFF5C82DE, 0xFF284182);
        putDye(DyeColor.BROWN, 0xFF5E432F, 0xFF8A6449, 0xFF38271A, 0xFFA87650, 0xFF5E3C23);
        putDye(DyeColor.GREEN, 0xFF3B5C33, 0xFF58874D, 0xFF21361C, 0xFF63A854, 0xFF2E5C25);
        putDye(DyeColor.RED, 0xFF733333, 0xFFA34E4E, 0xFF451C1C, 0xFFD95959, 0xFF802929);
        putDye(DyeColor.BLACK, 0xFF33333A, 0xFF54545E, 0xFF1C1C21, 0xFF5E5E6B, 0xFF2D2D36);
    }

    private static void putDye(DyeColor color, int panelBg, int bevelLight, int bevelDark,
                               int borderStart, int borderEnd) {
        DYE_PALETTES.put(color, new BackpackTooltipPalette(
                panelBg, bevelLight, bevelDark, DEFAULT_OUTLINE,
                borderStart, borderEnd, DEFAULT_BG_START, DEFAULT_BG_END));
    }

    public static BackpackTooltipPalette resolve(BackpackTooltipData.Tier tier,
                                                 @Nullable DyeColor dyeColor,
                                                 int customRgb) {
        if (dyeColor != null) {
            BackpackTooltipPalette palette = DYE_PALETTES.get(dyeColor);
            if (palette != null)
                return palette;
        }
        if (customRgb != BackpackTooltipData.NO_CUSTOM_RGB) {
            return fromCustomRgb(customRgb);
        }
        return UNPAINTED_DEFAULT;
    }

    @Nullable
    public static BackpackTooltipPalette resolveForStack(ItemStack stack) {
        BackpackTooltipData.Tier tier = BackpackTooltipData.resolveTier(stack);
        if (tier == null)
            return null;
        return resolve(tier, BackpackTooltipData.resolveDyeColor(stack), BackpackTooltipData.resolveCustomRgb(stack));
    }

    private static BackpackTooltipPalette fromCustomRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Blend custom dye into dark gray so the tinted panel stays clean and items remain crisp
        int panelBg = pack(blend(r, 0x4A, 0.35), blend(g, 0x4A, 0.35), blend(b, 0x4A, 0.35));
        int bevelLight = pack(blend(r, 0x6E, 0.40) + 12, blend(g, 0x6E, 0.40) + 12, blend(b, 0x6E, 0.40) + 12);
        int bevelDark = pack(blend(r, 0x2B, 0.30), blend(g, 0x2B, 0.30), blend(b, 0x2B, 0.30));
        int borderStart = pack(blend(r, 0x6E, 0.65) + 24, blend(g, 0x6E, 0.65) + 24, blend(b, 0x6E, 0.65) + 24);
        int borderEnd = pack(blend(r, 0x3A, 0.55), blend(g, 0x3A, 0.55), blend(b, 0x3A, 0.55));

        return new BackpackTooltipPalette(
                panelBg, bevelLight, bevelDark, DEFAULT_OUTLINE,
                borderStart, borderEnd, DEFAULT_BG_START, DEFAULT_BG_END);
    }

    private static int blend(int color, int base, double weight) {
        return (int) Math.round(color * weight + base * (1.0 - weight));
    }

    private static int pack(int r, int g, int b) {
        int cr = Math.max(0, Math.min(255, r));
        int cg = Math.max(0, Math.min(255, g));
        int cb = Math.max(0, Math.min(255, b));
        return 0xFF000000 | (cr << 16) | (cg << 8) | cb;
    }
}
