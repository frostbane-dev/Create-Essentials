package com.stackultra.essentials.content;

import com.stackultra.essentials.registry.ExtraDataComponents;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import javax.annotation.Nullable;

/**
 * Preserves {@link DataComponents#CUSTOM_NAME}, {@link DataComponents#DYED_COLOR},
 * {@link ExtraDataComponents#BACKPACK_COLOR}, and all {@link DataComponents#ENCHANTMENTS}
 * across block place/save/load/break cycles.
 */
public final class BlockItemComponentHelper {

    private static final String TAG_CUSTOM_NAME = "CustomName";
    private static final String TAG_DYED_COLOR = "DyedColor";
    private static final String TAG_BACKPACK_COLOR = "BackpackColor";
    private static final String TAG_ENCHANTMENTS = "Enchantments";

    private BlockItemComponentHelper() {
    }

    public static final class PreservedComponents {
        @Nullable
        private Component customName;
        @Nullable
        private DyedItemColor dyedColor;
        @Nullable
        private DyeColor backpackColor;
        private ItemEnchantments enchantments = ItemEnchantments.EMPTY;

        @Nullable
        public Component getCustomName() {
            return customName;
        }

        @Nullable
        public DyedItemColor getDyedColor() {
            return dyedColor;
        }

        @Nullable
        public DyeColor getBackpackColor() {
            return backpackColor;
        }

        public void setBackpackColorIfAbsent(@Nullable DyeColor color) {
            if (this.backpackColor == null && color != null) {
                this.backpackColor = color;
            }
        }

        public ItemEnchantments getEnchantments() {
            return enchantments;
        }

        public void loadFromStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                clear();
                return;
            }
            this.customName = stack.get(DataComponents.CUSTOM_NAME);
            this.dyedColor = stack.get(DataComponents.DYED_COLOR);
            this.backpackColor = stack.get(ExtraDataComponents.BACKPACK_COLOR);
            this.enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        }

        public void saveToStack(ItemStack stack) {
            if (stack == null || stack.isEmpty())
                return;
            if (customName != null)
                stack.set(DataComponents.CUSTOM_NAME, customName);
            if (dyedColor != null)
                stack.set(DataComponents.DYED_COLOR, dyedColor);
            if (backpackColor != null)
                stack.set(ExtraDataComponents.BACKPACK_COLOR, backpackColor);
            if (enchantments != null && !enchantments.isEmpty())
                stack.set(DataComponents.ENCHANTMENTS, enchantments);
        }

        public void writeToTag(CompoundTag tag, HolderLookup.Provider registries) {
            if (tag == null || registries == null)
                return;
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            if (customName != null) {
                ComponentSerialization.CODEC.encodeStart(ops, customName)
                        .result()
                        .ifPresent(encoded -> tag.put(TAG_CUSTOM_NAME, encoded));
            }
            if (dyedColor != null) {
                DyedItemColor.CODEC.encodeStart(ops, dyedColor)
                        .result()
                        .ifPresent(encoded -> tag.put(TAG_DYED_COLOR, encoded));
            }
            if (backpackColor != null) {
                tag.putString(TAG_BACKPACK_COLOR, backpackColor.getSerializedName());
            }
            if (enchantments != null && !enchantments.isEmpty()) {
                ItemEnchantments.CODEC.encodeStart(ops, enchantments)
                        .result()
                        .ifPresent(encoded -> tag.put(TAG_ENCHANTMENTS, encoded));
            }
        }

        public void readFromTag(CompoundTag tag, HolderLookup.Provider registries) {
            clear();
            if (tag == null || registries == null)
                return;
            RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            if (tag.contains(TAG_CUSTOM_NAME)) {
                ComponentSerialization.CODEC.parse(ops, tag.get(TAG_CUSTOM_NAME))
                        .result()
                        .ifPresent(decoded -> this.customName = decoded);
            }
            if (tag.contains(TAG_DYED_COLOR)) {
                DyedItemColor.CODEC.parse(ops, tag.get(TAG_DYED_COLOR))
                        .result()
                        .ifPresent(decoded -> this.dyedColor = decoded);
            }
            if (tag.contains(TAG_BACKPACK_COLOR)) {
                this.backpackColor = DyeColor.byName(tag.getString(TAG_BACKPACK_COLOR), null);
            }
            if (tag.contains(TAG_ENCHANTMENTS)) {
                ItemEnchantments.CODEC.parse(ops, tag.get(TAG_ENCHANTMENTS))
                        .result()
                        .ifPresent(decoded -> this.enchantments = decoded);
            }
        }

        public void clear() {
            this.customName = null;
            this.dyedColor = null;
            this.backpackColor = null;
            this.enchantments = ItemEnchantments.EMPTY;
        }
    }
}
