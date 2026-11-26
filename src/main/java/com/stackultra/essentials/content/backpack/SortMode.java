package com.stackultra.essentials.content.backpack;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import javax.annotation.Nullable;

public enum SortMode implements StringRepresentable {
    NAME("name", "A"),
    MOD("mod", "M"),
    COUNT("count", "#");

    public static final Codec<SortMode> CODEC = StringRepresentable.fromEnum(SortMode::values);
    public static final StreamCodec<ByteBuf, SortMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(SortMode::byOrdinal, SortMode::ordinal);

    private static final SortMode[] VALUES = values();

    private final String serializedName;
    private final String shortBadge;

    SortMode(String serializedName, String shortBadge) {
        this.serializedName = serializedName;
        this.shortBadge = shortBadge;
    }

    @Override
    public String getSerializedName() {
        return serializedName;
    }

    public String translationKey() {
        return "createessentials.gui.sort_mode." + serializedName;
    }

    public String shortBadge() {
        return shortBadge;
    }

    public SortMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static SortMode byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length)
            return NAME;
        return VALUES[ordinal];
    }

    @Nullable
    public static SortMode fromIdStrict(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length)
            return null;
        return VALUES[ordinal];
    }

    public static SortMode byName(String name) {
        for (SortMode mode : VALUES) {
            if (mode.serializedName.equals(name))
                return mode;
        }
        return NAME;
    }
}
