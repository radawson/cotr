package org.clockworx.cotr;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.listener.CoinListener;

/**
 * Coin of the Realm Plugin
 * 
 * This plugin creates a custom currency item called "Coin of the Realm" that:
 * - Can be held in player inventories
 * - Uses a resource pack for custom gold coin appearance
 * - Appears as a custom entity when dropped in the world
 * - Can be identified uniquely for economy integration
 * 
 * The coin is based on a gold nugget (fallback appearance) with CustomModelData
 * to display a custom texture for players with the resource pack.
 */
public class CoinOfTheRealmPlugin extends JavaPlugin {
    
    /**
     * CustomModelData value used to identify the coin texture in the resource pack.
     * This value must match the CustomModelData in the item model JSON.
     */
    public static final int COIN_CUSTOM_MODEL_DATA = 1000;
    
    /**
     * NBT key used to identify coin items.
     * Items with this NBT key are considered coins.
     */
    public static final String COIN_NBT_KEY = "cotr:coin";
    
    private static CoinOfTheRealmPlugin instance;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new CoinListener(), this);
        
        // Start a task to handle proximity-based pickup for coin displays
        // This allows players to pick up coins by walking near them
        startCoinPickupTask();
        
        getLogger().info("Coin of the Realm plugin has been enabled!");
        getLogger().info("CustomModelData: " + COIN_CUSTOM_MODEL_DATA);
    }
    
    /**
     * Starts a repeating task that checks for players near coin displays
     * and automatically picks them up (similar to standard item pickup).
     */
    private void startCoinPickupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check all online players
                for (Player player : getServer().getOnlinePlayers()) {
                    // Check for nearby ItemDisplay entities
                    for (org.bukkit.entity.Entity entity : player.getNearbyEntities(1.5, 1.5, 1.5)) {
                        if (!(entity instanceof ItemDisplay)) {
                            continue;
                        }
                        
                        ItemDisplay display = (ItemDisplay) entity;
                        ItemStack coin = CoinEntityManager.getCoinFromDisplay(display);
                        
                        if (coin != null) {
                            // Remove the display
                            display.remove();
                            
                            // Give the coin to the player
                            CoinEntityManager.giveCoinToPlayer(player, coin);
                            
                            // Only pick up one coin per tick to avoid lag
                            break;
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 5L); // Run every 5 ticks (0.25 seconds)
    }
    
    @Override
    public void onDisable() {
        getLogger().info("Coin of the Realm plugin has been disabled!");
        instance = null;
    }
    
    /**
     * Get the plugin instance.
     * 
     * @return The plugin instance, or null if the plugin is not enabled
     */
    public static CoinOfTheRealmPlugin getInstance() {
        return instance;
    }
    
    /**
     * Creates a NamespacedKey for this plugin.
     * 
     * @param key The key string
     * @return A NamespacedKey for this plugin
     */
    public NamespacedKey getKey(String key) {
        return new NamespacedKey(this, key);
    }
}
