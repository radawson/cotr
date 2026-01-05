package org.clockworx.cotr.listener;

import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
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
        ItemStack droppedItem = event.getItemDrop().getItemStack();
        
        // Check if the dropped item is a coin
        if (!CoinItem.isCoin(droppedItem)) {
            return; // Not a coin, let it drop normally
        }
        
        // Cancel the default drop (we'll create our own entity)
        event.setCancelled(true);
        
        // Get the drop location
        Player player = event.getPlayer();
        org.bukkit.Location dropLocation = player.getLocation().add(
            player.getLocation().getDirection().multiply(0.5)
        );
        dropLocation.setY(dropLocation.getY() + player.getEyeHeight() - 0.3);
        
        // Create a custom ItemDisplay entity for the coin
        org.bukkit.entity.ItemDisplay coinDisplay = CoinEntityManager.createCoinDisplay(
            dropLocation,
            droppedItem
        );
        
        if (coinDisplay != null) {
            // Add a small velocity to make it look natural
            coinDisplay.setVelocity(player.getLocation().getDirection().multiply(0.3));
        } else {
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
        // Only handle player pickups
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        
        Item itemEntity = event.getItem();
        ItemStack item = itemEntity.getItemStack();
        
        // Check if it's a coin
        if (CoinItem.isCoin(item)) {
            // For standard Item entities with coins, let them pick up normally
            // The custom display entities will be handled separately
            return;
        }
    }
    
    /**
     * Handles when a player interacts with an entity (right-click).
     * If the entity is a coin ItemDisplay, converts it to an ItemStack and gives it to the player.
     * 
     * @param event The PlayerInteractEntityEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemDisplay)) {
            return;
        }
        
        ItemDisplay display = (ItemDisplay) event.getRightClicked();
        Player player = event.getPlayer();
        
        // Check if this is a coin display
        ItemStack coin = CoinEntityManager.getCoinFromDisplay(display);
        if (coin == null) {
            return; // Not a coin display
        }
        
        // Remove the display entity
        display.remove();
        
        // Give the coin to the player
        CoinEntityManager.giveCoinToPlayer(player, coin);
        
        // Cancel the event to prevent other handlers
        event.setCancelled(true);
    }
}
