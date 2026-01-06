package org.clockworx.cotr.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.item.CoinItem;

/**
 * CoinListener - Handles events related to coin items
 * 
 * This listener manages the lifecycle of coin items in the world:
 * 
 * 1. When a player drops a coin:
 *    - Detects if the dropped item is a coin
 *    - Cancels the default Item entity creation
 *    - Creates a custom ItemDisplay entity instead
 * 
 * 2. When a player picks up a coin:
 *    - Detects if the picked up entity is a coin display
 *    - Converts the ItemDisplay back to an ItemStack
 *    - Gives the coin to the player
 *    - Prevents duplication exploits
 */
public class CoinListener implements Listener {
    
    /**
     * Handles when a player drops an item.
     * If the item is a coin, replaces the standard Item entity with an ItemDisplay.
     * 
     * @param event The PlayerDropItemEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        Player player = event.getPlayer();
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        plugin.debug("CoinListener.onPlayerDropItem() - player={}, item={}, amount={}", 
            player.getName(), droppedItem.getType(), droppedItem.getAmount());
        
        // Check if the dropped item is a coin
        if (!CoinItem.isCoin(droppedItem)) {
            plugin.debug("CoinListener.onPlayerDropItem() - Item is not a coin, allowing normal drop");
            return; // Not a coin, let it drop normally
        }
        
        plugin.debug("CoinListener.onPlayerDropItem() - Coin detected, creating custom ItemDisplay entity");
        
        // Cancel the default drop (we'll create our own entity)
        event.setCancelled(true);
        
        // Get the drop location
        org.bukkit.Location dropLocation = player.getLocation().add(
            player.getLocation().getDirection().multiply(0.5)
        );
        dropLocation.setY(dropLocation.getY() + player.getEyeHeight() - 0.3);
        plugin.debug("CoinListener.onPlayerDropItem() - Drop location: {}", dropLocation);
        
        // Create a custom ItemDisplay entity for the coin
        org.bukkit.entity.ItemDisplay coinDisplay = CoinEntityManager.createCoinDisplay(
            dropLocation,
            droppedItem
        );
        
        if (coinDisplay != null) {
            plugin.debug("CoinListener.onPlayerDropItem() - ItemDisplay created successfully, adding velocity");
            // Add a small velocity to make it look natural
            coinDisplay.setVelocity(player.getLocation().getDirection().multiply(0.3));
        } else {
            plugin.debug("CoinListener.onPlayerDropItem() - ItemDisplay creation failed, falling back to normal drop");
            // Fallback: if display creation fails, drop normally
            player.getWorld().dropItemNaturally(dropLocation, droppedItem);
        }
    }
    
    /**
     * Handles when a player picks up an item.
     * If the item is a coin display entity, converts it back to an ItemStack.
     * 
     * @param event The EntityPickupItemEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        
        // Only handle player pickups
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Player player = (Player) event.getEntity();
        Item itemEntity = event.getItem();
        ItemStack item = itemEntity.getItemStack();
        
        plugin.debug("CoinListener.onEntityPickupItem() - player={}, item={}, amount={}", 
            player.getName(), item.getType(), item.getAmount());
        
        // Check if it's a coin
        if (CoinItem.isCoin(item)) {
            plugin.debug("CoinListener.onEntityPickupItem() - Coin detected, allowing normal pickup");
            // For standard Item entities with coins, let them pick up normally
            // The custom display entities will be handled separately
            return;
        }
        
        plugin.debug("CoinListener.onEntityPickupItem() - Item is not a coin, allowing normal pickup");
    }
    
    /**
     * Handles when a player interacts with an entity (right-click).
     * If the entity is a coin ItemDisplay, converts it to an ItemStack and gives it to the player.
     * 
     * @param event The PlayerInteractEntityEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        
        if (!(event.getRightClicked() instanceof ItemDisplay)) {
            return;
        }
        
        ItemDisplay display = (ItemDisplay) event.getRightClicked();
        Player player = event.getPlayer();
        
        plugin.debug("CoinListener.onPlayerInteractEntity() - player={}, entityType=ItemDisplay, location={}", 
            player.getName(), display.getLocation());
        
        // Check if this is a coin display
        ItemStack coin = CoinEntityManager.getCoinFromDisplay(display);
        if (coin == null) {
            plugin.debug("CoinListener.onPlayerInteractEntity() - ItemDisplay is not a coin display");
            return; // Not a coin display
        }
        
        plugin.debug("CoinListener.onPlayerInteractEntity() - Coin display detected, amount={}, removing entity and giving to player", 
            coin.getAmount());
        
        // Remove the display entity
        display.remove();
        
        // Give the coin to the player
        CoinEntityManager.giveCoinToPlayer(player, coin);
        
        // Cancel the event to prevent other handlers
        event.setCancelled(true);
    }
    
    /**
     * Handles when a player joins the server.
     * Automatically applies the resource pack if configured.
     * 
     * @param event The PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        
        plugin.debug("CoinListener.onPlayerJoin() - player={}", player.getName());
        
        if (plugin == null) {
            return;
        }
        
        String resourcePackUrl = plugin.getConfigManager().getResourcePackUrl();
        plugin.debug("CoinListener.onPlayerJoin() - Resource pack URL: {}", resourcePackUrl != null && !resourcePackUrl.isEmpty() ? "configured" : "not configured");
        
        // Only apply resource pack if URL is configured
        if (resourcePackUrl == null || resourcePackUrl.isEmpty()) {
            plugin.debug("CoinListener.onPlayerJoin() - Resource pack URL not configured, skipping");
            return;
        }
        
        // Get resource pack hash if configured
        String resourcePackHash = plugin.getConfigManager().getResourcePackHash();
        boolean prompt = plugin.getConfigManager().isResourcePackPrompt();
        plugin.debug("CoinListener.onPlayerJoin() - Resource pack hash: {}, prompt: {}", 
            resourcePackHash != null && !resourcePackHash.isEmpty() ? "configured" : "not configured", prompt);
        
        // Apply resource pack with a slight delay to ensure player is fully connected
        plugin.debug("CoinListener.onPlayerJoin() - Scheduling resource pack application in 20 ticks (1 second)");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            try {
                plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack to player {}", player.getName());
                
                if (resourcePackHash != null && !resourcePackHash.isEmpty()) {
                    // Apply with hash verification (convert hex string to byte array)
                    byte[] hashBytes = hexStringToByteArray(resourcePackHash);
                    if (hashBytes != null && hashBytes.length == 20) { // SHA-1 is 20 bytes
                        plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack with hash verification");
                        player.setResourcePack(resourcePackUrl, hashBytes);
                    } else {
                        // Invalid hash, apply without verification
                        plugin.getLogger().warning("Invalid resource pack hash format, applying without verification");
                        plugin.debug("CoinListener.onPlayerJoin() - Hash conversion failed, applying without verification");
                        player.setResourcePack(resourcePackUrl);
                    }
                } else {
                    // Apply without hash verification
                    plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack without hash verification");
                    player.setResourcePack(resourcePackUrl);
                }
                
                plugin.debug("CoinListener.onPlayerJoin() - Resource pack application initiated successfully");
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to apply resource pack to " + player.getName(), e);
                plugin.debug("CoinListener.onPlayerJoin() - Exception applying resource pack: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage());
            }
        }, 20L); // 1 second delay
    }
    
    /**
     * Converts a hexadecimal string to a byte array.
     * Used for converting SHA-1 hash strings to byte arrays for resource pack verification.
     * 
     * @param hexString The hexadecimal string (e.g., "a1b2c3...")
     * @return The byte array, or null if the string is invalid
     */
    private byte[] hexStringToByteArray(String hexString) {
        if (hexString == null || hexString.length() % 2 != 0) {
            return null;
        }
        
        try {
            int len = hexString.length();
            byte[] data = new byte[len / 2];
            for (int i = 0; i < len; i += 2) {
                data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                        + Character.digit(hexString.charAt(i + 1), 16));
            }
            return data;
        } catch (Exception e) {
            return null;
        }
    }
}
