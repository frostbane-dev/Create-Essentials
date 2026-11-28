package com.stackultra.essentials.content.backpack;

import com.stackultra.essentials.content.BlockItemComponentHelper;
import com.stackultra.essentials.content.ItemPullHelper;
import com.stackultra.essentials.content.MenuValidationHelper;
import com.stackultra.essentials.content.magnet.RotationalMagnetBlockEntity;
import com.stackultra.essentials.registry.AllBlocks;
import com.stackultra.essentials.registry.AllItems;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests covering Critical #1 (container validity & locked open-item slots),
 * High #3 (pickupDelay guard), High #4 (DataComponent-aware restock matching),
 * and High #5 (place -> break DataComponent preservation & Schematicannon NETHERITE state).
 */
class AuditFixesTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void blockEntityValidationRejectsRemovedOrDetachedBlockEntities() {
        MechanicalBackpackBlockEntity be = new MechanicalBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MECHANICAL_BACKPACK.get().defaultBlockState());
        assertFalse(MenuValidationHelper.isBlockEntityValid(null, be));
        be.setRemoved();
        assertTrue(be.isRemoved());
        assertFalse(MenuValidationHelper.isBlockEntityValid(null, be));
        assertFalse(MenuValidationHelper.isPlayerCarryingStack(null, new ItemStack(AllItems.MECHANICAL_BACKPACK.get())));
        assertFalse(MenuValidationHelper.isPlayerCarryingStack(null, ItemStack.EMPTY));
    }

    @Test
    void lockedOpenStackClickBlocksPickupMoveSwapThrowButAllowsQuickCraftAndOtherSlots() {
        ItemStack openBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        ItemStack otherItem = new ItemStack(Items.COBBLESTONE, 16);
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, openBackpack);
        container.setItem(1, otherItem);

        AbstractContainerMenu dummyMenu = new AbstractContainerMenu(null, 0) {
            {
                addSlot(new Slot(container, 0, 0, 0));
                addSlot(new Slot(container, 1, 18, 0));
            }

            @Override
            public ItemStack quickMoveStack(Player player, int index) {
                return ItemStack.EMPTY;
            }

            @Override
            public boolean stillValid(Player player) {
                return true;
            }
        };

        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.PICKUP, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.QUICK_MOVE, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.THROW, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 0, ClickType.PICKUP_ALL, null));
        assertTrue(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 1, ClickType.SWAP, null));

        // Drag-split termination (QUICK_CRAFT) and clicks on unrelated slots must stay allowed
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 0, 2, ClickType.QUICK_CRAFT, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, openBackpack, 1, 0, ClickType.PICKUP, null));
        assertFalse(MenuValidationHelper.isLockedOpenStackClick(dummyMenu, ItemStack.EMPTY, 0, 0, ClickType.PICKUP, null));
    }

    @Test
    void pickupGuardRejectsDelayedDeadAndEmptyItems() {
        assertFalse(ItemPullHelper.canPullOrPickup(null));
        assertTrue(ItemPullHelper.isPickable(true, false, false));
        assertFalse(ItemPullHelper.isPickable(true, true, false), "Items with active pickupDelay or setNeverPickUp must be skipped");
        assertFalse(ItemPullHelper.isPickable(false, false, false), "Dead entities must be skipped");
        assertFalse(ItemPullHelper.isPickable(true, false, true), "Empty stacks must be skipped");
    }

    @Test
    void restockMatchesExactDataComponentsWithoutPullingRenamedBlocks() {
        BackpackInventory inv = new BackpackInventory();

        ItemStack renamedStone = new ItemStack(Items.STONE, 12);
        renamedStone.set(DataComponents.CUSTOM_NAME, Component.literal("Special Stone"));
        ItemStack plainStone = new ItemStack(Items.STONE, 24);

        inv.setStackInSlot(0, renamedStone);
        inv.setStackInSlot(1, plainStone);

        // Restocking a plain Stone must skip the renamed stack in slot 0 and pull slot 1
        ItemStack extractedPlain = BackpackRestockHandler.extractMatchingRestock(inv, new ItemStack(Items.STONE));
        assertEquals(24, extractedPlain.getCount());
        assertFalse(extractedPlain.has(DataComponents.CUSTOM_NAME));
        assertEquals(12, inv.getStackInSlot(0).getCount(), "Renamed stone in slot 0 must stay untouched");
        assertTrue(inv.getStackInSlot(1).isEmpty());

        // Restocking with a non-matching custom name returns EMPTY
        ItemStack otherNamed = new ItemStack(Items.STONE);
        otherNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Different Name"));
        assertTrue(BackpackRestockHandler.extractMatchingRestock(inv, otherNamed).isEmpty());

        // Restocking with the exact matching renamed template pulls slot 0
        ItemStack matchingNamed = new ItemStack(Items.STONE);
        matchingNamed.set(DataComponents.CUSTOM_NAME, Component.literal("Special Stone"));
        ItemStack extractedNamed = BackpackRestockHandler.extractMatchingRestock(inv, matchingNamed);
        assertEquals(12, extractedNamed.getCount());
        assertEquals("Special Stone", extractedNamed.getHoverName().getString());
    }

    @Test
    void mechanicalBackpackBlockEntityInitializesNetheriteFromBlockStateAndPreservesComponents() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        // Schematicannon / /setblock placement with NETHERITE=true without loadFromStack
        BlockState netheriteState = AllBlocks.MECHANICAL_BACKPACK.get()
                .defaultBlockState()
                .setValue(MechanicalBackpackBlock.NETHERITE, true);
        MechanicalBackpackBlockEntity schematicBe = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        assertTrue(schematicBe.isNetherite(), "NETHERITE=true blockstate must initialize isNetherite() to true");

        // Place -> NBT save -> NBT load -> break round-trip preserves CUSTOM_NAME and DYED_COLOR
        ItemStack placed = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        placed.set(DataComponents.CUSTOM_NAME, Component.literal("Builder Pack"));
        placed.set(DataComponents.DYED_COLOR, new DyedItemColor(0x3A7BD5, true));

        MechanicalBackpackBlockEntity be = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        be.loadFromStack(placed);
        assertEquals("Builder Pack", be.getDisplayName().getString());

        CompoundTag saved = new CompoundTag();
        be.saveAdditional(saved, registries);

        MechanicalBackpackBlockEntity reloaded = new MechanicalBackpackBlockEntity(BlockPos.ZERO, netheriteState);
        reloaded.loadAdditional(saved, registries);

        ItemStack dropped = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        reloaded.saveToStack(dropped);

        assertEquals("Builder Pack", dropped.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(new DyedItemColor(0x3A7BD5, true), dropped.get(DataComponents.DYED_COLOR));
    }

    @Test
    void magneticBackpackAndRotationalMagnetPreserveCustomNameAndDyeAcrossPlaceBreak() {
        // Magnetic Backpack
        ItemStack placedBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        placedBackpack.set(DataComponents.CUSTOM_NAME, Component.literal("Void Vacuum"));
        placedBackpack.set(DataComponents.DYED_COLOR, new DyedItemColor(0xD95959, true));

        MagneticBackpackBlockEntity backpackBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());
        backpackBe.loadFromStack(placedBackpack);
        assertEquals("Void Vacuum", backpackBe.getDisplayName().getString());

        ItemStack droppedBackpack = new ItemStack(AllItems.MAGNETIC_BACKPACK.get());
        backpackBe.saveToStack(droppedBackpack);
        assertEquals("Void Vacuum", droppedBackpack.get(DataComponents.CUSTOM_NAME).getString());
        assertEquals(new DyedItemColor(0xD95959, true), droppedBackpack.get(DataComponents.DYED_COLOR));

        // Rotational / Precision Magnet
        ItemStack placedMagnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        placedMagnet.set(DataComponents.CUSTOM_NAME, Component.literal("Ore Magnet"));

        RotationalMagnetBlockEntity magnetBe = new RotationalMagnetBlockEntity(
                BlockPos.ZERO, AllBlocks.PRECISION_MAGNET.get().defaultBlockState());
        magnetBe.loadFromStack(placedMagnet);
        assertEquals("Ore Magnet", magnetBe.getDisplayName().getString());

        ItemStack droppedMagnet = new ItemStack(AllItems.PRECISION_MAGNET.get());
        magnetBe.saveToStack(droppedMagnet);
        assertEquals("Ore Magnet", droppedMagnet.get(DataComponents.CUSTOM_NAME).getString());

        // Direct PreservedComponents NBT round-trip check
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        BlockItemComponentHelper.PreservedComponents helper = new BlockItemComponentHelper.PreservedComponents();
        helper.loadFromStack(placedBackpack);
        CompoundTag tag = new CompoundTag();
        helper.writeToTag(tag, registries);

        BlockItemComponentHelper.PreservedComponents restored = new BlockItemComponentHelper.PreservedComponents();
        restored.readFromTag(tag, registries);
        assertEquals("Void Vacuum", restored.getCustomName().getString());
        assertEquals(new DyedItemColor(0xD95959, true), restored.getDyedColor());
    }

    @Test
    void shiftClickPartialExtractAndStackMergePersistWithoutDupeOrLoss() {
        ItemStack backpackStack = new ItemStack(AllItems.MECHANICAL_BACKPACK.get());
        BackpackInventory initial = BackpackInventory.forStack(backpackStack);
        initial.setStackInSlot(0, new ItemStack(Items.DIAMOND, 64));
        initial.setStackInSlot(1, new ItemStack(Items.EMERALD, 10));
        initial.save(backpackStack);

        net.minecraft.world.entity.player.Inventory playerInv = new net.minecraft.world.entity.player.Inventory(null);
        // Fill all 36 player slots with stone except hotbar 0 (32 diamonds) and hotbar 1 (20 emeralds)
        for (int i = 0; i < playerInv.items.size(); i++) {
            playerInv.setItem(i, new ItemStack(Items.STONE, 64));
        }
        playerInv.setItem(0, new ItemStack(Items.DIAMOND, 32));
        playerInv.setItem(1, new ItemStack(Items.EMERALD, 20));

        BackpackMenu menu = BackpackMenu.create(1, playerInv, backpackStack);

        // 1. Partial Shift-click OUT of backpack slot 0 (64 -> 32 remaining in backpack, 64 in hotbar 0)
        menu.quickMoveStack(null, 0);
        assertEquals(64, playerInv.getItem(0).getCount());
        assertEquals(32, menu.getSlot(0).getItem().getCount());
        assertEquals(32, BackpackInventory.forStack(backpackStack).getStackInSlot(0).getCount(),
                "Partial Shift-click out must immediately persist reduced count (no duplication)");

        // 2. Shift-click MERGE INTO existing backpack slot 1 (10 + 20 -> 30 in backpack)
        int hotbar1MenuSlot = menu.getPlayerSlotStart() + 27 + 1;
        menu.quickMoveStack(null, hotbar1MenuSlot);
        assertTrue(playerInv.getItem(1).isEmpty());
        assertEquals(30, menu.getSlot(1).getItem().getCount());
        assertEquals(30, BackpackInventory.forStack(backpackStack).getStackInSlot(1).getCount(),
                "Shift-click merge into existing slot must immediately persist increased count (no item loss)");

        // 3. In-place cursor merge via Slot.safeInsert onto existing backpack slot 1 (30 + 5 -> 35)
        menu.getSlot(1).safeInsert(new ItemStack(Items.EMERALD, 5));
        assertEquals(35, BackpackInventory.forStack(backpackStack).getStackInSlot(1).getCount(),
                "In-place cursor stack merge must immediately persist via SlotItemHandler.setChanged()");
    }

    @Test
    void placedBlockEntityMenusBindDirectlyToLiveFilterInventory() {
        net.minecraft.world.entity.player.Inventory invA = new net.minecraft.world.entity.player.Inventory(null);
        net.minecraft.world.entity.player.Inventory invB = new net.minecraft.world.entity.player.Inventory(null);

        // Magnetic Backpack BlockEntity: two concurrent menus share live filter inventory
        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());
        MagneticBackpackMenu magMenuA = MagneticBackpackMenu.create(1, invA, magBe);
        MagneticBackpackMenu magMenuB = MagneticBackpackMenu.create(2, invB, magBe);

        magBe.getFilterInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        assertEquals(Items.DIAMOND, magMenuA.getSlot(MagneticBackpackMenu.STORAGE_SLOTS).getItem().getItem());
        assertEquals(Items.DIAMOND, magMenuB.getSlot(MagneticBackpackMenu.STORAGE_SLOTS).getItem().getItem(),
                "Second viewer must see live filter updates immediately without stale detached copy");

        // Rotational / Precision Magnet BlockEntity: two concurrent menus share live filter inventory
        RotationalMagnetBlockEntity magnetBe = new RotationalMagnetBlockEntity(
                BlockPos.ZERO, AllBlocks.PRECISION_MAGNET.get().defaultBlockState());
        com.stackultra.essentials.content.magnet.RotationalMagnetMenu magnetMenuA =
                com.stackultra.essentials.content.magnet.RotationalMagnetMenu.create(3, invA, magnetBe);
        com.stackultra.essentials.content.magnet.RotationalMagnetMenu magnetMenuB =
                com.stackultra.essentials.content.magnet.RotationalMagnetMenu.create(4, invB, magnetBe);

        magnetBe.getFilterInventory().setStackInSlot(1, new ItemStack(Items.GOLD_INGOT));
        assertEquals(Items.GOLD_INGOT, magnetMenuA.getSlot(1).getItem().getItem());
        assertEquals(Items.GOLD_INGOT, magnetMenuB.getSlot(1).getItem().getItem(),
                "Second magnet viewer must see live filter updates immediately");
    }

    @Test
    void magneticBackpackRecipePreservesCustomNameAndMergesEnchantments() {
        net.minecraft.world.item.crafting.ShapedRecipePattern pattern =
                net.minecraft.world.item.crafting.ShapedRecipePattern.of(
                        java.util.Map.of(
                                'N', net.minecraft.world.item.crafting.Ingredient.of(AllItems.NETHERITE_BACKPACK.get()),
                                'F', net.minecraft.world.item.crafting.Ingredient.of(AllItems.PRECISION_MAGNET.get())
                        ),
                        java.util.List.of("NF"));
        MagneticBackpackRecipe recipe = new MagneticBackpackRecipe(
                "",
                net.minecraft.world.item.crafting.CraftingBookCategory.MISC,
                pattern,
                new ItemStack(AllItems.MAGNETIC_BACKPACK.get()),
                true);

        ItemStack netheritePack = new ItemStack(AllItems.NETHERITE_BACKPACK.get());
        netheritePack.set(DataComponents.CUSTOM_NAME, Component.literal("Mining Rig"));
        BackpackColorHelper.setDyeColor(netheritePack, net.minecraft.world.item.DyeColor.CYAN);

        ItemStack precisionMagnet = new ItemStack(AllItems.PRECISION_MAGNET.get());

        // Construct two distinct direct enchantment holders for headless verification of highest-level merging
        net.minecraft.world.item.enchantment.Enchantment.EnchantmentDefinition def =
                net.minecraft.world.item.enchantment.Enchantment.definition(
                        net.minecraft.core.HolderSet.empty(), 1, 3,
                        net.minecraft.world.item.enchantment.Enchantment.constantCost(1),
                        net.minecraft.world.item.enchantment.Enchantment.constantCost(10),
                        1);
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchA =
                net.minecraft.core.Holder.direct(net.minecraft.world.item.enchantment.Enchantment.enchantment(def)
                        .build(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createessentials", "test_cap")));
        net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> enchB =
                net.minecraft.core.Holder.direct(net.minecraft.world.item.enchantment.Enchantment.enchantment(def)
                        .build(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createessentials", "test_other")));

        netheritePack.enchant(enchA, 1);
        netheritePack.enchant(enchB, 2);
        precisionMagnet.enchant(enchA, 3);

        net.minecraft.world.item.crafting.CraftingInput input =
                net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(netheritePack, precisionMagnet));
        ItemStack result = recipe.assemble(input, RegistryAccess.EMPTY);

        assertEquals("Mining Rig", result.get(DataComponents.CUSTOM_NAME).getString(),
                "MagneticBackpackRecipe must preserve Netherite Backpack CUSTOM_NAME");
        assertEquals(net.minecraft.world.item.DyeColor.CYAN, BackpackColorHelper.resolveDyeColor(result));
        net.minecraft.world.item.enchantment.ItemEnchantments resultEnchants =
                result.getOrDefault(DataComponents.ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
        assertEquals(3, resultEnchants.getLevel(enchA), "Merged enchantments must retain the highest level (3 > 1)");
        assertEquals(2, resultEnchants.getLevel(enchB), "Netherite Backpack enchantments must be preserved");
        assertEquals(0, result.getOrDefault(com.stackultra.essentials.registry.AllDataComponents.CHARGE, -1));
    }

    @Test
    void blockEntityClientUpdateTagsStayLightweightWhileMenuTagIncludesFullData() {
        RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

        MagneticBackpackBlockEntity magBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());
        magBe.setCharge(1234);
        magBe.getInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 16));
        magBe.getToolboxInventory().insertIntoCompartment(0, new ItemStack(Items.TORCH, 32), false);
        magBe.setFilters(java.util.List.of(new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY, ItemStack.EMPTY));

        CompoundTag clientUpdate = magBe.getUpdateTag(registries);
        assertEquals(1234, clientUpdate.getInt("Charge"));
        assertFalse(clientUpdate.contains("Inventory"), "Routine client packet must omit full 27-slot Inventory");
        assertFalse(clientUpdate.contains("Toolbox"), "Routine client packet must omit 32-slot Toolbox");
        assertFalse(clientUpdate.contains("Filters"), "Routine client packet must omit Filters");

        CompoundTag menuTag = magBe.buildMenuTag(registries);
        assertTrue(menuTag.contains("Inventory"), "Menu open tag must include Inventory");
        assertTrue(menuTag.contains("Toolbox"), "Menu open tag must include Toolbox");
        assertTrue(menuTag.contains("Filters"), "Menu open tag must include Filters");

        MagneticBackpackBlockEntity clientBe = new MagneticBackpackBlockEntity(
                BlockPos.ZERO, AllBlocks.MAGNETIC_BACKPACK.get().defaultBlockState());
        clientBe.readClient(menuTag, registries);
        assertEquals(16, clientBe.getInventory().getStackInSlot(0).getCount());
        assertEquals(32, clientBe.getToolboxInventory().getTotalCount(0));
        assertEquals(Items.IRON_INGOT, clientBe.getFilterInventory().getStackInSlot(0).getItem());

        RotationalMagnetBlockEntity magnetBe = new RotationalMagnetBlockEntity(
                BlockPos.ZERO, AllBlocks.PRECISION_MAGNET.get().defaultBlockState());
        magnetBe.setCharge(999);
        magnetBe.setFilters(java.util.List.of(new ItemStack(Items.COAL), ItemStack.EMPTY, ItemStack.EMPTY));

        CompoundTag magnetUpdate = magnetBe.getUpdateTag(registries);
        assertEquals(999, magnetUpdate.getInt("Charge"));
        assertFalse(magnetUpdate.contains("Filters"), "Routine magnet client packet must omit Filters");
        CompoundTag magnetMenuTag = magnetBe.buildMenuTag(registries);
        assertTrue(magnetMenuTag.contains("Filters"), "Magnet menu open tag must include Filters");
    }

    @Test
    void displaySourceGuiOverridesAreServerSafeAndDisabledEnchantedMagnetKeepsFoil() throws Exception {
        // Reflecting over every declared method on common menus & display sources must never throw NoClassDefFoundError
        assertDoesNotThrow(() -> {
            BackpackMenu.class.getDeclaredMethods();
            MagneticBackpackMenu.class.getDeclaredMethods();
            com.stackultra.essentials.content.magnet.RotationalMagnetMenu.class.getDeclaredMethods();
            com.stackultra.essentials.compat.create.BackpackDisplaySources.FillLevel.class.getDeclaredMethods();
            com.stackultra.essentials.compat.create.BackpackDisplaySources.ChargeLevel.class.getDeclaredMethods();
        });

        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.DEDICATED_SERVER) {
            // On Dist.DEDICATED_SERVER, FML DistCleaner strips @OnlyIn(Dist.CLIENT) initConfigurationWidgets overrides
            assertThrows(NoSuchMethodException.class, () ->
                    com.stackultra.essentials.compat.create.BackpackDisplaySources.FillLevel.class.getDeclaredMethod(
                            "initConfigurationWidgets",
                            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext.class,
                            com.simibubi.create.foundation.gui.ModularGuiLineBuilder.class,
                            boolean.class));
            assertThrows(NoSuchMethodException.class, () ->
                    com.stackultra.essentials.compat.create.BackpackDisplaySources.ChargeLevel.class.getDeclaredMethod(
                            "initConfigurationWidgets",
                            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext.class,
                            com.simibubi.create.foundation.gui.ModularGuiLineBuilder.class,
                            boolean.class));
        } else {
            var fillMethod = com.stackultra.essentials.compat.create.BackpackDisplaySources.FillLevel.class
                    .getDeclaredMethod("initConfigurationWidgets",
                            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext.class,
                            com.simibubi.create.foundation.gui.ModularGuiLineBuilder.class,
                            boolean.class);
            var chargeMethod = com.stackultra.essentials.compat.create.BackpackDisplaySources.ChargeLevel.class
                    .getDeclaredMethod("initConfigurationWidgets",
                            com.simibubi.create.content.redstone.displayLink.DisplayLinkContext.class,
                            com.simibubi.create.foundation.gui.ModularGuiLineBuilder.class,
                            boolean.class);
            net.neoforged.api.distmarker.OnlyIn fillAnn = fillMethod.getAnnotation(net.neoforged.api.distmarker.OnlyIn.class);
            net.neoforged.api.distmarker.OnlyIn chargeAnn = chargeMethod.getAnnotation(net.neoforged.api.distmarker.OnlyIn.class);
            assertNotNull(fillAnn, "FillLevel.initConfigurationWidgets must be annotated @OnlyIn(Dist.CLIENT)");
            assertNotNull(chargeAnn, "ChargeLevel.initConfigurationWidgets must be annotated @OnlyIn(Dist.CLIENT)");
            assertEquals(net.neoforged.api.distmarker.Dist.CLIENT, fillAnn.value());
            assertEquals(net.neoforged.api.distmarker.Dist.CLIENT, chargeAnn.value());
        }

        // Disabled magnet with glint override / enchantment must retain foil
        ItemStack disabledPlain = new ItemStack(AllItems.PRECISION_MAGNET.get());
        disabledPlain.set(com.stackultra.essentials.registry.AllDataComponents.ENABLED, false);
        assertFalse(disabledPlain.hasFoil());

        ItemStack disabledGlint = disabledPlain.copy();
        disabledGlint.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        assertTrue(disabledGlint.hasFoil(), "Disabled enchanted/glint magnet must preserve super.isFoil(stack)");
    }
}
