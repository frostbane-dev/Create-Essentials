package com.stackultra.essentials.content.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;

/**
 * Audio constants and helpers shared across Mechanical, Netherite Mechanical, and Magnetic Backpacks.
 * <p>
 * Block place/break/step sounds use {@link SoundType#WOOL}, while opening a backpack uses
 * Create's exact {@code ToolboxBlockEntity#tickAudio()} open sound:
 * <ul>
 *   <li>{@link SoundEvents#IRON_DOOR_OPEN}: volume {@code 0.25F}, pitch {@code 1.2F + rand * 0.1F}</li>
 *   <li>{@link SoundEvents#CHEST_OPEN}: volume {@code 0.1F}, pitch {@code 1.1F + rand * 0.1F}</li>
 * </ul>
 */
public final class BackpackSounds {

    public static final SoundType BLOCK_SOUND_TYPE = SoundType.WOOL;

    public static final float LATCH_OPEN_VOLUME = 0.25F;
    public static final float LATCH_OPEN_PITCH_BASE = 1.2F;
    public static final float LATCH_OPEN_PITCH_VARIANCE = 0.1F;

    public static final float LID_OPEN_VOLUME = 0.1F;
    public static final float LID_OPEN_PITCH_BASE = 1.1F;
    public static final float LID_OPEN_PITCH_VARIANCE = 0.1F;

    private BackpackSounds() {
    }

    public static void playOpenSound(Level level, BlockPos pos) {
        if (level == null || pos == null)
            return;
        playOpenSound(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundSource.BLOCKS);
    }

    public static void playOpenSound(Player player) {
        if (player == null)
            return;
        playOpenSound(player.level(), player.getX(), player.getY(), player.getZ(), SoundSource.PLAYERS);
    }

    public static void playOpenSound(Level level, double x, double y, double z, SoundSource source) {
        if (level == null || level.isClientSide())
            return;
        RandomSource random = level.getRandom();
        level.playSound(null, x, y, z, SoundEvents.IRON_DOOR_OPEN, source,
                LATCH_OPEN_VOLUME, random.nextFloat() * LATCH_OPEN_PITCH_VARIANCE + LATCH_OPEN_PITCH_BASE);
        level.playSound(null, x, y, z, SoundEvents.CHEST_OPEN, source,
                LID_OPEN_VOLUME, random.nextFloat() * LID_OPEN_PITCH_VARIANCE + LID_OPEN_PITCH_BASE);
    }
}
