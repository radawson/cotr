package org.clockworx.cotr.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
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
     * 
     * @param amount The number of coins in the stack (1-64)
     * @return A new ItemStack representing the specified number of coins
     * @throws IllegalArgumentException if amount is less than 1 or greater than 64
     */
    @NotNull
    public static ItemStack createCoin(int amount) {
        if (amount < 1 || amount > 64) {
            throw new IllegalArgumentException("Coin amount must be between 1 and 64");
        }
        
        // Create ItemStack based on gold nugget (fallback appearance)
        ItemStack coin = new ItemStack(Material.GOLD_NUGGET, amount);
        ItemMeta meta = coin.getItemMeta();
        
        if (meta == null) {
            throw new IllegalStateException("Failed to get ItemMeta for gold nugget");
        }
        
        // Set display name
        meta.displayName(Component.text("Coin of the Realm", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        
        // Set lore
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("A valuable currency", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("used throughout the realm.", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        
        // Set CustomModelData for resource pack texture
        meta.setCustomModelData(CoinOfTheRealmPlugin.COIN_CUSTOM_MODEL_DATA);
        
        // Add NBT data to identify this as a coin
        PersistentDataContainer container = meta.getPersistentDataContainer();
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin != null) {
            container.set(
                plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY),
                PersistentDataType.BOOLEAN,
                true
            );
        }
        
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
     * 
     * @param item The ItemStack to check
     * @return true if the ItemStack is a coin, false otherwise
     */
    public static boolean isCoin(@NotNull ItemStack item) {
        if (item == null || item.getType() != Material.GOLD_NUGGET) {
            return false;
        }
        
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        // Check for the coin NBT identifier
        PersistentDataContainer container = meta.getPersistentDataContainer();
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin == null) {
            return false;
        }
        return container.has(
            plugin.getKey(CoinOfTheRealmPlugin.COIN_NBT_KEY),
            PersistentDataType.BOOLEAN
        );
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
