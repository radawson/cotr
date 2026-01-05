package org.clockworx.cotr.entity;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.clockworx.cotr.item.CoinItem;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * CoinEntityManager - Manages the conversion between coin ItemStacks and display entities
 * 
 * This class handles the visual representation of coins when they are dropped in the world.
 * Instead of using the standard Item entity, coins are displayed as ItemDisplay entities
 * which can show custom models and textures from the resource pack.
 * 
 * When a coin is dropped:
 * - The standard Item entity is replaced with an ItemDisplay entity
 * - The ItemDisplay shows the coin ItemStack with its CustomModelData
 * - The display is configured with appropriate scale and rotation
 * 
 * When a coin is picked up:
 * - The ItemDisplay entity is converted back to an ItemStack
 * - The ItemStack is given to the player
 */
public class CoinEntityManager {
    
    /**
     * Scale factor for the coin display entity.
     * A value of 0.5 means the coin will appear at 50% of normal size.
     */
    private static final float COIN_SCALE = 0.5f;
    
    /**
     * Creates an ItemDisplay entity to represent a dropped coin.
     * 
     * @param location The location where the coin should be displayed
     * @param coin The coin ItemStack to display
     * @return The created ItemDisplay entity, or null if creation failed
     */
    public static ItemDisplay createCoinDisplay(Location location, ItemStack coin) {
        if (location == null || coin == null || !CoinItem.isCoin(coin)) {
            return null;
        }
        
        // Spawn an ItemDisplay entity at the location
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class);
        
        // Set the item to display (this will use the CustomModelData)
        display.setItemStack(coin);
        
        // Configure the display transformation (scale, rotation, translation)
        Transformation transformation = new Transformation(
            new Vector3f(0, 0, 0), // Translation (offset)
            new AxisAngle4f(0, 0, 0, 0), // Left rotation (none)
            new Vector3f(COIN_SCALE, COIN_SCALE, COIN_SCALE), // Scale (make it smaller)
            new AxisAngle4f(0, 0, 0, 0) // Right rotation (none)
        );
        display.setTransformation(transformation);
        
        // Set display properties
        display.setDisplayWidth(0.5f);
        display.setDisplayHeight(0.5f);
        
        // Make the display billboard (always face the player)
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        
        // Mark this display as a coin display for identification
        // The ItemStack is already stored in the display via setItemStack()
        display.getPersistentDataContainer().set(
            org.clockworx.cotr.CoinOfTheRealmPlugin.getInstance().getKey("is_coin_display"),
            org.bukkit.persistence.PersistentDataType.BOOLEAN,
            true
        );
        
        return display;
    }
    
    /**
     * Converts an ItemDisplay entity back to an ItemStack.
     * 
     * @param display The ItemDisplay entity representing a coin
     * @return The coin ItemStack, or null if the display is not a coin
     */
    public static ItemStack getCoinFromDisplay(ItemDisplay display) {
        if (display == null) {
            return null;
        }
        
        // Check if this is a coin display
        boolean isCoinDisplay = display.getPersistentDataContainer().has(
            org.clockworx.cotr.CoinOfTheRealmPlugin.getInstance().getKey("is_coin_display"),
            org.bukkit.persistence.PersistentDataType.BOOLEAN
        );
        
        if (!isCoinDisplay) {
            return null;
        }
        
        // Fall back to the item stack being displayed
        ItemStack item = display.getItemStack();
        if (item != null && CoinItem.isCoin(item)) {
            return item;
        }
        
        return null;
    }
    
    /**
     * Gives a coin ItemStack to a player, handling inventory overflow.
     * 
     * @param player The player to give the coin to
     * @param coin The coin ItemStack to give
     * @return true if the coin was successfully given, false otherwise
     */
    public static boolean giveCoinToPlayer(Player player, ItemStack coin) {
        if (player == null || coin == null || !CoinItem.isCoin(coin)) {
            return false;
        }
        
        // Try to add to inventory first
        java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(coin);
        
        // If there's overflow, drop it at the player's location
        if (!overflow.isEmpty()) {
            for (ItemStack item : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        
        return true;
    }
}
