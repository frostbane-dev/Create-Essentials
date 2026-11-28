package com.stackultra.essentials.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for Create: Essentials (saved to {@code config/createessentials-client.toml}).
 * Safe to reference from common mod initialization and headless unit tests without loading client-only GUI classes.
 */
public final class ClientConfig {

    public enum UiTheme {
        VANILLA,
        DARK
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<UiTheme> UI_THEME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        UI_THEME = builder
                .comment("Visual theme for Create: Essentials custom GUIs (Mechanical Backpack, Netherite Backpack, Magnetic Backpack, Rotational Magnet, Precision Magnet).",
                        "VANILLA = classic Minecraft gray container style, DARK = custom dark beveled style.")
                .translation("createessentials.configuration.uiTheme")
                .defineEnum("uiTheme", UiTheme.VANILLA);

        SPEC = builder.build();
    }

    private ClientConfig() {
    }

    /**
     * Returns the currently configured UI theme, safely falling back to {@link UiTheme#VANILLA}
     * if the config spec has not been loaded yet (e.g., during early startup or headless tests).
     */
    public static UiTheme getUiTheme() {
        if (!SPEC.isLoaded()) {
            return UiTheme.VANILLA;
        }
        try {
            UiTheme value = UI_THEME.get();
            return value != null ? value : UiTheme.VANILLA;
        } catch (IllegalStateException ignored) {
            return UiTheme.VANILLA;
        }
    }
}
