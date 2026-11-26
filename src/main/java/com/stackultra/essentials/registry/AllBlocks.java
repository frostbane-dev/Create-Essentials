package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
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
            BLOCKS.register("mechanical_backpack", () -> new MechanicalBackpackBlock(backpackProperties()));

    public static final DeferredBlock<MagneticBackpackBlock> MAGNETIC_BACKPACK =
            BLOCKS.register("magnetic_backpack", () -> new MagneticBackpackBlock(magnetProperties()));

    private static BlockBehaviour.Properties magnetProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .instabreak()
                .sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties backpackProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .instabreak()
                .sound(SoundType.WOOD);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }
}
