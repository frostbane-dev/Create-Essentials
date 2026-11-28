package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackSounds;
import com.stackultra.essentials.content.backpack.MagneticBackpackBlock;
import com.stackultra.essentials.content.backpack.MechanicalBackpackBlock;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(CreateEssentials.MOD_ID);

    public static final DeferredBlock<RotationalMagnetBlock> ROTATIONAL_MAGNET =
            BLOCKS.register("rotational_magnet", () -> new RotationalMagnetBlock(MagnetTier.TIER_1, magnetProperties()));

    public static final DeferredBlock<RotationalMagnetBlock> PRECISION_MAGNET =
            BLOCKS.register("precision_magnet", () -> new RotationalMagnetBlock(MagnetTier.TIER_2, magnetProperties()));

    public static final DeferredBlock<MechanicalBackpackBlock> MECHANICAL_BACKPACK =
            BLOCKS.register("mechanical_backpack", () -> new MechanicalBackpackBlock(backpackProperties(MapColor.WOOD)));

    public static final DeferredBlock<MagneticBackpackBlock> MAGNETIC_BACKPACK =
            BLOCKS.register("magnetic_backpack", () -> new MagneticBackpackBlock(backpackProperties(MapColor.METAL)));

    private static BlockBehaviour.Properties magnetProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .instabreak()
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties backpackProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(0.8F)
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .sound(BackpackSounds.BLOCK_SOUND_TYPE);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
