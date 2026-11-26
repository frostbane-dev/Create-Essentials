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
        int borderStart,
        int borderEnd,
        int headerBg,
        int headerAccent,
        int divider,
        int tooltipBgStart,
        int tooltipBgEnd
) {

    private static final int DEFAULT_BG_START = 0xF017171A;
    private static final int DEFAULT_BG_END = 0xF0111114;

    private static final BackpackTooltipPalette MECHANICAL_DEFAULT = new BackpackTooltipPalette(
            0xFFD9A14A, 0xFF8C5B20, 0xFF3A3124, 0xFFF2C24C, 0xFF7A5624, DEFAULT_BG_START, DEFAULT_BG_END);

    private static final BackpackTooltipPalette NETHERITE_DEFAULT = new BackpackTooltipPalette(
            0xFFA87682, 0xFF563B43, 0xFF33292D, 0xFFD4A3AF, 0xFF694852, DEFAULT_BG_START, DEFAULT_BG_END);

    private static final BackpackTooltipPalette MAGNETIC_DEFAULT = new BackpackTooltipPalette(
            0xFF5CE1FF, 0xFF7B4DFF, 0xFF23303B, 0xFF7EE8FF, 0xFF47698A, DEFAULT_BG_START, DEFAULT_BG_END);

    private static final Map<DyeColor, BackpackTooltipPalette> DYE_PALETTES = new EnumMap<>(DyeColor.class);

    static {
        putDye(DyeColor.WHITE, 0xFFDCDCE6, 0xFF8E8E9E, 0xFF38383E, 0xFFF0F0F8, 0xFF727280);
        putDye(DyeColor.ORANGE, 0xFFFF9A3C, 0xFFA65214, 0xFF3D2C1E, 0xFFFFB86C, 0xFF8A4C1E);
        putDye(DyeColor.MAGENTA, 0xFFE366D9, 0xFF8C2B87, 0xFF382438, 0xFFF28CEB, 0xFF7A3478);
        putDye(DyeColor.LIGHT_BLUE, 0xFF6BCBFF, 0xFF2B75A8, 0xFF22333D, 0xFF94DCFF, 0xFF34688A);
        putDye(DyeColor.YELLOW, 0xFFF5CC47, 0xFFA67C1E, 0xFF3B3520, 0xFFFFDF73, 0xFF8A6C24);
        putDye(DyeColor.LIME, 0xFF8CE64C, 0xFF438C1F, 0xFF283820, 0xFFA9F573, 0xFF467829);
        putDye(DyeColor.PINK, 0xFFFF96BC, 0xFFA84C6E, 0xFF3B2830, 0xFFFFB5D0, 0xFF8A4660);
        putDye(DyeColor.GRAY, 0xFF9A9AA6, 0xFF565661, 0xFF2E2E33, 0xFFC8C8D4, 0xFF585863);
        putDye(DyeColor.LIGHT_GRAY, 0xFFC2C2CC, 0xFF787885, 0xFF34343A, 0xFFE0E0EA, 0xFF6A6A75);
        putDye(DyeColor.CYAN, 0xFF4CD4D4, 0xFF1F7A82, 0xFF1F3538, 0xFF7AEAEA, 0xFF2B6C73);
        putDye(DyeColor.PURPLE, 0xFFB36BFF, 0xFF642CA6, 0xFF2E233B, 0xFFCBA0FF, 0xFF5D358A);
        putDye(DyeColor.BLUE, 0xFF5E8BFF, 0xFF2848A6, 0xFF22293D, 0xFF8CB0FF, 0xFF344C8A);
        putDye(DyeColor.BROWN, 0xFFC28554, 0xFF734726, 0xFF362A21, 0xFFDEA373, 0xFF6B462B);
        putDye(DyeColor.GREEN, 0xFF69B854, 0xFF326E26, 0xFF243322, 0xFF8ED67A, 0xFF3B6632);
        putDye(DyeColor.RED, 0xFFFF6666, 0xFFA32929, 0xFF3B2323, 0xFFFF8F8F, 0xFF853232);
        putDye(DyeColor.BLACK, 0xFF6C6C7D, 0xFF343440, 0xFF242429, 0xFFB8B8CC, 0xFF4A4A57);
    }

    private static void putDye(DyeColor color, int borderStart, int borderEnd,
                               int headerBg, int headerAccent, int divider) {
        DYE_PALETTES.put(color, new BackpackTooltipPalette(
                borderStart, borderEnd, headerBg, headerAccent, divider, DEFAULT_BG_START, DEFAULT_BG_END));
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
        return switch (tier) {
            case MECHANICAL -> MECHANICAL_DEFAULT;
            case NETHERITE -> NETHERITE_DEFAULT;
            case MAGNETIC -> MAGNETIC_DEFAULT;
        };
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

        // Lift very dark colors so borders and accents stay legible on dark tooltips
        int borderStart = pack(clampChannel((int) (r * 0.90 + 38)), clampChannel((int) (g * 0.90 + 38)), clampChannel((int) (b * 0.90 + 38)));
        int borderEnd = pack(clampChannel((int) (r * 0.55 + 18)), clampChannel((int) (g * 0.55 + 18)), clampChannel((int) (b * 0.55 + 18)));
        int headerBg = pack(clampChannel((int) (r * 0.18 + 38)), clampChannel((int) (g * 0.18 + 38)), clampChannel((int) (b * 0.18 + 40)));
        int headerAccent = pack(clampChannel((int) (r * 0.85 + 64)), clampChannel((int) (g * 0.85 + 64)), clampChannel((int) (b * 0.85 + 64)));
        int divider = pack(clampChannel((int) (r * 0.45 + 24)), clampChannel((int) (g * 0.45 + 24)), clampChannel((int) (b * 0.45 + 26)));

        return new BackpackTooltipPalette(borderStart, borderEnd, headerBg, headerAccent, divider,
                DEFAULT_BG_START, DEFAULT_BG_END);
    }

    private static int clampChannel(int c) {
        return Math.max(0, Math.min(255, c));
    }

    private static int pack(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
