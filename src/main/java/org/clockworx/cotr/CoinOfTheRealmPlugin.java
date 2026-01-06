package org.clockworx.cotr;

import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.clockworx.cotr.bank.AccountMembershipManager;
import org.clockworx.cotr.bank.BankManager;
import org.clockworx.cotr.bank.impl.CotrBankController;
import org.clockworx.cotr.bank.storage.BankStorage;
import org.clockworx.cotr.bank.storage.DatabaseBankStorage;
import org.clockworx.cotr.command.CotrCommand;
import org.clockworx.cotr.config.ConfigManager;
import org.clockworx.cotr.datapack.DataPackManager;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.listener.CoinListener;
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
        
        // Initialize data pack manager and install data pack for custom items
        dataPackManager = new DataPackManager(this);
        installDataPack();
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new CoinListener(), this);
        
        // Register commands
        registerCommands();
        
        // Start a task to handle proximity-based pickup for coin displays
        // This allows players to pick up coins by walking near them
        startCoinPickupTask();
        
        getLogger().info("Coin of the Realm plugin has been enabled!");
        getLogger().info("CustomModelData: " + configManager.getCoinConfig().getCustomModelData());
        getLogger().info("Coin item: " + configManager.getCoinConfig().getItemKey());
        if (bankManager.isBankingEnabled()) {
            getLogger().info("Banking features enabled with " + bankManager.getAccountCount() + " accounts");
        }
    }
    
    /**
     * Initializes bank storage.
     */
    private void initializeBankStorage() {
        debug("CoinOfTheRealmPlugin.initializeBankStorage() - Starting storage initialization");
        
        String storageType = configManager.getBankStorageType();
        if (!"database".equals(storageType)) {
            getLogger().warning("Bank storage type '" + storageType + "' is not yet supported. Using database.");
        }
        
        String databaseType = configManager.getDatabaseType();
        String connectionString = configManager.getDatabaseConnectionString();
        String tablePrefix = configManager.getDatabasePrefix();
        
        debug("CoinOfTheRealmPlugin.initializeBankStorage() - Database type: {}, connection: {}, prefix: '{}'", 
            databaseType, connectionString, tablePrefix);
        
        databaseBankStorage = new DatabaseBankStorage(this, databaseType, connectionString, tablePrefix);
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
        
        // Register with ServiceIO (synchronously)
        try {
            Class<?> bankControllerClass = Class.forName("net.thenextlvl.service.api.economy.bank.BankController");
            
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registering BankController");
            
            // Register with ServiceIO using reflection to avoid type issues
            org.bukkit.plugin.ServicesManager servicesManager = getServer().getServicesManager();
            
            debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registering BankController with ServiceIO");
            
            // Use reflection to call register method
            try {
                // Try the 3-parameter version first (Class, Object, Plugin)
                java.lang.reflect.Method registerMethod = servicesManager.getClass().getMethod(
                    "register",
                    Class.class,
                    Object.class,
                    org.bukkit.plugin.Plugin.class
                );
                registerMethod.invoke(servicesManager, bankControllerClass, cotrBankController, this);
                debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registered using 3-parameter method");
            } catch (NoSuchMethodException e) {
                // Try 4-parameter version with priority (if it exists)
                try {
                    // Try to find PluginPriority enum
                    Class<?> priorityClass = Class.forName("org.bukkit.plugin.PluginPriority");
                    java.lang.reflect.Method registerMethod = servicesManager.getClass().getMethod(
                        "register",
                        Class.class,
                        Object.class,
                        org.bukkit.plugin.Plugin.class,
                        priorityClass
                    );
                    // Get NORMAL priority
                    Object[] priorities = priorityClass.getEnumConstants();
                    Object normalPriority = priorities[2]; // NORMAL is typically index 2
                    registerMethod.invoke(servicesManager, bankControllerClass, cotrBankController, this, normalPriority);
                    debug("CoinOfTheRealmPlugin.initializeOwnBankController() - Registered using 4-parameter method with priority");
                } catch (Exception e2) {
                    throw new RuntimeException("Failed to register BankController: No suitable register method found", e2);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to register BankController: " + e.getMessage(), e);
            }
            
            getLogger().info("Registered Coin of the Realm BankController with ServiceIO");
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
        cotrCommand.setUsage("/<command> <drop|give> [arguments]");
        cotrCommand.setAliases(java.util.Arrays.asList("coin", "coins"));
        cotrCommand.setPermission("cotr.command.use");
        cotrCommand.setPermissionMessage("You do not have permission to use this command.");
        
        // Register the command using CommandMap
        getServer().getCommandMap().register("cotr", cotrCommand);
        
        getLogger().info("Registered /cotr command with Paper command system");
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
