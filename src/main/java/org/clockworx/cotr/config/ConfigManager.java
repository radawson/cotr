package org.clockworx.cotr.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * ConfigManager - Manages loading and accessing the plugin configuration
 * 
 * This class handles:
 * - Loading config.yml from the plugin's data folder
 * - Validating configuration values
 * - Providing access to coin configuration
 * - Saving default config if it doesn't exist
 * 
 * The configuration supports:
 * - Configurable coin items via namespaced keys (vanilla or custom)
 * - Display name and lore customization
 * - CustomModelData for resource pack integration
 * - Banking configuration
 */
public class ConfigManager {
    
    private final CoinOfTheRealmPlugin plugin;
    private FileConfiguration config;
    private CoinConfig coinConfig;
    private boolean debugEnabled;
    private boolean bankingEnabled;
    private String defaultAccountPattern;
    private String membershipStorage;
    private String resourcePackUrl;
    private String resourcePackHash;
    private boolean resourcePackPrompt;
    
    /**
     * Creates a new ConfigManager for the specified plugin.
     * 
     * @param plugin The plugin instance
     */
    public ConfigManager(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Loads the configuration from config.yml.
     * Creates a default config file if it doesn't exist.
     * 
     * @return true if the config was loaded successfully, false otherwise
     */
    public boolean loadConfig() {
        // Ensure the data folder exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        // If config doesn't exist, save default
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        
        // Load the config
        config = YamlConfiguration.loadConfiguration(configFile);
        
        // Load debug setting first (needed for other config loading)
        loadDebugConfig();
        
        // Load coin configuration
        loadCoinConfig();
        
        // Load banking configuration
        loadBankingConfig();
        
        // Load resource pack configuration
        loadResourcePackConfig();
        
        plugin.getLogger().info("Configuration loaded successfully");
        return true;
    }
    
    /**
     * Saves the default config.yml from resources to the data folder.
     */
    private void saveDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        
        // Copy default config from resources
        try (InputStream defaultConfig = plugin.getResource("config.yml")) {
            if (defaultConfig != null) {
                Files.copy(defaultConfig, configFile.toPath());
                plugin.getLogger().info("Created default config.yml");
            } else {
                plugin.getLogger().warning("Default config.yml not found in resources!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save default config.yml: " + e.getMessage());
        }
    }
    
    /**
     * Loads the debug configuration from the config file.
     */
    private void loadDebugConfig() {
        debugEnabled = config.getBoolean("debug", false);
        plugin.getLogger().info("Debug mode: " + debugEnabled);
    }
    
    /**
     * Loads the coin configuration from the config file.
     * Validates the configuration and provides sensible defaults if invalid.
     */
    private void loadCoinConfig() {
        String itemKey = config.getString("coin.item", "minecraft:gold_nugget");
        String displayName = config.getString("coin.display-name", "Coin of the Realm");
        List<String> lore = config.getStringList("coin.lore");
        int customModelData = config.getInt("coin.custom-model-data", 1000);
        
        // Parse model field (e.g., "cotr:coin")
        String modelString = config.getString("coin.model", null);
        String modelNamespace = null;
        String modelKey = null;
        if (modelString != null && modelString.contains(":")) {
            String[] parts = modelString.split(":", 2);
            if (parts.length == 2) {
                modelNamespace = parts[0];
                modelKey = parts[1];
            }
        }
        
        // Provide default lore if empty
        if (lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("A valuable currency");
            lore.add("used throughout the realm.");
        }
        
        // Validate that the item is droppable (basic check)
        if (!isValidItemKey(itemKey)) {
            plugin.getLogger().warning("Invalid coin item key: " + itemKey + ". Using default: minecraft:gold_nugget");
            itemKey = "minecraft:gold_nugget";
        }
        
        coinConfig = new CoinConfig(itemKey, displayName, lore, customModelData, modelNamespace, modelKey);
        
        plugin.getLogger().info("Coin configuration loaded: " + itemKey);
    }
    
    /**
     * Validates that an item key is in a valid format.
     * 
     * @param itemKey The item key to validate
     * @return true if the key appears valid, false otherwise
     */
    private boolean isValidItemKey(@NotNull String itemKey) {
        if (itemKey == null || itemKey.isEmpty()) {
            return false;
        }
        
        // Check if it's a valid namespaced key format
        if (itemKey.contains(":")) {
            String[] parts = itemKey.split(":", 2);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                return false;
            }
            
            // If it's a minecraft namespace, check if Material exists
            if ("minecraft".equals(parts[0])) {
                try {
                    Material.valueOf(parts[1].toUpperCase());
                    return true;
                } catch (IllegalArgumentException e) {
                    return false;
                }
            }
            
            // Custom namespace - assume valid (will be validated when used)
            return true;
        }
        
        // Try as Material enum name
        try {
            Material.valueOf(itemKey.toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * Loads the banking configuration from the config file.
     */
    private void loadBankingConfig() {
        bankingEnabled = config.getBoolean("banking.enabled", true);
        defaultAccountPattern = config.getString("banking.default-account-pattern", "{player-uuid}-main");
        membershipStorage = config.getString("banking.membership-storage", "yaml");
        
        plugin.getLogger().info("Banking enabled: " + bankingEnabled);
    }
    
    /**
     * Loads the resource pack configuration from the config file.
     */
    private void loadResourcePackConfig() {
        resourcePackUrl = config.getString("resource-pack.url", "");
        resourcePackHash = config.getString("resource-pack.hash", "");
        resourcePackPrompt = config.getBoolean("resource-pack.prompt", true);
        
        if (resourcePackUrl != null && !resourcePackUrl.isEmpty()) {
            plugin.getLogger().info("Resource pack URL configured: " + resourcePackUrl);
            if (resourcePackHash != null && !resourcePackHash.isEmpty()) {
                plugin.getLogger().info("Resource pack hash configured (SHA-1 verification enabled)");
            }
        } else {
            plugin.getLogger().info("Resource pack URL not configured - automatic application disabled");
        }
    }
    
    /**
     * Checks if debug mode is enabled.
     * 
     * @return true if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
    
    /**
     * Gets the coin configuration.
     * 
     * @return The CoinConfig instance
     */
    @NotNull
    public CoinConfig getCoinConfig() {
        return coinConfig;
    }
    
    /**
     * Checks if banking is enabled in the configuration.
     * 
     * @return true if banking is enabled
     */
    public boolean isBankingEnabled() {
        return bankingEnabled;
    }
    
    /**
     * Gets the default account name pattern.
     * 
     * @return The account name pattern (e.g., "{player-uuid}-main")
     */
    @NotNull
    public String getDefaultAccountPattern() {
        return defaultAccountPattern;
    }
    
    /**
     * Gets the membership storage type.
     * 
     * @return The storage type ("yaml" or "database")
     */
    @NotNull
    public String getMembershipStorage() {
        return membershipStorage;
    }
    
    /**
     * Gets the resource pack URL.
     * 
     * @return The resource pack URL, or empty string if not configured
     */
    @NotNull
    public String getResourcePackUrl() {
        return resourcePackUrl != null ? resourcePackUrl : "";
    }
    
    /**
     * Gets the resource pack SHA-1 hash.
     * 
     * @return The resource pack hash, or empty string if not configured
     */
    @NotNull
    public String getResourcePackHash() {
        return resourcePackHash != null ? resourcePackHash : "";
    }
    
    /**
     * Checks if the resource pack should prompt players.
     * 
     * @return true if players should be prompted, false for automatic application
     */
    public boolean isResourcePackPrompt() {
        return resourcePackPrompt;
    }
    
    /**
     * Reloads the configuration from disk.
     * 
     * @return true if reload was successful
     */
    public boolean reload() {
        return loadConfig();
    }
}
