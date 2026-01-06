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
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Coin amount must be between 1 and 64");
        }

        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin == null) {
            throw new IllegalStateException("Plugin instance not available");
        }

        CoinConfig coinConfig = plugin.getConfigManager().getCoinConfig();
        Material material = coinConfig.getMaterial();

        if (material == null) {
            // Fallback to gold nugget if material is not available (custom items)
            material = Material.GOLD_NUGGET;
        }

        // Create ItemStack based on configured material
        ItemStack coin = new ItemStack(material, amount);
        ItemMeta meta = coin.getItemMeta();

        if (meta == null) {
            throw new IllegalStateException("Failed to get ItemMeta for coin material: " + material);
        }

        // Set display name from config
        meta.displayName(Component.text(coinConfig.getDisplayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        // Set lore from config
        List<Component> lore = new ArrayList<>();
        for (String loreLine : coinConfig.getLore()) {
            lore.add(Component.text(loreLine, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);

        // Set item model for resource pack texture (newer Paper API)
        NamespacedKey modelKey = coinConfig.getItemModelKey();
        if (modelKey != null) {
            meta.setItemModel(modelKey);
        } else {
            // Fallback to CustomModelData if model not specified
            meta.setCustomModelData(coinConfig.getCustomModelData());
        }

        // Add NBT data to identify this as a coin
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(
                plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY),
                PersistentDataType.BOOLEAN,
                true);

        // Make the item unbreakable and prevent it from being destroyed
        meta.setUnbreakable(true);

        coin.setItemMeta(meta);
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
        if (item == null) {
            return false;
        }

        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin == null) {
            return false;
        }

        CoinConfig coinConfig = plugin.getConfigManager().getCoinConfig();

        // Check if material matches configured coin material
        Material itemMaterial = item.getType();
        Material coinMaterial = coinConfig.getMaterial();

        if (coinMaterial != null && itemMaterial != coinMaterial) {
            return false;
        }

        // For custom items (no Material), we rely solely on NBT
        // Check for the coin NBT identifier
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(
                plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY),
                PersistentDataType.BOOLEAN);
    }

    /**
     * Gets the amount of coins in an ItemStack.
     * Only works if the ItemStack is actually a coin.
     * 
     * @param item The coin ItemStack
     * @return The amount of coins, or 0 if not a coin
     */
    public static int getCoinAmount(@NotNull ItemStack item) {
        if (!isCoin(item)) {
            return 0;
        }
        return item.getAmount();
    }
}
