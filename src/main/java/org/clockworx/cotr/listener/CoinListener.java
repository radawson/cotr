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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.item.CoinItem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
    
    // Track retry attempts per player
    private final Map<UUID, Integer> retryCounts = new HashMap<>();
    private final Map<UUID, Long> lastRetryTimes = new HashMap<>();
    // Track players who have successfully loaded the resource pack
    private final Map<UUID, Boolean> packLoaded = new HashMap<>();
    
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
        
        // Reset retry tracking for this player
        retryCounts.remove(player.getUniqueId());
        lastRetryTimes.remove(player.getUniqueId());
        packLoaded.remove(player.getUniqueId());
        
        String resourcePackUrl = plugin.getConfigManager().getResourcePackUrl();
        plugin.debug("CoinListener.onPlayerJoin() - Resource pack URL: {}", resourcePackUrl != null && !resourcePackUrl.isEmpty() ? "configured" : "not configured");
        
        // Only apply resource pack if URL is configured
        if (resourcePackUrl == null || resourcePackUrl.isEmpty()) {
            plugin.debug("CoinListener.onPlayerJoin() - Resource pack URL not configured, skipping");
            return;
        }
        
        // Log resource pack URL (truncated if very long)
        String urlDisplay = resourcePackUrl.length() > 80 ? resourcePackUrl.substring(0, 77) + "..." : resourcePackUrl;
        plugin.getLogger().info("Applying resource pack to " + player.getName() + ": " + urlDisplay);
        plugin.debug("CoinListener.onPlayerJoin() - Full resource pack URL: {}", resourcePackUrl);
        
        // Get resource pack hash if configured
        String resourcePackHash = plugin.getConfigManager().getResourcePackHash();
        boolean prompt = plugin.getConfigManager().isResourcePackPrompt();
        boolean hasHash = resourcePackHash != null && !resourcePackHash.isEmpty();
        
        plugin.debug("CoinListener.onPlayerJoin() - Resource pack hash: {}, prompt: {}", 
            hasHash ? "configured" : "not configured", prompt);
        
        if (hasHash) {
            plugin.getLogger().info("Resource pack hash verification enabled for " + player.getName());
        } else {
            plugin.getLogger().warning("Resource pack hash not configured - verification disabled for " + player.getName());
        }
        
        // Check if player is online before applying
        if (!player.isOnline()) {
            plugin.getLogger().warning("Player " + player.getName() + " is not online, skipping resource pack application");
            plugin.debug("CoinListener.onPlayerJoin() - Player offline check failed");
            return;
        }
        
        // Apply resource pack with a slight delay to ensure player is fully connected
        plugin.debug("CoinListener.onPlayerJoin() - Scheduling resource pack application in 20 ticks (1 second)");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Double-check player is still online
            if (!player.isOnline()) {
                plugin.debug("CoinListener.onPlayerJoin() - Player {} went offline before resource pack could be applied", player.getName());
                return;
            }
            
            try {
                plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack to player {}", player.getName());
                
                if (hasHash) {
                    // Apply with hash verification (convert hex string to byte array)
                    byte[] hashBytes = hexStringToByteArray(resourcePackHash);
                    if (hashBytes != null && hashBytes.length == 20) { // SHA-1 is 20 bytes
                        plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack with hash verification");
                        player.setResourcePack(resourcePackUrl, hashBytes);
                    } else {
                        // Invalid hash, apply without verification
                        plugin.getLogger().warning("Invalid resource pack hash format for " + player.getName() + ", applying without verification");
                        plugin.debug("CoinListener.onPlayerJoin() - Hash conversion failed, applying without verification");
                        player.setResourcePack(resourcePackUrl);
                    }
                } else {
                    // Apply without hash verification
                    plugin.debug("CoinListener.onPlayerJoin() - Applying resource pack without hash verification");
                    player.setResourcePack(resourcePackUrl);
                }
                
                plugin.debug("CoinListener.onPlayerJoin() - Resource pack application initiated successfully for {}", player.getName());
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to apply resource pack to " + player.getName(), e);
                plugin.debug("CoinListener.onPlayerJoin() - Exception applying resource pack: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage());
            }
        }, 20L); // 1 second delay
    }
    
    /**
     * Handles when a player quits the server.
     * Cleans up retry tracking data.
     * 
     * @param event The PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        retryCounts.remove(playerId);
        lastRetryTimes.remove(playerId);
        packLoaded.remove(playerId);
    }
    
    /**
     * Handles resource pack status events from players.
     * Tracks acceptance, decline, and failure statuses, and triggers retries as needed.
     * 
     * @param event The PlayerResourcePackStatusEvent
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        UUID playerId = player.getUniqueId();
        
        if (plugin == null) {
            return;
        }
        
        PlayerResourcePackStatusEvent.Status status = event.getStatus();
        String statusName = status.name();
        
        plugin.debug("CoinListener.onResourcePackStatus() - player={}, status={}", player.getName(), statusName);
        
        switch (status) {
            case ACCEPTED:
                plugin.getLogger().info("Player " + player.getName() + " accepted the resource pack");
                // Don't clear retry tracking yet - wait for SUCCESSFULLY_LOADED
                break;
                
            case DECLINED:
                plugin.getLogger().warning("Player " + player.getName() + " declined the resource pack");
                
                // Check if we should kick the player
                if (plugin.getConfigManager().isResourcePackKickOnDecline() && 
                    !plugin.getConfigManager().isResourcePackPrompt()) {
                    plugin.getLogger().info("Kicking " + player.getName() + " for declining mandatory resource pack");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.kickPlayer("You must accept the resource pack to play on this server.");
                    });
                    return; // Don't retry if we're kicking
                }
                
                // Retry if enabled
                if (plugin.getConfigManager().getResourcePackMaxRetries() > 0) {
                    retryResourcePack(player);
                }
                break;
                
            case FAILED_DOWNLOAD:
                plugin.getLogger().warning("Player " + player.getName() + " failed to download the resource pack");
                
                // Retry if enabled
                if (plugin.getConfigManager().getResourcePackMaxRetries() > 0) {
                    retryResourcePack(player);
                }
                break;
                
            case SUCCESSFULLY_LOADED:
                plugin.getLogger().info("Player " + player.getName() + " successfully loaded the resource pack");
                // Clear retry tracking on successful load
                retryCounts.remove(playerId);
                lastRetryTimes.remove(playerId);
                packLoaded.put(playerId, true);
                break;
                
            default:
                plugin.debug("CoinListener.onResourcePackStatus() - Unhandled status: {}", statusName);
                break;
        }
    }
    
    /**
     * Retries applying the resource pack to a player.
     * Uses exponential backoff and respects max retry limits.
     * 
     * @param player The player to retry the resource pack for
     */
    private void retryResourcePack(Player player) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        UUID playerId = player.getUniqueId();
        
        if (plugin == null) {
            return;
        }
        
        // Check if player is still online
        if (!player.isOnline()) {
            plugin.debug("CoinListener.retryResourcePack() - Player {} is offline, skipping retry", player.getName());
            retryCounts.remove(playerId);
            lastRetryTimes.remove(playerId);
            return;
        }
        
        // Get current retry count
        int currentRetries = retryCounts.getOrDefault(playerId, 0);
        int maxRetries = plugin.getConfigManager().getResourcePackMaxRetries();
        
        // Check if we've exceeded max retries
        if (currentRetries >= maxRetries) {
            plugin.getLogger().warning("Player " + player.getName() + " exceeded max resource pack retry attempts (" + maxRetries + ")");
            retryCounts.remove(playerId);
            lastRetryTimes.remove(playerId);
            return;
        }
        
        // Calculate exponential backoff delay: delay * (2^retry_count)
        int baseDelay = plugin.getConfigManager().getResourcePackRetryDelay();
        long delayTicks = baseDelay * (1L << currentRetries); // 2^retry_count
        
        // Increment retry count
        currentRetries++;
        retryCounts.put(playerId, currentRetries);
        lastRetryTimes.put(playerId, System.currentTimeMillis());
        
        plugin.getLogger().info("Retrying resource pack for " + player.getName() + " (attempt " + currentRetries + "/" + maxRetries + ") in " + (delayTicks / 20) + " seconds");
        plugin.debug("CoinListener.retryResourcePack() - player={}, attempt={}/{}, delay={} ticks", 
            player.getName(), currentRetries, maxRetries, delayTicks);
        
        // Schedule retry with exponential backoff
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            // Check if player is still online
            if (!player.isOnline()) {
                plugin.debug("CoinListener.retryResourcePack() - Player {} went offline before retry", player.getName());
                retryCounts.remove(playerId);
                lastRetryTimes.remove(playerId);
                return;
            }
            
            // Get resource pack configuration
            String resourcePackUrl = plugin.getConfigManager().getResourcePackUrl();
            String resourcePackHash = plugin.getConfigManager().getResourcePackHash();
            boolean hasHash = resourcePackHash != null && !resourcePackHash.isEmpty();
            
            try {
                plugin.debug("CoinListener.retryResourcePack() - Applying resource pack retry to player {}", player.getName());
                
                if (hasHash) {
                    byte[] hashBytes = hexStringToByteArray(resourcePackHash);
                    if (hashBytes != null && hashBytes.length == 20) {
                        player.setResourcePack(resourcePackUrl, hashBytes);
                    } else {
                        player.setResourcePack(resourcePackUrl);
                    }
                } else {
                    player.setResourcePack(resourcePackUrl);
                }
                
                plugin.debug("CoinListener.retryResourcePack() - Retry application initiated successfully");
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, 
                    "Failed to retry resource pack for " + player.getName(), e);
                plugin.debug("CoinListener.retryResourcePack() - Exception: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage());
            }
        }, delayTicks);
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
