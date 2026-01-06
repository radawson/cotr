package org.clockworx.cotr.config;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * CoinConfig - Data class holding configuration for the coin item
 * 
 * This class stores the configuration loaded from config.yml, including:
 * - The material/item to use as the coin (via namespaced key)
 * - Display name and lore for the coin
 * - CustomModelData for resource pack integration
 * 
 * The class provides methods to convert the namespaced key to a Material
 * or ItemStack, handling both vanilla items and custom resource pack items.
 */
public class CoinConfig {

    private final String itemKey;
    private final String fallbackItemKey;
    private final String displayName;
    private final String description;
    private final List<String> lore;
    private final int customModelData;
    private final String modelNamespace;
    private final String modelKey;
    private final int maxStackSize;
    private final String rarity;
    private final boolean enchantmentGlint;
    private final int useDuration;
    private final String useAnimation;
    private final boolean attributeModifiersEnabled;
    private final List<AttributeModifier> attributeModifiers;
    private Material material;
    private Material fallbackMaterial;
    private NamespacedKey namespacedKey;
    
    /**
     * Inner class for attribute modifier configuration.
     */
    public static class AttributeModifier {
        private final String attribute;
        private final double amount;
        private final String operation;
        private final String slot;
        
        public AttributeModifier(String attribute, double amount, String operation, String slot) {
            this.attribute = attribute;
            this.amount = amount;
            this.operation = operation;
            this.slot = slot;
        }
        
        public String getAttribute() { return attribute; }
        public double getAmount() { return amount; }
        public String getOperation() { return operation; }
        public String getSlot() { return slot; }
    }

    /**
     * Creates a new CoinConfig with the specified values.
     * 
     * @param itemKey         The namespaced key string (e.g.,
     *                        "minecraft:gold_nugget" or "custom:my_coin")
     * @param fallbackItemKey The fallback item key if custom item fails
     * @param displayName     The display name for the coin
     * @param description     Additional description text for the coin
     * @param lore            The lore lines for the coin
     * @param customModelData The CustomModelData value for resource pack texture
     *                        (deprecated, use model instead)
     * @param modelNamespace  The namespace for the item model (e.g., "cotr")
     * @param modelKey        The key for the item model (e.g., "coin")
     * @param maxStackSize    The maximum stack size (1-64)
     * @param rarity          The item rarity (common, uncommon, rare, epic)
     * @param enchantmentGlint Whether to show enchantment glint
     * @param useDuration     Duration in ticks to use the coin (0 = instant)
     * @param useAnimation    Animation when using (eat, drink, block, etc.)
     * @param attributeModifiersEnabled Whether attribute modifiers are enabled
     * @param attributeModifiers List of attribute modifiers
     */
    public CoinConfig(@NotNull String itemKey, @NotNull String fallbackItemKey,
            @NotNull String displayName, @Nullable String description, @NotNull List<String> lore, 
            int customModelData, @Nullable String modelNamespace, 
            @Nullable String modelKey, int maxStackSize, @NotNull String rarity,
            boolean enchantmentGlint, int useDuration, @NotNull String useAnimation,
            boolean attributeModifiersEnabled, @NotNull List<AttributeModifier> attributeModifiers) {
        this.itemKey = itemKey;
        this.fallbackItemKey = fallbackItemKey;
        this.displayName = displayName;
        this.description = description;
        this.lore = lore;
        this.customModelData = customModelData;
        this.modelNamespace = modelNamespace;
        this.modelKey = modelKey;
        this.maxStackSize = maxStackSize;
        this.rarity = rarity;
        this.enchantmentGlint = enchantmentGlint;
        this.useDuration = useDuration;
        this.useAnimation = useAnimation;
        this.attributeModifiersEnabled = attributeModifiersEnabled;
        this.attributeModifiers = attributeModifiers;
        parseItemKey();
        parseFallbackItemKey();
    }

    /**
     * Parses the item key string into a Material (if vanilla) or NamespacedKey (if
     * custom).
     * This method attempts to match the key to a Material enum first, then falls
     * back
     * to parsing it as a NamespacedKey for custom items.
     */
    private void parseItemKey() {
        // Try to parse as Material enum first (for vanilla items)
        try {
            // Handle both "minecraft:gold_nugget" and "GOLD_NUGGET" formats
            String materialName = itemKey;
            if (itemKey.contains(":")) {
                String[] parts = itemKey.split(":", 2);
                if ("minecraft".equals(parts[0])) {
                    materialName = parts[1].toUpperCase();
                } else {
                    // Custom namespace, parse as NamespacedKey
                    this.namespacedKey = NamespacedKey.fromString(itemKey);
                    return;
                }
            }

            this.material = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Not a Material enum, try parsing as NamespacedKey
            try {
                this.namespacedKey = NamespacedKey.fromString(itemKey);
            } catch (IllegalArgumentException ex) {
                // Invalid format, will use fallback
                this.material = Material.GOLD_NUGGET;
            }
        }
    }
    
    /**
     * Parses the fallback item key string into a Material.
     */
    private void parseFallbackItemKey() {
        try {
            String materialName = fallbackItemKey;
            if (fallbackItemKey.contains(":")) {
                String[] parts = fallbackItemKey.split(":", 2);
                if ("minecraft".equals(parts[0])) {
                    materialName = parts[1].toUpperCase();
                } else {
                    // Non-minecraft namespace, try as Material name anyway
                    materialName = parts[1].toUpperCase();
                }
            }
            this.fallbackMaterial = Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            // Invalid fallback, use gold nugget
            this.fallbackMaterial = Material.GOLD_NUGGET;
        }
    }

    /**
     * Gets the NamespacedKey for the item model, used with ItemMeta.setItemModel().
     * 
     * @return The item model NamespacedKey, or null if model is not configured
     */
    @Nullable
    public NamespacedKey getItemModelKey() {
        if (modelNamespace != null && modelKey != null) {
            return new NamespacedKey(modelNamespace, modelKey);
        }
        return null;
    }

    /**
     * Gets the Material for this coin, if it's a vanilla item.
     * 
     * @return The Material, or null if this is a custom item
     */
    @Nullable
    public Material getMaterial() {
        return material;
    }

    /**
     * Gets the NamespacedKey for this coin, if it's a custom item.
     * 
     * @return The NamespacedKey, or null if this is a vanilla item
     */
    @Nullable
    public NamespacedKey getNamespacedKey() {
        return namespacedKey;
    }

    /**
     * Gets the raw item key string.
     * 
     * @return The item key as specified in config
     */
    @NotNull
    public String getItemKey() {
        return itemKey;
    }

    /**
     * Gets the display name for the coin.
     * 
     * @return The display name
     */
    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Gets the lore lines for the coin.
     * 
     * @return The lore lines
     */
    @NotNull
    public List<String> getLore() {
        return lore;
    }

    /**
     * Gets the CustomModelData value for resource pack texture.
     * 
     * @return The CustomModelData value
     * @deprecated Use getModelNamespace() and getModelKey() instead
     */
    @Deprecated
    public int getCustomModelData() {
        return customModelData;
    }

    /**
     * Gets the namespace for the item model.
     * 
     * @return The model namespace, or null if not set
     */
    @Nullable
    public String getModelNamespace() {
        return modelNamespace;
    }

    /**
     * Gets the key for the item model.
     * 
     * @return The model key, or null if not set
     */
    @Nullable
    public String getModelKey() {
        return modelKey;
    }

    /**
     * Checks if this coin uses a vanilla Material.
     * 
     * @return true if using a Material, false if using a custom NamespacedKey
     */
    public boolean isVanillaItem() {
        return material != null;
    }
    
    /**
     * Gets the fallback item key.
     * 
     * @return The fallback item key
     */
    @NotNull
    public String getFallbackItemKey() {
        return fallbackItemKey;
    }
    
    /**
     * Gets the fallback Material.
     * 
     * @return The fallback Material, or null if not set
     */
    @Nullable
    public Material getFallbackMaterial() {
        return fallbackMaterial;
    }
    
    /**
     * Gets the maximum stack size.
     * 
     * @return The maximum stack size (1-64)
     */
    public int getMaxStackSize() {
        return maxStackSize;
    }
    
    /**
     * Gets the item rarity.
     * 
     * @return The rarity (common, uncommon, rare, epic)
     */
    @NotNull
    public String getRarity() {
        return rarity;
    }
    
    /**
     * Gets the description text.
     * 
     * @return The description, or null if not set
     */
    @Nullable
    public String getDescription() {
        return description;
    }
    
    /**
     * Gets whether enchantment glint is enabled.
     * 
     * @return true if glint is enabled
     */
    public boolean isEnchantmentGlint() {
        return enchantmentGlint;
    }
    
    /**
     * Gets the use duration in ticks.
     * 
     * @return The use duration (0 = instant)
     */
    public int getUseDuration() {
        return useDuration;
    }
    
    /**
     * Gets the use animation.
     * 
     * @return The use animation (eat, drink, block, etc.)
     */
    @NotNull
    public String getUseAnimation() {
        return useAnimation;
    }
    
    /**
     * Checks if attribute modifiers are enabled.
     * 
     * @return true if attribute modifiers are enabled
     */
    public boolean isAttributeModifiersEnabled() {
        return attributeModifiersEnabled;
    }
    
    /**
     * Gets the list of attribute modifiers.
     * 
     * @return The list of attribute modifiers
     */
    @NotNull
    public List<AttributeModifier> getAttributeModifiers() {
        return attributeModifiers;
    }

    /**
     * Creates an ItemStack from this configuration.
     * This method creates a basic ItemStack with the configured material.
     * Note: For custom items, this may not work perfectly and may require
     * additional handling in the ItemStack creation code.
     * 
     * @param amount The amount for the ItemStack
     * @return A new ItemStack, or null if the material/key is invalid
     */
    @Nullable
    public ItemStack createItemStack(int amount) {
        if (material != null) {
            return new ItemStack(material, amount);
        }
        // For custom items, we'd need additional handling
        // For now, return null and let the caller handle it
        return null;
    }
}
