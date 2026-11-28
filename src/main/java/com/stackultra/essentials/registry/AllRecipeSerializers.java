package com.stackultra.essentials.registry;

import com.stackultra.essentials.CreateEssentials;
import com.stackultra.essentials.content.backpack.BackpackDyeRecipe;
import com.stackultra.essentials.content.backpack.MagneticBackpackRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class AllRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, CreateEssentials.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, MagneticBackpackRecipe.Serializer> MAGNETIC_BACKPACK =
            RECIPE_SERIALIZERS.register("magnetic_backpack", MagneticBackpackRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<BackpackDyeRecipe>> BACKPACK_DYE =
            RECIPE_SERIALIZERS.register("backpack_dye", () -> new SimpleCraftingRecipeSerializer<>(BackpackDyeRecipe::new));

    public static void register(IEventBus bus) {
        RECIPE_SERIALIZERS.register(bus);
    }
}
