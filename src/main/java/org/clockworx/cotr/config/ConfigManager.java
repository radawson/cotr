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
    private FileConfiguration bankConfig;
    private FileConfiguration dropsConfig;
    private CoinConfig coinConfigObject;
    private BankConfig bankConfigObject;
    private DropsConfig dropsConfigObject;
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
    private int resourcePackMaxRetries;
    private boolean resourcePackKickOnDecline;
    private int resourcePackRetryDelay;
    
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
        
        // Load storage configuration from config.yml (global for all persistence)
        loadStorageConfig();
        
        // Load banking configuration from bank.yml
        loadBankConfigFile();
        loadBankConfig();
        
        // Load mob drops configuration from drops.yml
        loadDropsConfigFile();
        loadDropsConfig();
        
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
     * Loads the bank.yml configuration file.
     * Creates a default bank.yml if it doesn't exist.
     */
    private void loadBankConfigFile() {
        File bankConfigFile = new File(plugin.getDataFolder(), "bank.yml");
        
        // If bank.yml doesn't exist, save default
        if (!bankConfigFile.exists()) {
            saveDefaultBankConfig();
        }
        
        // Load the bank config
        bankConfig = YamlConfiguration.loadConfiguration(bankConfigFile);
    }
    
    /**
     * Loads the drops.yml configuration file.
     * Creates a default drops.yml if it doesn't exist.
     */
    private void loadDropsConfigFile() {
        File dropsConfigFile = new File(plugin.getDataFolder(), "drops.yml");
        
        // If drops.yml doesn't exist, save default
        if (!dropsConfigFile.exists()) {
            saveDefaultDropsConfig();
        }
        
        // Load the drops config
        dropsConfig = YamlConfiguration.loadConfiguration(dropsConfigFile);
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
     * Saves the default bank.yml from resources to the data folder.
     */
    private void saveDefaultBankConfig() {
        File bankConfigFile = new File(plugin.getDataFolder(), "bank.yml");
        
        // Copy default bank config from resources
        try (InputStream defaultBankConfig = plugin.getResource("bank.yml")) {
            if (defaultBankConfig != null) {
                Files.copy(defaultBankConfig, bankConfigFile.toPath());
                plugin.getLogger().info("Created default bank.yml");
            } else {
                plugin.getLogger().warning("Default bank.yml not found in resources!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save default bank.yml: " + e.getMessage());
        }
    }
    
    /**
     * Saves the default drops.yml from resources to the data folder.
     */
    private void saveDefaultDropsConfig() {
        File dropsConfigFile = new File(plugin.getDataFolder(), "drops.yml");
        
        // Copy default drops config from resources
        try (InputStream defaultDropsConfig = plugin.getResource("drops.yml")) {
            if (defaultDropsConfig != null) {
                Files.copy(defaultDropsConfig, dropsConfigFile.toPath());
                plugin.getLogger().info("Created default drops.yml");
            } else {
                plugin.getLogger().warning("Default drops.yml not found in resources!");
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save default drops.yml: " + e.getMessage());
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
     * Loads the storage configuration from config.yml.
     * Storage configuration is global and applies to all persistence operations
     * (banking, account memberships, exchange tracking, etc.).
     */
    private void loadStorageConfig() {
        // Storage configuration is now in config.yml under the "storage" key
        bankStorageType = config.getString("storage.type", "database");
        databaseType = config.getString("storage.database.type", "sqlite");
        databasePrefix = config.getString("storage.database.prefix", "");
        databaseFile = config.getString("storage.database.file", "banks.db");
        databaseHost = config.getString("storage.database.host", "localhost");
        databasePort = config.getInt("storage.database.port", 3306);
        databaseName = config.getString("storage.database.database", "cotr");
        databaseUsername = config.getString("storage.database.username", "cotr");
        databasePassword = config.getString("storage.database.password", "");
        
        plugin.getLogger().info("Storage configuration loaded from config.yml");
        plugin.getLogger().info("Storage type: " + bankStorageType);
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
    }
    
    /**
     * Loads the banking configuration from bank.yml.
     * Note: Storage configuration is no longer in bank.yml - it's in config.yml.
     */
    private void loadBankConfig() {
        if (bankConfig == null) {
            plugin.getLogger().warning("bank.yml not loaded; banking configuration may be invalid.");
            return;
        }
        
        bankingEnabled = bankConfig.getBoolean("banking.enabled", true);
        defaultAccountPattern = bankConfig.getString("banking.default-account-pattern", "{player-uuid}-main");
        useOwnController = bankConfig.getBoolean("banking.use-own-controller", true);
        servicePriority = bankConfig.getString("banking.service-priority", "NORMAL");
        
        // Emerald exchange configuration
        boolean emeraldExchangeEnabled = bankConfig.getBoolean("exchange.emerald.enabled", true);
        int baseRate = bankConfig.getInt("exchange.emerald.base-rate", 10);
        int minRate = bankConfig.getInt("exchange.emerald.min-rate", 1);
        int maxRate = bankConfig.getInt("exchange.emerald.max-rate", 100);
        String mode = bankConfig.getString("exchange.emerald.mode", "hybrid").toLowerCase();
        String fallbackRegion = bankConfig.getString("exchange.regions.fallback", "global");
        
        if (minRate < 1) {
            plugin.getLogger().warning("Invalid exchange.emerald.min-rate: " + minRate + ". Using default: 1");
            minRate = 1;
        }
        if (maxRate < minRate) {
            plugin.getLogger().warning("exchange.emerald.max-rate < min-rate. Adjusting max-rate to min-rate.");
            maxRate = minRate;
        }
        if (baseRate < minRate || baseRate > maxRate) {
            plugin.getLogger().warning("exchange.emerald.base-rate out of range. Clamping to [" + minRate + ", " + maxRate + "]");
            baseRate = Math.max(minRate, Math.min(baseRate, maxRate));
        }
        if (fallbackRegion == null || fallbackRegion.isBlank()) {
            fallbackRegion = "global";
        }
        
        List<BankConfig.Threshold> thresholds = new ArrayList<>();
        List<?> thresholdList = bankConfig.getList("exchange.emerald.thresholds");
        if (thresholdList != null) {
            for (Object thresholdObj : thresholdList) {
                if (thresholdObj instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> thresholdMap = (java.util.Map<String, Object>) thresholdObj;
                    Object maxObj = thresholdMap.get("max-emeralds");
                    Object rateObj = thresholdMap.get("rate");
                    int maxEmeralds = maxObj instanceof Number ? ((Number) maxObj).intValue() : -1;
                    int rate = rateObj instanceof Number ? ((Number) rateObj).intValue() : -1;
                    if (maxEmeralds >= 0 && rate > 0) {
                        thresholds.add(new BankConfig.Threshold(maxEmeralds, rate));
                    }
                }
            }
        }
        
        boolean formulaEnabled = bankConfig.getBoolean("exchange.emerald.formula.enabled", false);
        int divisor = bankConfig.getInt("exchange.emerald.formula.divisor", 500);
        int step = bankConfig.getInt("exchange.emerald.formula.step", 1);
        if (divisor < 1) {
            plugin.getLogger().warning("Invalid exchange.emerald.formula.divisor: " + divisor + ". Using default: 500");
            divisor = 500;
        }
        
        BankConfig.Formula formula = new BankConfig.Formula(formulaEnabled, divisor, step);
        BankConfig.EmeraldExchangeConfig emeraldExchangeConfig = new BankConfig.EmeraldExchangeConfig(
            emeraldExchangeEnabled,
            baseRate,
            minRate,
            maxRate,
            mode,
            thresholds,
            formula
        );
        BankConfig.ExchangeConfig exchangeConfig = new BankConfig.ExchangeConfig(fallbackRegion, emeraldExchangeConfig);
        
        // BankConfig no longer includes storage configuration - that's now global in config.yml
        bankConfigObject = new BankConfig(
            bankingEnabled,
            defaultAccountPattern,
            useOwnController,
            servicePriority,
            exchangeConfig
        );
        
        plugin.getLogger().info("Banking enabled: " + bankingEnabled);
        if (bankingEnabled) {
            plugin.getLogger().info("Use own BankController: " + useOwnController);
            if (useOwnController) {
                plugin.getLogger().info("ServiceIO registration priority: " + servicePriority);
            }
        }
    }
    
    /**
     * Loads the mob drops configuration from drops.yml.
     */
    private void loadDropsConfig() {
        if (dropsConfig == null) {
            plugin.getLogger().warning("drops.yml not loaded; mob drops configuration may be invalid.");
            // Initialize with disabled config to prevent null pointer exceptions
            dropsConfigObject = new DropsConfig(false, new java.util.HashMap<>());
            return;
        }
        
        boolean enabled = dropsConfig.getBoolean("enabled", true);
        
        // Parse mob settings
        java.util.Map<org.bukkit.entity.EntityType, DropsConfig.MobDropSettings> mobSettings = new java.util.HashMap<>();
        
        if (dropsConfig.contains("mobs") && dropsConfig.isConfigurationSection("mobs")) {
            org.bukkit.configuration.ConfigurationSection mobsSection = dropsConfig.getConfigurationSection("mobs");
            if (mobsSection != null) {
                for (String mobName : mobsSection.getKeys(false)) {
                    try {
                        // Convert string to EntityType enum
                        org.bukkit.entity.EntityType entityType = org.bukkit.entity.EntityType.valueOf(mobName.toUpperCase());
                        
                        // Get mob settings from the mob's configuration section
                        org.bukkit.configuration.ConfigurationSection mobSection = mobsSection.getConfigurationSection(mobName);
                        if (mobSection == null) {
                            plugin.getLogger().warning("Invalid mob configuration for " + mobName + ": not a configuration section. Skipping.");
                            continue;
                        }
                        
                        double chance = mobSection.getDouble("chance", 0.5);
                        int minAmount = mobSection.getInt("min-amount", 1);
                        int maxAmount = mobSection.getInt("max-amount", 3);
                        
                        // Validate and clamp values
                        if (chance < 0.0) {
                            plugin.getLogger().warning("Invalid chance for " + mobName + ": " + chance + ". Clamping to 0.0");
                            chance = 0.0;
                        } else if (chance > 1.0) {
                            plugin.getLogger().warning("Invalid chance for " + mobName + ": " + chance + ". Clamping to 1.0");
                            chance = 1.0;
                        }
                        
                        if (minAmount < 1) {
                            plugin.getLogger().warning("Invalid min-amount for " + mobName + ": " + minAmount + ". Using default: 1");
                            minAmount = 1;
                        }
                        
                        if (maxAmount < minAmount) {
                            plugin.getLogger().warning("Invalid max-amount for " + mobName + " (" + maxAmount + " < min " + minAmount + "). Swapping values.");
                            int temp = minAmount;
                            minAmount = maxAmount;
                            maxAmount = temp;
                        }
                        
                        // Create MobDropSettings
                        try {
                            DropsConfig.MobDropSettings settings = new DropsConfig.MobDropSettings(chance, minAmount, maxAmount);
                            mobSettings.put(entityType, settings);
                            plugin.debug("Loaded drop settings for " + mobName + ": chance=" + chance + ", amount=" + minAmount + "-" + maxAmount);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Failed to create drop settings for " + mobName + ": " + e.getMessage());
                        }
                        
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid entity type in drops.yml: " + mobName + ". Skipping.");
                    }
                }
            }
        }
        
        dropsConfigObject = new DropsConfig(enabled, mobSettings);
        
        plugin.getLogger().info("Mob drops enabled: " + enabled);
        if (enabled) {
            plugin.getLogger().info("Configured " + mobSettings.size() + " mob type(s) for coin drops");
        }
    }
    
    /**
     * Loads the resource pack configuration from the config file.
     */
    private void loadResourcePackConfig() {
        resourcePackUrl = config.getString("resource-pack.url", "");
        resourcePackHash = config.getString("resource-pack.hash", "");
        resourcePackPrompt = config.getBoolean("resource-pack.prompt", true);
        resourcePackMaxRetries = config.getInt("resource-pack.max-retries", 3);
        resourcePackKickOnDecline = config.getBoolean("resource-pack.kick-on-decline", false);
        resourcePackRetryDelay = config.getInt("resource-pack.retry-delay", 100);
        
        // Validate max retries
        if (resourcePackMaxRetries < 0) {
            plugin.getLogger().warning("Invalid resource-pack.max-retries: " + resourcePackMaxRetries + ". Using default: 3");
            resourcePackMaxRetries = 3;
        }
        
        // Validate retry delay
        if (resourcePackRetryDelay < 1) {
            plugin.getLogger().warning("Invalid resource-pack.retry-delay: " + resourcePackRetryDelay + ". Using default: 100");
            resourcePackRetryDelay = 100;
        }
        
        if (resourcePackUrl != null && !resourcePackUrl.isEmpty()) {
            String urlDisplay = resourcePackUrl.length() > 60 ? resourcePackUrl.substring(0, 57) + "..." : resourcePackUrl;
            plugin.getLogger().info("Resource pack URL configured: " + urlDisplay);
            if (resourcePackHash != null && !resourcePackHash.isEmpty()) {
                plugin.getLogger().info("Resource pack hash configured (SHA-1 verification enabled)");
            }
            plugin.getLogger().info("Resource pack retry settings: max=" + resourcePackMaxRetries + ", delay=" + resourcePackRetryDelay + " ticks");
            if (resourcePackKickOnDecline) {
                plugin.getLogger().info("Resource pack kick-on-decline enabled");
                if (resourcePackPrompt) {
                    plugin.getLogger().warning("kick-on-decline is enabled but prompt is true. For mandatory packs, set prompt: false in config.yml");
                }
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
     * Gets the storage type.
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The storage type ("database" or "yaml")
     */
    @NotNull
    public String getBankStorageType() {
        return bankStorageType;
    }
    
    /**
     * Gets the database type.
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database type ("sqlite" or "mysql")
     */
    @NotNull
    public String getDatabaseType() {
        return databaseType;
    }
    
    /**
     * Gets the database table prefix.
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The table prefix (empty string if no prefix)
     */
    @NotNull
    public String getDatabasePrefix() {
        return databasePrefix != null ? databasePrefix : "";
    }
    
    /**
     * Gets the database file path (for SQLite).
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database file path
     */
    @NotNull
    public String getDatabaseFile() {
        return databaseFile;
    }
    
    /**
     * Gets the database host (for MySQL).
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database host
     */
    @NotNull
    public String getDatabaseHost() {
        return databaseHost;
    }
    
    /**
     * Gets the database port (for MySQL).
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database port
     */
    public int getDatabasePort() {
        return databasePort;
    }
    
    /**
     * Gets the database name (for MySQL).
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database name
     */
    @NotNull
    public String getDatabaseName() {
        return databaseName;
    }
    
    /**
     * Gets the database username (for MySQL).
     * Storage configuration is global and applies to all persistence operations.
     * 
     * @return The database username
     */
    @NotNull
    public String getDatabaseUsername() {
        return databaseUsername;
    }
    
    /**
     * Gets the database password (for MySQL).
     * Storage configuration is global and applies to all persistence operations.
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
     * Builds the shared-library connection settings from this configuration.
     * Used by both Flyway migrations and the Hibernate session manager.
     *
     * @return the database settings for the clockworx-data layer
     */
    @NotNull
    public org.clockworx.data.DatabaseSettings getDatabaseSettings() {
        String jdbcUrl;
        if ("sqlite".equals(databaseType)) {
            File dbFile = new File(plugin.getDataFolder(), databaseFile);
            jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        } else if ("mysql".equals(databaseType)) {
            jdbcUrl = getDatabaseConnectionString();
        } else {
            throw new IllegalStateException("Unsupported database type: " + databaseType);
        }
        return org.clockworx.data.DatabaseSettings.withDefaults(
                org.clockworx.data.DatabaseType.fromString(databaseType),
                jdbcUrl,
                databaseUsername,
                databasePassword,
                getDatabasePrefix());
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
     * Gets the maximum number of retry attempts for resource pack application.
     * 
     * @return The maximum number of retries (0 = no retry)
     */
    public int getResourcePackMaxRetries() {
        return resourcePackMaxRetries;
    }
    
    /**
     * Checks if players should be kicked when they decline the resource pack.
     * 
     * @return true if players should be kicked on decline
     */
    public boolean isResourcePackKickOnDecline() {
        return resourcePackKickOnDecline;
    }
    
    /**
     * Gets the delay between retry attempts in ticks.
     * 
     * @return The retry delay in ticks (20 ticks = 1 second)
     */
    public int getResourcePackRetryDelay() {
        return resourcePackRetryDelay;
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
        // Reload bank config file
        loadBankConfigFile();
        // Reload drops config file
        loadDropsConfigFile();
        // Reload all configs
        return loadConfig();
    }
    
    /**
     * Gets the bank configuration.
     *
     * @return The BankConfig instance
     */
    @NotNull
    public BankConfig getBankConfig() {
        return bankConfigObject;
    }
    
    /**
     * Gets the mob drops configuration.
     *
     * @return The DropsConfig instance
     */
    @NotNull
    public DropsConfig getDropsConfig() {
        return dropsConfigObject;
    }
}
