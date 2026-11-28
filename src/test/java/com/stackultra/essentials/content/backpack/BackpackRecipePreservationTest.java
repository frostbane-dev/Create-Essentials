package com.stackultra.essentials.content.backpack;

import com.simibubi.create.AllEnchantments;
import com.stackultra.essentials.content.magnet.MagnetTier;
import com.stackultra.essentials.registry.AllDataComponents;
import com.stackultra.essentials.registry.AllItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackpackRecipePreservationTest {

    private static final HolderOwner<Enchantment> TEST_OWNER = new HolderOwner<>() {};

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Holder<Enchantment> enchantmentHolder(ResourceKey<Enchantment> key) {
        return Holder.Reference.createStandAlone(TEST_OWNER, key);
    }

    private static MagneticBackpackRecipe createMagneticBackpackRecipe() {
        ShapedRecipePattern pattern = ShapedRecipePattern.of(
                Map.of(
                        'N', Ingredient.of(AllItems.NETHERITE_BACKPACK.get()),
                        'F', Ingredient.of(AllItems.PRECISION_MAGNET.get())
                ),
                List.of("NF")
        );
        ItemStack result = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        result.set(AllDataComponents.CHARGE, 0);
        return new MagneticBackpackRecipe("", CraftingBookCategory.MISC, pattern, result, true);
    }

    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    @Test
    void magneticBackpackRecipePreservesCustomNameFromNetheriteBackpack() {
        MagneticBackpackRecipe recipe = createMagneticBackpackRecipe();

        ItemStack netherite = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        BackpackColorHelper.setDyeColor(netherite, DyeColor.ORANGE);
        netherite.set(DataComponents.CUSTOM_NAME, Component.literal("Test"));

        ItemStack magnet = new ItemStack(AllItems.PRECISION_MAGNET.get());

        CraftingInput input = CraftingInput.of(2, 1, List.of(netherite, magnet));
        ItemStack output = recipe.assemble(input, registries());

        assertTrue(output.has(DataComponents.CUSTOM_NAME), "Crafted Magnetic Backpack must preserve CUSTOM_NAME");
        assertEquals("Test", output.getHoverName().getString());
        assertEquals(DyeColor.ORANGE, BackpackColorHelper.resolveDyeColor(output));
    }

    @Test
    void magneticBackpackRecipePreservesCapacityFromPrecisionMagnet() {
        MagneticBackpackRecipe recipe = createMagneticBackpackRecipe();
        Holder<Enchantment> capacity = enchantmentHolder(AllEnchantments.CAPACITY);

        ItemStack netherite = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        ItemStack magnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        ItemEnchantments.Mutable magnetEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        magnetEnchants.set(capacity, 3);
        magnet.set(DataComponents.ENCHANTMENTS, magnetEnchants.toImmutable());

        CraftingInput input = CraftingInput.of(2, 1, List.of(netherite, magnet));
        ItemStack output = recipe.assemble(input, registries());

        assertEquals(3, MagneticBackpackItem.getCapacityLevel(output),
                "Capacity enchantment on Precision Magnet must transfer to crafted Magnetic Backpack");
        assertEquals(MagnetTier.TIER_2.maxCharge(3),
                AllItems.MAGNETIC_BACKPACK.get().getMaxCharge(output),
                "Max charge must reflect preserved Capacity III");
        assertTrue(output.isEnchanted(), "Crafted Magnetic Backpack must be enchanted");
        output.set(AllDataComponents.ENABLED, false);
        assertTrue(output.hasFoil(), "Enchanted Magnetic Backpack must retain enchantment glint even when disabled");
    }

    @Test
    void magneticBackpackRecipeMergesHighestEnchantmentsFromBackpackAndMagnet() {
        MagneticBackpackRecipe recipe = createMagneticBackpackRecipe();
        Holder<Enchantment> capacity = enchantmentHolder(AllEnchantments.CAPACITY);
        Holder<Enchantment> unbreaking = enchantmentHolder(Enchantments.UNBREAKING);

        ItemStack netherite = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        netherite.set(DataComponents.CUSTOM_NAME, Component.literal("Test"));
        ItemEnchantments.Mutable backpackEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        backpackEnchants.set(capacity, 2);
        backpackEnchants.set(unbreaking, 3);
        netherite.set(DataComponents.ENCHANTMENTS, backpackEnchants.toImmutable());

        ItemStack magnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        ItemEnchantments.Mutable magnetEnchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        magnetEnchants.set(capacity, 3);
        magnet.set(DataComponents.ENCHANTMENTS, magnetEnchants.toImmutable());

        CraftingInput input = CraftingInput.of(2, 1, List.of(netherite, magnet));
        ItemStack output = recipe.assemble(input, registries());

        assertEquals("Test", output.getHoverName().getString());
        assertEquals(3, MagneticBackpackItem.getCapacityLevel(output),
                "Merged Capacity must keep the highest level across inputs");
        assertEquals(3, output.getTagEnchantments().getLevel(unbreaking),
                "Non-overlapping enchantments from the backpack must be preserved");
        assertEquals(MagnetTier.TIER_2.maxCharge(3),
                AllItems.MAGNETIC_BACKPACK.get().getMaxCharge(output));
    }

    @Test
    void magneticBackpackRecipePreservesStoredEnchantmentsIfPresent() {
        MagneticBackpackRecipe recipe = createMagneticBackpackRecipe();
        Holder<Enchantment> capacity = enchantmentHolder(AllEnchantments.CAPACITY);

        ItemStack netherite = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(capacity, 2);
        netherite.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());

        ItemStack magnet = new ItemStack(AllItems.PRECISION_MAGNET.get());

        CraftingInput input = CraftingInput.of(2, 1, List.of(netherite, magnet));
        ItemStack output = recipe.assemble(input, registries());

        assertEquals(2, MagneticBackpackItem.getCapacityLevel(output));
    }

    @Test
    void backpackDyeAndNetheriteSmithingRecipesPreserveCustomNameAndEnchantments() {
        Holder<Enchantment> capacity = enchantmentHolder(AllEnchantments.CAPACITY);

        // 1. Smithing upgrade: Mechanical Backpack -> Netherite Backpack
        ItemStack mechanical = new ItemStack(AllItems.MECHANICAL_BACKPACK.get());
        mechanical.set(DataComponents.CUSTOM_NAME, Component.literal("Test"));
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(capacity, 2);
        mechanical.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());

        SmithingTransformRecipe smithing = new SmithingTransformRecipe(
                Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                Ingredient.of(AllItems.MECHANICAL_BACKPACK.get()),
                Ingredient.of(Items.NETHERITE_INGOT),
                new ItemStack(AllItems.NETHERITE_BACKPACK.get())
        );
        SmithingRecipeInput smithingInput = new SmithingRecipeInput(
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                mechanical,
                new ItemStack(Items.NETHERITE_INGOT)
        );
        ItemStack netherite = smithing.assemble(smithingInput, registries());
        assertTrue(netherite.has(DataComponents.CUSTOM_NAME));
        assertEquals("Test", netherite.getHoverName().getString());
        assertEquals(2, netherite.getTagEnchantments().getLevel(capacity));

        // 2. Dye recipe: preserve CUSTOM_NAME and enchantments
        BackpackDyeRecipe dyeRecipe = new BackpackDyeRecipe(CraftingBookCategory.EQUIPMENT);
        CraftingInput dyeInput = CraftingInput.of(2, 1, List.of(netherite, new ItemStack(Items.ORANGE_DYE)));
        ItemStack dyed = dyeRecipe.assemble(dyeInput, registries());
        assertTrue(dyed.has(DataComponents.CUSTOM_NAME));
        assertEquals("Test", dyed.getHoverName().getString());
        assertEquals(DyeColor.ORANGE, BackpackColorHelper.resolveDyeColor(dyed));
        assertEquals(2, dyed.getTagEnchantments().getLevel(capacity));
    }

    @Test
    void disabledEnchantedPrecisionMagnetRetainsGlint() {
        Holder<Enchantment> capacity = enchantmentHolder(AllEnchantments.CAPACITY);
        ItemStack magnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        magnet.set(AllDataComponents.ENABLED, false);
        ItemEnchantments.Mutable enchants = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        enchants.set(capacity, 1);
        magnet.set(DataComponents.ENCHANTMENTS, enchants.toImmutable());

        assertTrue(magnet.hasFoil(), "Disabled enchanted magnet must keep enchantment glint");
    }
}
