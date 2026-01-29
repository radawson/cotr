package org.clockworx.cotr.listener;

import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.config.DropsConfig;
import org.clockworx.cotr.item.CoinItem;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

/**
 * MobDropListener - Handles adding custom coins to mob drop tables.
 * 
 * This listener monitors EntityDeathEvent and adds coins to the drop list
 * based on the configuration in drops.yml. The system:
 * - Checks if mob drops are enabled globally
 * - Looks up the entity type in the configuration
 * - Rolls a chance to determine if coins should drop
 * - Generates a random amount between min and max
 * - Creates coin ItemStacks and adds them to the drop list
 * 
 * The listener follows the same pattern as EmeraldTrackingListener but
 * adds items to drops instead of tracking them.
 */
public class MobDropListener implements Listener {
    
    private final CoinOfTheRealmPlugin plugin;
    private final Random random;
    
    /**
     * Creates a new MobDropListener.
     * 
     * @param plugin The plugin instance
     */
    public MobDropListener(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
        this.random = new Random();
    }
    
    /**
     * Handles when an entity dies and potentially adds coins to its drops.
     * 
     * @param event The EntityDeathEvent
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(@NotNull EntityDeathEvent event) {
        // Check if drops are enabled globally
        DropsConfig dropsConfig = plugin.getConfigManager().getDropsConfig();
        if (dropsConfig == null || !dropsConfig.isEnabled()) {
            plugin.debug("MobDropListener.onEntityDeath() - Drops disabled or config not loaded");
            return;
        }
        
        // Get the entity type
        EntityType entityType = event.getEntityType();
        plugin.debug("MobDropListener.onEntityDeath() - Entity type: {}", entityType);
        
        // Check if this entity type has drop settings configured
        if (!dropsConfig.hasSettingsForEntity(entityType)) {
            plugin.debug("MobDropListener.onEntityDeath() - No drop settings for entity type: {}", entityType);
            return;
        }
        
        // Get the drop settings for this entity type
        DropsConfig.MobDropSettings settings = dropsConfig.getSettingsForEntity(entityType);
        if (settings == null) {
            plugin.debug("MobDropListener.onEntityDeath() - Settings are null for entity type: {}", entityType);
            return;
        }
        
        // Roll chance to determine if coins should drop
        // random.nextDouble() returns a value in [0.0, 1.0)
        // For chance = 0.5, we want 50% success rate, so roll < 0.5 should succeed
        double roll = random.nextDouble();
        double chance = settings.getChance();
        plugin.debug("MobDropListener.onEntityDeath() - Rolled: {}, chance: {}", roll, chance);
        
        if (roll >= chance) {
            plugin.debug("MobDropListener.onEntityDeath() - Chance failed (roll {} >= chance {}), no coins dropped", roll, chance);
            return;
        }
        
        // Calculate random amount between min and max (inclusive)
        int minAmount = settings.getMinAmount();
        int maxAmount = settings.getMaxAmount();
        int coinAmount = minAmount + random.nextInt(maxAmount - minAmount + 1);
        plugin.debug("MobDropListener.onEntityDeath() - Dropping {} coins (range: {}-{})", coinAmount, minAmount, maxAmount);
        
        // Create coin ItemStack(s)
        // Handle amounts > 64 by creating multiple stacks
        List<ItemStack> drops = event.getDrops();
        int remaining = coinAmount;
        
        while (remaining > 0) {
            int stackSize = Math.min(remaining, 64); // Max stack size is 64
            try {
                ItemStack coin = CoinItem.createCoin(stackSize);
                drops.add(coin);
                plugin.debug("MobDropListener.onEntityDeath() - Added {} coins to drops", stackSize);
                remaining -= stackSize;
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, 
                    "Failed to create coin for mob drop: " + e.getMessage(), e);
                plugin.debug("MobDropListener.onEntityDeath() - Exception creating coin: {} - {}", 
                    e.getClass().getSimpleName(), e.getMessage());
                break; // Stop trying if coin creation fails
            }
        }
        
        plugin.debug("MobDropListener.onEntityDeath() - Successfully added {} total coins to drops", coinAmount);
    }
}
