package org.clockworx.cotr.config;

import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * DropsConfig - Immutable configuration model for mob drop settings.
 * 
 * This class stores the configuration loaded from drops.yml, including:
 * - Whether mob drops are enabled globally
 * - Per-mob-type drop settings (chance, min/max amounts)
 * 
 * The configuration is intentionally separate from ConfigManager so that:
 * - Config parsing stays centralized in ConfigManager
 * - Other systems can safely consume typed, validated values
 * - The configuration intent is documented and stable over time
 */
public class DropsConfig {
    
    private final boolean enabled;
    private final Map<EntityType, MobDropSettings> mobSettings;
    
    /**
     * Creates a new DropsConfig with the specified values.
     * 
     * @param enabled Whether mob drops are enabled globally
     * @param mobSettings Map of EntityType to MobDropSettings for each mob
     */
    public DropsConfig(boolean enabled, @NotNull Map<EntityType, MobDropSettings> mobSettings) {
        this.enabled = enabled;
        this.mobSettings = mobSettings != null ? Collections.unmodifiableMap(new HashMap<>(mobSettings)) : Collections.emptyMap();
    }
    
    /**
     * Checks if mob drops are enabled globally.
     * 
     * @return true if drops are enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Gets the drop settings for a specific entity type.
     * 
     * @param entityType The EntityType to look up
     * @return The MobDropSettings for that entity type, or null if not configured
     */
    @Nullable
    public MobDropSettings getSettingsForEntity(@NotNull EntityType entityType) {
        return mobSettings.get(entityType);
    }
    
    /**
     * Gets all configured mob settings.
     * 
     * @return An unmodifiable map of EntityType to MobDropSettings
     */
    @NotNull
    public Map<EntityType, MobDropSettings> getAllMobSettings() {
        return mobSettings;
    }
    
    /**
     * Checks if a specific entity type has drop settings configured.
     * 
     * @param entityType The EntityType to check
     * @return true if settings exist for this entity type, false otherwise
     */
    public boolean hasSettingsForEntity(@NotNull EntityType entityType) {
        return mobSettings.containsKey(entityType);
    }
    
    /**
     * MobDropSettings - Configuration for a single mob type's coin drops.
     * 
     * Contains:
     * - chance: Probability (0.0-1.0) that coins will drop when this mob dies
     * - minAmount: Minimum number of coins to drop (if chance succeeds)
     * - maxAmount: Maximum number of coins to drop (if chance succeeds)
     */
    public static class MobDropSettings {
        private final double chance;
        private final int minAmount;
        private final int maxAmount;
        
        /**
         * Creates a new MobDropSettings with the specified values.
         * 
         * @param chance Probability of dropping coins (0.0-1.0)
         * @param minAmount Minimum coins to drop (must be >= 1)
         * @param maxAmount Maximum coins to drop (must be >= minAmount)
         * @throws IllegalArgumentException if chance is out of range or amounts are invalid
         */
        public MobDropSettings(double chance, int minAmount, int maxAmount) {
            // Validate chance
            if (chance < 0.0 || chance > 1.0) {
                throw new IllegalArgumentException("Chance must be between 0.0 and 1.0, got: " + chance);
            }
            
            // Validate amounts
            if (minAmount < 1) {
                throw new IllegalArgumentException("Min amount must be at least 1, got: " + minAmount);
            }
            if (maxAmount < minAmount) {
                throw new IllegalArgumentException("Max amount (" + maxAmount + ") must be >= min amount (" + minAmount + ")");
            }
            
            this.chance = chance;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }
        
        /**
         * Gets the probability that coins will drop.
         * 
         * @return The chance value (0.0-1.0)
         */
        public double getChance() {
            return chance;
        }
        
        /**
         * Gets the minimum number of coins to drop.
         * 
         * @return The minimum amount (>= 1)
         */
        public int getMinAmount() {
            return minAmount;
        }
        
        /**
         * Gets the maximum number of coins to drop.
         * 
         * @return The maximum amount (>= minAmount)
         */
        public int getMaxAmount() {
            return maxAmount;
        }
    }
}
