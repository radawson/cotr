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
    private FileConfiguration coinConfig;
    private CoinConfig coinConfigObject;
    private boolean debugEnabled;
    private boolean bankingEnabled;
    private String defaultAccountPattern;
    private boolean useOwnController;
    private String servicePriority;
    private String bankStorageType;
    private String databaseType;
    private String databasePrefix;
    private String databaseFile;
    private String databaseHost;
    private int databasePort;
    private String databaseName;
    private String databaseUsername;
    private String databasePassword;
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
        
        // Load coin configuration from coin.yml
        loadCoinConfigFile();
        loadCoinConfig();
        
        // Load banking configuration
        loadBankingConfig();
        
        // Load resource pack configuration
        loadResourcePackConfig();
        
        plugin.getLogger().info("Configuration loaded successfully");
        return true;
    }
    
    /**
     * Loads the coin.yml configuration file.
     * Creates a default coin.yml if it doesn't exist.
     */
    private void loadCoinConfigFile() {
        File coinConfigFile = new File(plugin.getDataFolder(), "coin.yml");
        
        // If coin.yml doesn't exist, save default
        if (!coinConfigFile.exists()) {
            saveDefaultCoinConfig();
        }
        
        // Load the coin config
        coinConfig = YamlConfiguration.loadConfiguration(coinConfigFile);
    }
    
    /**
     * Saves the default coin.yml from resources to the data folder.
     */
    private void saveDefaultCoinConfig() {
        File coinConfigFile = new File(plugin.getDataFolder(), "coin.yml");
        
        // Copy default coin config from resources
        try (InputStream defaultCoinConfig = plugin.getResource("coin.yml")) {
            if (defaultCoinConfig != null) {
                Files.copy(defaultCoinConfig, coinConfigFile.toPath());
                plugin.getLogger().info("Created default coin.yml");
            } else {
                plugin.getLogger().warning("Default coin.yml not found in resources!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save default coin.yml: " + e.getMessage());
        }
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
     * Loads the coin configuration from coin.yml.
     * Validates the configuration and provides sensible defaults if invalid.
     */
    private void loadCoinConfig() {
        String itemKey = coinConfig.getString("item", "cotr:coin");
        String fallbackItemKey = coinConfig.getString("fallback-item", "minecraft:gold_nugget");
        String displayName = coinConfig.getString("display-name", "Coin of the Realm");
        String description = coinConfig.getString("description", null);
        List<String> lore = coinConfig.getStringList("lore");
        int customModelData = 1000; // Deprecated, but kept for compatibility
        int maxStackSize = coinConfig.getInt("max-stack-size", 64);
        String rarity = coinConfig.getString("rarity", "common");
        boolean enchantmentGlint = coinConfig.getBoolean("enchantment-glint", true);
        int useDuration = coinConfig.getInt("use-duration", 0);
        String useAnimation = coinConfig.getString("use-animation", "none");
        boolean attributeModifiersEnabled = coinConfig.getBoolean("attribute-modifiers.enabled", false);
        
        // Validate max stack size
        if (maxStackSize < 1 || maxStackSize > 64) {
            plugin.getLogger().warning("Invalid max-stack-size: " + maxStackSize + ". Using default: 64");
            maxStackSize = 64;
        }
        
        // Validate rarity
        if (!rarity.equals("common") && !rarity.equals("uncommon") && 
            !rarity.equals("rare") && !rarity.equals("epic")) {
            plugin.getLogger().warning("Invalid rarity: " + rarity + ". Using default: common");
            rarity = "common";
        }
        
        // Validate use animation
        if (!useAnimation.equals("eat") && !useAnimation.equals("drink") && 
            !useAnimation.equals("block") && !useAnimation.equals("bow") &&
            !useAnimation.equals("crossbow") && !useAnimation.equals("spyglass") &&
            !useAnimation.equals("none")) {
            plugin.getLogger().warning("Invalid use-animation: " + useAnimation + ". Using default: none");
            useAnimation = "none";
        }
        
        // Parse model field (e.g., "cotr:coin")
        String modelString = coinConfig.getString("model", null);
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
        
        // Validate that the item key is in valid format
        if (!isValidItemKey(itemKey)) {
            plugin.getLogger().warning("Invalid coin item key: " + itemKey + ". Using default: cotr:coin");
            itemKey = "cotr:coin";
        }
        
        // Validate fallback item key
        if (!isValidItemKey(fallbackItemKey)) {
            plugin.getLogger().warning("Invalid fallback item key: " + fallbackItemKey + ". Using default: minecraft:gold_nugget");
            fallbackItemKey = "minecraft:gold_nugget";
        }
        
        // Load attribute modifiers
        List<CoinConfig.AttributeModifier> attributeModifiers = new ArrayList<>();
        if (attributeModifiersEnabled) {
            List<?> modifiersList = coinConfig.getList("attribute-modifiers.modifiers");
            if (modifiersList != null) {
                for (Object modifierObj : modifiersList) {
                    if (modifierObj instanceof java.util.Map) {
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> modifierMap = (java.util.Map<String, Object>) modifierObj;
                        String attr = (String) modifierMap.get("attribute");
                        Object amountObj = modifierMap.get("amount");
                        double amount = amountObj instanceof Number ? ((Number) amountObj).doubleValue() : 0.0;
                        String operation = (String) modifierMap.get("operation");
                        String slot = (String) modifierMap.get("slot");
                        
                        if (attr != null && operation != null && slot != null) {
                            attributeModifiers.add(new CoinConfig.AttributeModifier(attr, amount, operation, slot));
                        }
                    }
                }
            }
        }
        
        coinConfigObject = new CoinConfig(itemKey, fallbackItemKey, displayName, description, lore, 
                                         customModelData, modelNamespace, modelKey, maxStackSize, rarity,
                                         enchantmentGlint, useDuration, useAnimation,
                                         attributeModifiersEnabled, attributeModifiers);
        
        plugin.getLogger().info("Coin configuration loaded: " + itemKey + " (fallback: " + fallbackItemKey + ")");
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
        useOwnController = config.getBoolean("banking.use-own-controller", true);
        servicePriority = config.getString("banking.service-priority", "NORMAL");
        
        // Bank storage configuration
        bankStorageType = config.getString("banking.storage.type", "database");
        databaseType = config.getString("banking.storage.database.type", "sqlite");
        databasePrefix = config.getString("banking.storage.database.prefix", "");
        databaseFile = config.getString("banking.storage.database.file", "banks.db");
        databaseHost = config.getString("banking.storage.database.host", "localhost");
        databasePort = config.getInt("banking.storage.database.port", 3306);
        databaseName = config.getString("banking.storage.database.database", "cotr");
        databaseUsername = config.getString("banking.storage.database.username", "cotr");
        databasePassword = config.getString("banking.storage.database.password", "");
        
        plugin.getLogger().info("Banking enabled: " + bankingEnabled);
        if (bankingEnabled) {
            plugin.getLogger().info("Bank storage type: " + bankStorageType);
            if ("database".equals(bankStorageType)) {
                plugin.getLogger().info("Database type: " + databaseType);
                if (!databasePrefix.isEmpty()) {
                    plugin.getLogger().info("Database table prefix: '" + databasePrefix + "'");
                }
                if ("sqlite".equals(databaseType)) {
                    plugin.getLogger().info("SQLite database file: " + databaseFile);
                } else if ("mysql".equals(databaseType)) {
                    plugin.getLogger().info("MySQL database: " + databaseName + "@" + databaseHost + ":" + databasePort);
                }
            }
            plugin.getLogger().info("Use own BankController: " + useOwnController);
            if (useOwnController) {
                plugin.getLogger().info("ServiceIO registration priority: " + servicePriority);
            }
        }
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
        return coinConfigObject;
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
     * Checks if we should use our own BankController.
     * 
     * @return true if we should register our own BankController
     */
    public boolean isUseOwnController() {
        return useOwnController;
    }
    
    /**
     * Gets the ServiceIO registration priority.
     * 
     * @return The priority string (LOWEST, LOW, NORMAL, HIGH, HIGHEST)
     */
    @NotNull
    public String getServicePriority() {
        return servicePriority;
    }
    
    /**
     * Gets the bank storage type.
     * 
     * @return The storage type ("database" or "yaml")
     */
    @NotNull
    public String getBankStorageType() {
        return bankStorageType;
    }
    
    /**
     * Gets the database type.
     * 
     * @return The database type ("sqlite" or "mysql")
     */
    @NotNull
    public String getDatabaseType() {
        return databaseType;
    }
    
    /**
     * Gets the database table prefix.
     * 
     * @return The table prefix (empty string if no prefix)
     */
    @NotNull
    public String getDatabasePrefix() {
        return databasePrefix != null ? databasePrefix : "";
    }
    
    /**
     * Gets the database file path (for SQLite).
     * 
     * @return The database file path
     */
    @NotNull
    public String getDatabaseFile() {
        return databaseFile;
    }
    
    /**
     * Gets the database host (for MySQL).
     * 
     * @return The database host
     */
    @NotNull
    public String getDatabaseHost() {
        return databaseHost;
    }
    
    /**
     * Gets the database port (for MySQL).
     * 
     * @return The database port
     */
    public int getDatabasePort() {
        return databasePort;
    }
    
    /**
     * Gets the database name (for MySQL).
     * 
     * @return The database name
     */
    @NotNull
    public String getDatabaseName() {
        return databaseName;
    }
    
    /**
     * Gets the database username (for MySQL).
     * 
     * @return The database username
     */
    @NotNull
    public String getDatabaseUsername() {
        return databaseUsername;
    }
    
    /**
     * Gets the database password (for MySQL).
     * 
     * @return The database password
     */
    @NotNull
    public String getDatabasePassword() {
        return databasePassword;
    }
    
    /**
     * Gets the database connection string.
     * For SQLite, returns the file path.
     * For MySQL, returns a JDBC connection string.
     * 
     * @return The connection string
     */
    @NotNull
    public String getDatabaseConnectionString() {
        if ("sqlite".equals(databaseType)) {
            // For SQLite, return the file path (will be prepended with jdbc:sqlite: in DatabaseBankStorage)
            File dbFile = new File(plugin.getDataFolder(), databaseFile);
            return dbFile.getAbsolutePath();
        } else if ("mysql".equals(databaseType)) {
            // For MySQL, return JDBC connection string with SSL disabled and auto-reconnect enabled
            // useSSL=false is required for older MySQL servers or when SSL is not configured
            // autoReconnect=true helps with connection stability
            return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true",
                databaseHost, databasePort, databaseName);
        } else {
            throw new IllegalStateException("Unsupported database type: " + databaseType);
        }
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
     * Reloads both config.yml and coin.yml.
     * 
     * @return true if reload was successful
     */
    public boolean reload() {
        // Reload coin config file
        loadCoinConfigFile();
        // Reload all configs
        return loadConfig();
    }
}
