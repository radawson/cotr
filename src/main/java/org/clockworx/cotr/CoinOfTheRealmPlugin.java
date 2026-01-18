package org.clockworx.cotr;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.clockworx.cotr.bank.AccountMembershipManager;
import org.clockworx.cotr.bank.BankManager;
import org.clockworx.cotr.bank.impl.CotrBankController;
import org.clockworx.cotr.storage.BankStorage;
import org.clockworx.cotr.storage.DatabaseBankStorage;
import org.clockworx.cotr.bank.exchange.BankExchangeService;
import org.clockworx.cotr.bank.exchange.EmeraldTracker;
import org.clockworx.cotr.command.CotrCommand;
import org.clockworx.cotr.config.ConfigManager;
import org.clockworx.cotr.datapack.DataPackManager;
import org.clockworx.cotr.listener.CoinListener;
import org.clockworx.cotr.listener.EmeraldTrackingListener;
import org.clockworx.cotr.region.WorldGuardRegionResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
     * Note: This is just the key part - the namespace is automatically the plugin name.
     */
    public static final String COIN_NBT_KEY = "coin";
    
    private static CoinOfTheRealmPlugin instance;
    
    private ConfigManager configManager;
    private AccountMembershipManager membershipManager;
    private BankManager bankManager;
    private DataPackManager dataPackManager;
    private BankStorage bankStorage;
    private DatabaseBankStorage databaseBankStorage; // Specific type for AccountMembershipManager
    private CotrBankController cotrBankController;
    private WorldGuardRegionResolver regionResolver;
    private EmeraldTracker emeraldTracker;
    private BankExchangeService bankExchangeService;
    
    @Override
    public void onEnable() {
        instance = this;
        
        // Load configuration
        configManager = new ConfigManager(this);
        if (!configManager.loadConfig()) {
            getLogger().severe("Failed to load configuration! Plugin may not work correctly.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize bank storage first (needed for AccountMembershipManager)
        if (configManager.isBankingEnabled() && configManager.isUseOwnController()) {
            initializeBankStorage();
        }
        
        // Initialize account membership manager (with storage if available)
        membershipManager = new AccountMembershipManager(this, databaseBankStorage);
        if (databaseBankStorage != null) {
            membershipManager.load();
        }
        
        // Initialize bank controller if banking is enabled (storage must be initialized first)
        if (configManager.isBankingEnabled() && configManager.isUseOwnController() && databaseBankStorage != null) {
            initializeOwnBankController();
        }
        
        // Initialize bank manager (requires ServiceIO if banking is enabled)
        bankManager = new BankManager(this, membershipManager, configManager);
        
        // Initialize region resolver and emerald exchange tracking (optional)
        regionResolver = new WorldGuardRegionResolver(this);
        if (databaseBankStorage != null) {
            emeraldTracker = new EmeraldTracker(databaseBankStorage, regionResolver,
                configManager.getBankConfig().getExchangeConfig());
            bankExchangeService = new BankExchangeService(bankManager, databaseBankStorage, emeraldTracker,
                configManager.getBankConfig().getExchangeConfig().getEmeraldExchangeConfig());
        }
        
        // Initialize data pack manager and install data pack for custom items
        dataPackManager = new DataPackManager(this);
        installDataPack();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new CoinListener(), this);
        if (emeraldTracker != null) {
            getServer().getPluginManager().registerEvents(new EmeraldTrackingListener(emeraldTracker), this);
        }
        
        // Register commands
        registerCommands();
        
        getLogger().info("Coin of the Realm plugin has been enabled!");
        getLogger().info("CustomModelData: " + configManager.getCoinConfig().getCustomModelData());
        getLogger().info("Coin item: " + configManager.getCoinConfig().getItemKey());
        if (bankManager.isBankingEnabled()) {
            getLogger().info("Banking features enabled with " + bankManager.getAccountCount() + " accounts");
        }
    }
    
    /**
     * Initializes bank storage.
     * Storage configuration is global and loaded from config.yml (not bank.yml).
     * This storage is used for all persistence operations including banking,
     * account memberships, exchange tracking, and daily transaction limits.
     */
    private void initializeBankStorage() {
        debug("CoinOfTheRealmPlugin.initializeBankStorage() - Starting storage initialization");
        
        // Storage configuration is global and comes from config.yml
        String storageType = configManager.getBankStorageType();
        if (!"database".equals(storageType)) {
            getLogger().warning("Bank storage type '" + storageType + "' is not yet supported. Using database.");
        }
        
        String databaseType = configManager.getDatabaseType();
        String connectionString = configManager.getDatabaseConnectionString();
        String tablePrefix = configManager.getDatabasePrefix();
        String username = "mysql".equals(databaseType) ? configManager.getDatabaseUsername() : null;
        String password = "mysql".equals(databaseType) ? configManager.getDatabasePassword() : null;
        
        debug("CoinOfTheRealmPlugin.initializeBankStorage() - Database type: {}, connection: {}, prefix: '{}'", 
            databaseType, connectionString, tablePrefix);
        if ("mysql".equals(databaseType)) {
            debug("CoinOfTheRealmPlugin.initializeBankStorage() - MySQL username: '{}', password: {}",
                username != null ? username : "(null)",
                password != null ? (password.isEmpty() ? "(empty)" : "***") : "(null)");
        }
        
        databaseBankStorage = new DatabaseBankStorage(this, databaseType, connectionString, tablePrefix, username, password);
        bankStorage = databaseBankStorage; // Also store as interface
        
        // Initialize storage synchronously (wait for it to complete)
        try {
            databaseBankStorage.initialize().join();
            debug("CoinOfTheRealmPlugin.initializeBankStorage() - Storage initialized");
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize bank storage", e);
            databaseBankStorage = null;
            bankStorage = null;
        }
    }
    
    /**
     * Initializes our own BankController and registers it with ServiceIO.
     */
    private void initializeOwnBankController() {
        debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Starting initialization");
        
        // Check if ServiceIO is available
        org.bukkit.plugin.Plugin serviceIOPlugin = getServer().getPluginManager().getPlugin("ServiceIO");
        if (serviceIOPlugin == null || !serviceIOPlugin.isEnabled()) {
            getLogger().warning("Cannot register BankController: ServiceIO plugin not found or not enabled");
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - ServiceIO not available, skipping registration");
            return;
        }
        
        if (databaseBankStorage == null) {
            getLogger().warning("Cannot register BankController: Bank storage not initialized");
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Storage not available, skipping registration");
            return;
        }
        
        debug("CoinOfTheRealmPlugin.initializeOwnBankController() - ServiceIO found, creating BankController");
        
        // Create BankController instance (storage is already initialized)
        cotrBankController = new CotrBankController(this, databaseBankStorage);
        
        // Create a dynamic proxy that implements BankController interface
        // This allows us to register with ServiceIO without requiring the interface at compile time
        Object bankControllerProxy = org.clockworx.cotr.bank.impl.BankControllerProxy.createProxy(this, cotrBankController);
        
        if (bankControllerProxy == null) {
            getLogger().warning("Failed to create BankController proxy: BankController interface not found");
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Proxy creation failed");
            return;
        }
        
        // Register with ServiceIO using Bukkit's ServicesManager
        try {
            Class<?> bankControllerClass = Class.forName("net.thenextlvl.service.api.economy.bank.BankController");
            
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registering BankController");
            
            // Register with Bukkit's ServicesManager
            // ServicesManager.register(Class<T>, T provider, Plugin, ServicePriority)
            org.bukkit.plugin.ServicesManager servicesManager = getServer().getServicesManager();
            
            // Get the ServicePriority enum
            String priorityStr = configManager.getServicePriority();
            org.bukkit.plugin.ServicePriority priority;
            try {
                priority = org.bukkit.plugin.ServicePriority.valueOf(priorityStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                priority = org.bukkit.plugin.ServicePriority.Normal;
                debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Invalid priority '{}', using Normal", priorityStr);
            }
            
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registering with priority: {}", priority);
            
            // Use unchecked cast since we're using reflection to get the class
            @SuppressWarnings("unchecked")
            Class<Object> serviceClass = (Class<Object>) bankControllerClass;
            servicesManager.register(serviceClass, bankControllerProxy, this, priority);
            
            getLogger().info("Registered Coin of the Realm BankController with ServiceIO (priority: " + priority + ")");
            getLogger().info("Other plugins can now discover and use CotR banking via ServiceIO");
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registration successful");
            
        } catch (ClassNotFoundException e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to register BankController: BankController class not found", e);
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - ClassNotFoundException: {}", e.getMessage());
        } catch (Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to register BankController", e);
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    /**
     * Registers the /cotr command with the server.
     * Uses Paper's programmatic command registration system.
     */
    private void registerCommands() {
        CotrCommand executor = new CotrCommand();
        
        // Create a Command object for Paper plugins
        Command cotrCommand = new Command("cotr") {
            @Override
            public boolean execute(CommandSender sender, String commandLabel, String[] args) {
                return executor.onCommand(sender, this, commandLabel, args);
            }
            
            @Override
            public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                return executor.onTabComplete(sender, this, alias, args);
            }
        };
        
        cotrCommand.setDescription("Coin of the Realm command");
        cotrCommand.setUsage("/<command> <drop|give|deposit|withdraw|balance|rate|request|account|info> [arguments]");
        cotrCommand.setAliases(java.util.Arrays.asList("coin", "coins"));
        cotrCommand.setPermission("cotr.command.use");
        cotrCommand.setPermissionMessage("You do not have permission to use this command.");
        
        // Register the command using CommandMap
        getServer().getCommandMap().register("cotr", cotrCommand);
        
        getLogger().info("Registered /cotr command with Paper command system");
    }
    
    
    @Override
    public void onDisable() {
        // Save account memberships
        if (membershipManager != null) {
            membershipManager.save();
        }
        
        // Shutdown bank storage
        if (databaseBankStorage != null) {
            databaseBankStorage.shutdown().join(); // Wait for shutdown to complete
            debug("CoinOfTheRealmPlugin.onDisable() - Bank storage shut down");
        }
        
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
    
    /**
     * Gets the ConfigManager instance.
     * 
     * @return The ConfigManager
     */
    @NotNull
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    /**
     * Gets the BankManager instance.
     * 
     * @return The BankManager, or null if not initialized
     */
    @Nullable
    public BankManager getBankManager() {
        return bankManager;
    }
    
    /**
     * Gets the AccountMembershipManager instance.
     * 
     * @return The AccountMembershipManager, or null if not initialized
     */
    @Nullable
    public AccountMembershipManager getMembershipManager() {
        return membershipManager;
    }
    
    /**
     * Gets the BankStorage instance.
     * 
     * @return The BankStorage, or null if not initialized
     */
    @Nullable
    public BankStorage getBankStorage() {
        return bankStorage;
    }
    
    /**
     * Gets the DatabaseBankStorage instance.
     * 
     * @return The DatabaseBankStorage, or null if not initialized
     */
    @Nullable
    public DatabaseBankStorage getDatabaseBankStorage() {
        return databaseBankStorage;
    }
    
    /**
     * Gets the CotrBankController instance.
     * 
     * @return The CotrBankController, or null if not initialized
     */
    @Nullable
    public CotrBankController getCotrBankController() {
        return cotrBankController;
    }
    
    /**
     * Gets the WorldGuard region resolver (fallbacks to global if WorldGuard isn't installed).
     *
     * @return The region resolver
     */
    @Nullable
    public WorldGuardRegionResolver getRegionResolver() {
        return regionResolver;
    }
    
    /**
     * Gets the EmeraldTracker instance.
     *
     * @return The EmeraldTracker, or null if storage isn't available
     */
    @Nullable
    public EmeraldTracker getEmeraldTracker() {
        return emeraldTracker;
    }
    
    /**
     * Gets the BankExchangeService instance.
     *
     * @return The BankExchangeService, or null if not initialized
     */
    @Nullable
    public BankExchangeService getBankExchangeService() {
        return bankExchangeService;
    }
    
    /**
     * Installs the data pack for custom item registration.
     * Only installs if the configured item is a custom item (not a vanilla Material).
     */
    private void installDataPack() {
        if (dataPackManager == null || configManager == null) {
            return;
        }
        
        org.clockworx.cotr.config.CoinConfig coinConfig = configManager.getCoinConfig();
        
        // Only install data pack if using a custom item (has NamespacedKey but no Material)
        if (coinConfig.getNamespacedKey() != null && coinConfig.getMaterial() == null) {
            getLogger().info("Installing data pack for custom item: " + coinConfig.getItemKey());
            boolean success = dataPackManager.installDataPack(
                coinConfig,
                coinConfig.getMaxStackSize(),
                coinConfig.getRarity()
            );
            
            if (!success) {
                getLogger().warning("Data pack installation failed. Custom item may not work with /give commands.");
                getLogger().info("You may need to manually install the data pack or restart the server.");
            }
        } else {
            getLogger().info("Using vanilla item: " + coinConfig.getItemKey() + " (data pack not needed)");
        }
    }
    
    /**
     * Logs a debug message if debug mode is enabled.
     * Uses the plugin's logger with FINE level following Paper best practices.
     * 
     * @param message The debug message
     */
    public void debug(String message) {
        if (configManager != null && configManager.isDebugEnabled()) {
            getLogger().log(java.util.logging.Level.FINE, message);
        }
    }
    
    /**
     * Logs a formatted debug message if debug mode is enabled.
     * Uses the plugin's logger with FINE level following Paper best practices.
     * 
     * @param message The debug message format string
     * @param args Arguments for message formatting (will be formatted using String.format)
     */
    public void debug(String message, Object... args) {
        if (configManager != null && configManager.isDebugEnabled()) {
            // Format the message with arguments
            String formattedMessage = String.format(message.replace("{}", "%s"), args);
            getLogger().log(java.util.logging.Level.FINE, formattedMessage);
        }
    }
}
