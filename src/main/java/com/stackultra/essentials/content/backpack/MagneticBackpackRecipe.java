package com.stackultra.essentials.content.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.content.magnet.RotationalMagnetItem;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllRecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;

public class MagneticBackpackRecipe extends ShapedRecipe {

    private final ItemStack result;

    public MagneticBackpackRecipe(String group, CraftingBookCategory category, ShapedRecipePattern pattern,
                                  ItemStack result, boolean showNotification) {
        super(group, category, pattern, result, showNotification);
        this.result = result;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack output = super.assemble(input, registries);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty())
                continue;
            if (stack.getItem() instanceof NetheriteBackpackItem) {
                ItemContainerContents contents =
                        stack.getOrDefault(AllDataComponents.BACKPACK_INVENTORY, ItemContainerContents.EMPTY);
                output.set(AllDataComponents.BACKPACK_INVENTORY, contents);
            } else if (stack.getItem() instanceof RotationalMagnetItem magnet && magnet.getTier() == MagnetTier.TIER_2) {
                ItemContainerContents filters =
                        stack.getOrDefault(AllDataComponents.MAGNET_FILTERS, ItemContainerContents.EMPTY);
                output.set(AllDataComponents.BACKPACK_FILTERS, filters);
            }
        }
        output.set(AllDataComponents.CHARGE, 0);
        return output;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return AllRecipeSerializers.MAGNETIC_BACKPACK.get();
    }

    public static class Serializer implements RecipeSerializer<MagneticBackpackRecipe> {

        public static final MapCodec<MagneticBackpackRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.STRING.optionalFieldOf("group", "").forGetter(MagneticBackpackRecipe::getGroup),
                        CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC)
                                .forGetter(MagneticBackpackRecipe::category),
                        ShapedRecipePattern.MAP_CODEC.forGetter(recipe -> recipe.pattern),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                        Codec.BOOL.optionalFieldOf("show_notification", true)
                                .forGetter(MagneticBackpackRecipe::showNotification)
                )
                .apply(instance, MagneticBackpackRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, MagneticBackpackRecipe> STREAM_CODEC =
                StreamCodec.of(Serializer::toNetwork, Serializer::fromNetwork);

        @Override
        public MapCodec<MagneticBackpackRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MagneticBackpackRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private static MagneticBackpackRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
            String group = buffer.readUtf();
            CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
            ShapedRecipePattern pattern = ShapedRecipePattern.STREAM_CODEC.decode(buffer);
            ItemStack result = ItemStack.STREAM_CODEC.decode(buffer);
            boolean showNotification = buffer.readBoolean();
            return new MagneticBackpackRecipe(group, category, pattern, result, showNotification);
        }

        private static void toNetwork(RegistryFriendlyByteBuf buffer, MagneticBackpackRecipe recipe) {
            buffer.writeUtf(recipe.getGroup());
            buffer.writeEnum(recipe.category());
            ShapedRecipePattern.STREAM_CODEC.encode(buffer, recipe.pattern);
            ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
            buffer.writeBoolean(recipe.showNotification());
        }
    }
}
