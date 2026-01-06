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
import org.clockworx.cotr.command.CotrCommand;
import org.clockworx.cotr.config.ConfigManager;
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
        
        // Initialize account membership manager
        membershipManager = new AccountMembershipManager(this);
        membershipManager.load();
        
        // Initialize bank manager (requires ServiceIO if banking is enabled)
        bankManager = new BankManager(this, membershipManager, configManager);
        
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
