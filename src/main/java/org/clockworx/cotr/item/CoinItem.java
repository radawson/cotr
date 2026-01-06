package org.clockworx.cotr.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.config.CoinConfig;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * CoinItem - Represents a "Coin of the Realm" currency item
 * 
 * This class provides factory methods to create custom coin ItemStacks.
 * The coin is based on a gold nugget material, which provides an appropriate
 * fallback appearance for players who don't have the resource pack installed.
 * 
 * Features:
 * - Custom NBT data for identification (cotr:coin)
 * - CustomModelData for resource pack texture linking
 * - Custom display name and lore
 * - Persistent across server restarts
 */
public class CoinItem {

    /**
     * Creates a coin ItemStack with the specified amount.
     * Uses the configured coin material from ConfigManager.
     * 
     * @param amount The number of coins in the stack (1-64)
     * @return A new ItemStack representing the specified number of coins
     * @throws IllegalArgumentException if amount is less than 1 or greater than 64
     * @throws IllegalStateException    if the plugin or config is not available
     */
    @NotNull
    public static ItemStack createCoin(int amount) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CoinItem.createCoin() - Creating coin with amount: {}", amount);
        
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Coin amount must be between 1 and 64");
        }

        if (plugin == null) {
            throw new IllegalStateException("Plugin instance not available");
        }

        CoinConfig coinConfig = plugin.getConfigManager().getCoinConfig();
        Material material = coinConfig.getMaterial();
        plugin.debug("CoinItem.createCoin() - Coin material from config: {}", material);

        if (material == null) {
            // Fallback to gold nugget if material is not available (custom items)
            material = Material.GOLD_NUGGET;
            plugin.debug("CoinItem.createCoin() - Material was null, using fallback: {}", material);
        }

        // Create ItemStack based on configured material
        ItemStack coin = new ItemStack(material, amount);
        ItemMeta meta = coin.getItemMeta();

        if (meta == null) {
            throw new IllegalStateException("Failed to get ItemMeta for coin material: " + material);
        }

        // Set display name from config
        String displayName = coinConfig.getDisplayName();
        plugin.debug("CoinItem.createCoin() - Setting display name: {}", displayName);
        meta.displayName(Component.text(displayName, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Set lore from config
        List<String> loreLines = coinConfig.getLore();
        plugin.debug("CoinItem.createCoin() - Setting lore with {} lines", loreLines.size());
        List<Component> lore = new ArrayList<>();
        for (String loreLine : loreLines) {
            lore.add(Component.text(loreLine, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // Set item model for resource pack texture (newer Paper API)
        NamespacedKey modelKey = coinConfig.getItemModelKey();
        if (modelKey != null) {
            plugin.debug("CoinItem.createCoin() - Setting item model: {}", modelKey);
            meta.setItemModel(modelKey);
        } else {
            // Fallback to CustomModelData if model not specified
            int customModelData = coinConfig.getCustomModelData();
            plugin.debug("CoinItem.createCoin() - Setting CustomModelData: {}", customModelData);
            meta.setCustomModelData(customModelData);
        }

        // Add NBT data to identify this as a coin
        NamespacedKey coinKey = plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY);
        plugin.debug("CoinItem.createCoin() - Setting NBT key: {}", coinKey);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(coinKey, PersistentDataType.BOOLEAN, true);

        // Make the item unbreakable and prevent it from being destroyed
        meta.setUnbreakable(true);

        coin.setItemMeta(meta);
        plugin.debug("CoinItem.createCoin() - Coin created successfully: material={}, amount={}", material, amount);
        return coin;
    }

    /**
     * Creates a single coin ItemStack.
     * 
     * @return A new ItemStack containing one coin
     */
    @NotNull
    public static ItemStack createCoin() {
        return createCoin(1);
    }

    /**
     * Checks if an ItemStack is a coin.
     * Validates both the material matches the configured coin material
     * and that it has the coin NBT identifier.
     * 
     * @param item The ItemStack to check
     * @return true if the ItemStack is a coin, false otherwise
     */
    public static boolean isCoin(@NotNull ItemStack item) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        
        if (item == null) {
            plugin.debug("CoinItem.isCoin() - ItemStack is null, returning false");
            return false;
        }

        if (plugin == null) {
            return false;
        }

        CoinConfig coinConfig = plugin.getConfigManager().getCoinConfig();

        // Check if material matches configured coin material
        Material itemMaterial = item.getType();
        Material coinMaterial = coinConfig.getMaterial();
        plugin.debug("CoinItem.isCoin() - Checking item: material={}, configuredMaterial={}", itemMaterial, coinMaterial);

        if (coinMaterial != null && itemMaterial != coinMaterial) {
            plugin.debug("CoinItem.isCoin() - Material mismatch, returning false");
            return false;
        }

        // For custom items (no Material), we rely solely on NBT
        // Check for the coin NBT identifier
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            plugin.debug("CoinItem.isCoin() - ItemMeta is null, returning false");
            return false;
        }

        NamespacedKey coinKey = plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY);
        PersistentDataContainer container = meta.getPersistentDataContainer();
        boolean hasCoinNBT = container.has(coinKey, PersistentDataType.BOOLEAN);
        plugin.debug("CoinItem.isCoin() - NBT check: key={}, hasCoinNBT={}", coinKey, hasCoinNBT);
        
        return hasCoinNBT;
    }

    /**
     * Gets the amount of coins in an ItemStack.
     * Only works if the ItemStack is actually a coin.
     * 
     * @param item The coin ItemStack
     * @return The amount of coins, or 0 if not a coin
     */
    public static int getCoinAmount(@NotNull ItemStack item) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CoinItem.getCoinAmount() - Getting coin amount from ItemStack");
        
        if (!isCoin(item)) {
            plugin.debug("CoinItem.getCoinAmount() - Item is not a coin, returning 0");
            return 0;
        }
        
        int amount = item.getAmount();
        plugin.debug("CoinItem.getCoinAmount() - Coin amount: {}", amount);
        return amount;
    }
}
