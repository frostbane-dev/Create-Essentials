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
        putDye(DyeColor.WHITE, 0xFF92929C, 0xFFC2C2CE, 0xFF5A5A64, 0xFFEBEBF4, 0xFF8F8F9C);
        putDye(DyeColor.ORANGE, 0xFF945B2A, 0xFFCC8446, 0xFF5C3515, 0xFFF59B42, 0xFF99541C);
        putDye(DyeColor.MAGENTA, 0xFF884084, 0xFFBD62B8, 0xFF522250, 0xFFE36ADC, 0xFF8E3488);
        putDye(DyeColor.LIGHT_BLUE, 0xFF3C7296, 0xFF62A4D0, 0xFF21465E, 0xFF72C8FA, 0xFF3278A8);
        putDye(DyeColor.YELLOW, 0xFF94822E, 0xFFCCB64E, 0xFF594E18, 0xFFF4D852, 0xFF9C8220);
        putDye(DyeColor.LIME, 0xFF57882E, 0xFF80BE4B, 0xFF335418, 0xFF90E450, 0xFF498C22);
        putDye(DyeColor.PINK, 0xFF98546C, 0xFFCE7C99, 0xFF5C2F40, 0xFFF896BA, 0xFFA24D6D);
        putDye(DyeColor.GRAY, 0xFF5A5A64, 0xFF848490, 0xFF35353C, 0xFF9E9EAC, 0xFF565660);
        putDye(DyeColor.LIGHT_GRAY, 0xFF7A7A86, 0xFFA8A8B6, 0xFF4A4A54, 0xFFC8C8D4, 0xFF747480);
        putDye(DyeColor.CYAN, 0xFF2E747B, 0xFF4FA8B2, 0xFF19464B, 0xFF58D2DC, 0xFF267B85);
        putDye(DyeColor.PURPLE, 0xFF674294, 0xFF9466CC, 0xFF3C2359, 0xFFB678FA, 0xFF6432A2);
        putDye(DyeColor.BLUE, 0xFF3A5699, 0xFF5E83D4, 0xFF20325E, 0xFF6E98FA, 0xFF3050A4);
        putDye(DyeColor.BROWN, 0xFF745036, 0xFFA67652, 0xFF462E1D, 0xFFC48A5E, 0xFF724628);
        putDye(DyeColor.GREEN, 0xFF427338, 0xFF65A356, 0xFF25451E, 0xFF72C45E, 0xFF347028);
        putDye(DyeColor.RED, 0xFF903838, 0xFFC85858, 0xFF571E1E, 0xFFF26666, 0xFF9A2E2E);
        putDye(DyeColor.BLACK, 0xFF3E3E48, 0xFF646474, 0xFF202028, 0xFF747486, 0xFF383844);
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

        // Lively but grounded custom-dye tint keeping inner dark slots crisp
        int panelBg = pack(blend(r, 0x4A, 0.45), blend(g, 0x4A, 0.45), blend(b, 0x4A, 0.45));
        int bevelLight = pack(blend(r, 0x6E, 0.50) + 18, blend(g, 0x6E, 0.50) + 18, blend(b, 0x6E, 0.50) + 18);
        int bevelDark = pack(blend(r, 0x2B, 0.36), blend(g, 0x2B, 0.36), blend(b, 0x2B, 0.36));
        int borderStart = pack(blend(r, 0x6E, 0.75) + 28, blend(g, 0x6E, 0.75) + 28, blend(b, 0x6E, 0.75) + 28);
        int borderEnd = pack(blend(r, 0x3A, 0.62), blend(g, 0x3A, 0.62), blend(b, 0x3A, 0.62));

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
