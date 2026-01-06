package org.clockworx.cotr.bank;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.bank.impl.CotrBankController;
import org.clockworx.cotr.config.ConfigManager;
import org.clockworx.cotr.item.CoinItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * BankManager - Integrates ServiceIO's BankController with AccountMembershipManager
 * 
 * This class provides a unified interface for banking operations that supports:
 * - Many-to-many relationships (players ↔ accounts) via AccountMembershipManager
 * - Bank operations via ServiceIO's BankController
 * - Conversion between physical coins (ItemStacks) and bank balances (integers)
 * 
 * Note: Internally uses integers for all coin amounts (no fractional coins).
 * Converts to/from BigDecimal only when interacting with ServiceIO's BankController API.
 * 
 * The BankManager acts as a bridge between:
 * - BankController (one owner per bank, handles persistence)
 * - AccountMembershipManager (many-to-many relationships, our custom layer)
 * 
 * All bank operations check AccountMembershipManager for access control before
 * performing operations on BankController.
 */
public class BankManager {
    
    private final CoinOfTheRealmPlugin plugin;
    private final AccountMembershipManager membershipManager;
    private final ConfigManager configManager;
    private Object bankController; // Using Object to avoid class loading issues
    private boolean bankingEnabled;
    
    /**
     * Creates a new BankManager.
     * 
     * @param plugin The plugin instance
     * @param membershipManager The account membership manager
     * @param configManager The configuration manager
     */
    public BankManager(@NotNull CoinOfTheRealmPlugin plugin, 
                      @NotNull AccountMembershipManager membershipManager,
                      @NotNull ConfigManager configManager) {
        this.plugin = plugin;
        this.membershipManager = membershipManager;
        this.configManager = configManager;
        initializeBankController();
    }
    
    /**
     * Initializes the BankController from ServiceIO.
     * Checks if ServiceIO is available and banking is enabled in config.
     * Uses reflection to avoid ClassNotFoundException when ServiceIO isn't installed.
     */
    private void initializeBankController() {
        plugin.debug("BankManager.initializeBankController() - Starting BankController initialization");
        
        if (!configManager.isBankingEnabled()) {
            bankingEnabled = false;
            plugin.getLogger().info("Banking is disabled in config.yml");
            plugin.debug("BankManager.initializeBankController() - Banking disabled in config, aborting");
            return;
        }
        
        plugin.debug("BankManager.initializeBankController() - Banking enabled in config, checking for ServiceIO plugin");
        
        // First, check if ServiceIO plugin exists
        org.bukkit.plugin.Plugin serviceIOPlugin = plugin.getServer().getPluginManager().getPlugin("ServiceIO");
        if (serviceIOPlugin == null || !serviceIOPlugin.isEnabled()) {
            bankingEnabled = false;
            plugin.getLogger().info("ServiceIO plugin not found or not enabled. Banking features disabled.");
            plugin.getLogger().info("Install and enable ServiceIO plugin (v2.3.1+) to enable banking features.");
            plugin.getLogger().info("Download: https://github.com/TheNextLvl-net/service-io");
            plugin.debug("BankManager.initializeBankController() - ServiceIO plugin not found or disabled. serviceIOPlugin={}", serviceIOPlugin);
            return;
        }
        
        String serviceIOVersion = serviceIOPlugin.getDescription().getVersion();
        plugin.debug("BankManager.initializeBankController() - ServiceIO plugin found: enabled={}, version={}", 
            serviceIOPlugin.isEnabled(), 
            serviceIOVersion);
        
        plugin.getLogger().info("ServiceIO plugin detected (v" + serviceIOVersion + "). Discovering BankController service...");
        plugin.debug("BankManager.initializeBankController() - Starting ServiceIO service discovery process");
        
        try {
            // Try to load the BankController class
            String className = "net.thenextlvl.service.api.economy.bank.BankController";
            plugin.debug("BankManager.initializeBankController() - Attempting to load class: {}", className);
            
            Class<?> bankControllerClass = Class.forName(className);
            plugin.getLogger().info("BankController class found: " + bankControllerClass.getName());
            plugin.debug("BankManager.initializeBankController() - Class loaded successfully: {}", bankControllerClass.getName());
            
            // First, check if our own BankController is available and should be used
            if (configManager.isUseOwnController()) {
                CotrBankController ourController = plugin.getCotrBankController();
                if (ourController != null) {
                    plugin.debug("BankManager.initializeBankController() - Using our own BankController");
                    bankController = ourController;
                    bankingEnabled = true;
                    plugin.getLogger().info("Using Coin of the Realm's built-in BankController");
                    plugin.debug("BankManager.initializeBankController() - Our BankController initialized successfully");
                    return;
                } else {
                    plugin.debug("BankManager.initializeBankController() - Our BankController not yet initialized, will check external controllers");
                }
            }
            
            // Try to get an external BankController from ServicesManager
            plugin.debug("BankManager.initializeBankController() - Querying ServicesManager for external BankController");
            bankController = plugin.getServer().getServicesManager().load(bankControllerClass);
            plugin.debug("BankManager.initializeBankController() - ServicesManager.load() returned: {}", bankController != null ? "non-null" : "null");
            
            if (bankController == null) {
                // Try alternative: get provider directly
                plugin.debug("BankManager.initializeBankController() - ServicesManager.load() returned null, trying getRegistrations()");
                try {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<org.bukkit.plugin.RegisteredServiceProvider<?>> providers = 
                        (java.util.Collection<org.bukkit.plugin.RegisteredServiceProvider<?>>) 
                        (java.util.Collection<?>) plugin.getServer().getServicesManager().getRegistrations(bankControllerClass);
                    int providerCount = providers != null ? providers.size() : 0;
                    plugin.debug("BankManager.initializeBankController() - getRegistrations() returned {} providers", providerCount);
                    
                    if (providerCount > 0) {
                        // Log all available providers for debugging
                        plugin.debug("BankManager.initializeBankController() - Available BankController providers:");
                        for (org.bukkit.plugin.RegisteredServiceProvider<?> p : providers) {
                            plugin.debug("  - Plugin: {}, Priority: {}", p.getPlugin().getName(), p.getPriority());
                        }
                        
                        // If we want to use our own controller, check if it's in the list
                        if (configManager.isUseOwnController()) {
                            CotrBankController ourController = plugin.getCotrBankController();
                            for (org.bukkit.plugin.RegisteredServiceProvider<?> p : providers) {
                                if (p.getProvider() == ourController) {
                                    plugin.debug("BankManager.initializeBankController() - Found our own BankController in providers, using it");
                                    bankController = ourController;
                                    bankingEnabled = true;
                                    plugin.getLogger().info("Using Coin of the Realm's built-in BankController");
                                    return;
                                }
                            }
                        }
                        
                        // Get the highest priority provider (external controller)
                        org.bukkit.plugin.RegisteredServiceProvider<?> provider = providers.iterator().next();
                        bankController = provider.getProvider();
                        plugin.getLogger().info("Found BankController via provider: " + provider.getPlugin().getName() + " (priority: " + provider.getPriority() + ")");
                        plugin.debug("BankManager.initializeBankController() - Got BankController from provider: plugin={}, priority={}", 
                            provider.getPlugin().getName(), provider.getPriority());
                    } else {
                        plugin.debug("BankManager.initializeBankController() - No providers found in registration list");
                        plugin.debug("BankManager.initializeBankController() - This means no plugin has registered a BankController with ServiceIO");
                    }
                } catch (Exception e2) {
                    plugin.getLogger().warning("Failed to query BankController providers: " + e2.getMessage());
                    plugin.debug("BankManager.initializeBankController() - Exception getting provider: {} - {}", e2.getClass().getSimpleName(), e2.getMessage());
                }
            }
            
            if (bankController == null) {
                bankingEnabled = false;
                // ServiceIO is present but no BankController is registered
                plugin.getLogger().info("ServiceIO plugin is installed and enabled, but no BankController service is registered.");
                plugin.getLogger().info("Banking features are currently disabled.");
                plugin.getLogger().info("");
                plugin.getLogger().info("To enable banking, you have two options:");
                plugin.getLogger().info("1. Install a plugin that provides a BankController implementation (e.g., an economy plugin with banking support)");
                plugin.getLogger().info("2. Enable Coin of the Realm's built-in BankController in config.yml (banking.use-own-controller: true)");
                plugin.getLogger().info("");
                plugin.getLogger().info("Note: ServiceIO acts as a service registry. Plugins register their BankController");
                plugin.getLogger().info("implementations with ServiceIO, allowing other plugins to discover and use them.");
                plugin.debug("BankManager.initializeBankController() - BankController is null after all attempts, banking disabled");
            } else {
                bankingEnabled = true;
                org.bukkit.plugin.Plugin providerPlugin = null;
                try {
                    // Try to identify which plugin provided the BankController
                    @SuppressWarnings("unchecked")
                    java.util.Collection<org.bukkit.plugin.RegisteredServiceProvider<?>> providers = 
                        (java.util.Collection<org.bukkit.plugin.RegisteredServiceProvider<?>>) 
                        (java.util.Collection<?>) plugin.getServer().getServicesManager().getRegistrations(bankControllerClass);
                    if (providers != null && !providers.isEmpty()) {
                        providerPlugin = providers.iterator().next().getPlugin();
                    }
                } catch (Exception e) {
                    plugin.debug("BankManager.initializeBankController() - Could not identify BankController provider: {}", e.getMessage());
                }
                
                if (providerPlugin != null) {
                    plugin.getLogger().info("BankController loaded successfully from plugin: " + providerPlugin.getName() + " (v" + providerPlugin.getDescription().getVersion() + ")");
                    plugin.getLogger().info("Banking features enabled.");
                } else {
                    plugin.getLogger().info("BankController loaded successfully. Banking features enabled.");
                }
                plugin.debug("BankManager.initializeBankController() - BankController initialized successfully, banking enabled");
            }
        } catch (ClassNotFoundException e) {
            bankingEnabled = false;
            plugin.getLogger().warning("ServiceIO BankController class not found: " + e.getMessage());
            plugin.getLogger().warning("This may indicate a version mismatch. Ensure ServiceIO version 2.3.1+ is installed.");
            plugin.debug("BankManager.initializeBankController() - ClassNotFoundException: {}", e.getMessage());
        } catch (Exception e) {
            bankingEnabled = false;
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error loading ServiceIO BankController", e);
            plugin.debug("BankManager.initializeBankController() - Exception during initialization: {} - {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    /**
     * Checks if banking is enabled and available.
     * 
     * @return true if banking is enabled and BankController is available
     */
    public boolean isBankingEnabled() {
        return bankingEnabled && bankController != null;
    }
    
    /**
     * Gets the BankController instance.
     * 
     * @return The BankController, or null if not available
     */
    @Nullable
    public Object getBankController() {
        return bankController;
    }
    
    /**
     * Creates a new account for a player.
     * Creates both the BankController bank and the membership entry.
     * 
     * @param owner The player who will own the account
     * @param accountName The name for the account (must be unique)
     * @return A CompletableFuture that completes with true if created, false if already exists
     */
    @NotNull
    public CompletableFuture<Boolean> createAccount(@NotNull Player owner, @NotNull String accountName) {
        plugin.debug("BankManager.createAccount() - owner={}, accountName={}", owner.getName(), accountName);
        
        if (!isBankingEnabled()) {
            plugin.debug("BankManager.createAccount() - Banking not enabled, returning false");
            return CompletableFuture.completedFuture(false);
        }
        
        // Check if account already exists in membership system
        int existingMemberships = membershipManager.getAccountMemberships(accountName).size();
        plugin.debug("BankManager.createAccount() - Existing memberships for account '{}': {}", accountName, existingMemberships);
        
        if (existingMemberships > 0) {
            plugin.debug("BankManager.createAccount() - Account '{}' already exists, returning false", accountName);
            return CompletableFuture.completedFuture(false);
        }
        
        // Create membership entry first
        plugin.debug("BankManager.createAccount() - Creating membership entry for account '{}'", accountName);
        boolean membershipCreated = membershipManager.createAccount(accountName, owner.getUniqueId());
        plugin.debug("BankManager.createAccount() - Membership creation result: {}", membershipCreated);
        
        if (!membershipCreated) {
            plugin.debug("BankManager.createAccount() - Membership creation failed, returning false");
            return CompletableFuture.completedFuture(false);
        }
        
        // Create bank in BankController (owner is the player UUID)
        plugin.debug("BankManager.createAccount() - Creating bank via BankReflectionHelper for account '{}'", accountName);
        return BankReflectionHelper.createBank(bankController, owner.getUniqueId(), accountName)
            .thenApply(bank -> {
                if (bank != null) {
                    plugin.getLogger().info("Created account '" + accountName + "' for player " + owner.getName());
                    plugin.debug("BankManager.createAccount() - Bank created successfully for account '{}'", accountName);
                    return true;
                }
                // Rollback membership if bank creation failed
                plugin.debug("BankManager.createAccount() - Bank creation returned null, rolling back membership");
                membershipManager.deleteAccount(accountName);
                return false;
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create bank account '" + accountName + "'", ex);
                plugin.debug("BankManager.createAccount() - Exception during bank creation: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
                // Rollback membership
                membershipManager.deleteAccount(accountName);
                return false;
            });
    }
    
    /**
     * Gets a bank account if the player has access to it.
     * 
     * @param player The player requesting access
     * @param accountName The account name
     * @return A CompletableFuture that completes with the Bank (as Object), or null if no access
     */
    @NotNull
    public CompletableFuture<Object> getAccount(@NotNull Player player, @NotNull String accountName) {
        plugin.debug("BankManager.getAccount() - player={}, accountName={}", player.getName(), accountName);
        
        if (!isBankingEnabled()) {
            plugin.debug("BankManager.getAccount() - Banking not enabled, returning null");
            return CompletableFuture.completedFuture(null);
        }
        
        // Check access via membership manager
        boolean hasAccess = membershipManager.hasAccess(player.getUniqueId(), accountName);
        plugin.debug("BankManager.getAccount() - Access check result: {}", hasAccess);
        
        if (!hasAccess) {
            plugin.debug("BankManager.getAccount() - Player {} does not have access to account '{}'", player.getName(), accountName);
            return CompletableFuture.completedFuture(null);
        }
        
        plugin.debug("BankManager.getAccount() - Loading bank via BankReflectionHelper for account '{}'", accountName);
        return BankReflectionHelper.loadBank(bankController, accountName)
            .thenApply(bank -> {
                plugin.debug("BankManager.getAccount() - Bank loaded: {}", bank != null ? "success" : "null");
                return bank;
            })
            .exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load bank account '" + accountName + "'", ex);
                plugin.debug("BankManager.getAccount() - Exception loading bank: {} - {}", ex.getClass().getSimpleName(), ex.getMessage());
                return null;
            });
    }
    
    /**
     * Gets all accounts a player has access to.
     * 
     * @param player The player
     * @return A set of account names
     */
    @NotNull
    public Set<String> getPlayerAccounts(@NotNull Player player) {
        return membershipManager.getAccountsForPlayer(player.getUniqueId());
    }
    
    /**
     * Gets or creates the player's default account.
     * 
     * @param player The player
     * @return A CompletableFuture that completes with the default account name
     */
    @NotNull
    public CompletableFuture<String> getDefaultAccount(@NotNull Player player) {
        String pattern = configManager.getDefaultAccountPattern();
        String accountName = pattern.replace("{player-uuid}", player.getUniqueId().toString());
        
        Set<String> accounts = getPlayerAccounts(player);
        if (accounts.contains(accountName)) {
            return CompletableFuture.completedFuture(accountName);
        }
        
        // Create default account if it doesn't exist
        return createAccount(player, accountName)
            .thenApply(success -> success ? accountName : null);
    }
    
    /**
     * Gets the balance of an account if the player has access.
     * 
     * @param player The player requesting the balance
     * @param accountName The account name
     * @return A CompletableFuture that completes with the balance as Integer (number of coins), or null if no access
     */
    @NotNull
    public CompletableFuture<Integer> getBalance(@NotNull Player player, @NotNull String accountName) {
        plugin.debug("BankManager.getBalance() - player={}, accountName={}", player.getName(), accountName);
        
        return getAccount(player, accountName)
            .thenApply(bank -> {
                if (bank == null) {
                    plugin.debug("BankManager.getBalance() - Bank is null, returning null");
                    return null;
                }
                BigDecimal balance = BankReflectionHelper.getBalance(bank);
                int balanceInt = balance != null ? balance.intValue() : 0;
                plugin.debug("BankManager.getBalance() - Balance retrieved: {} (BigDecimal: {})", balanceInt, balance);
                // Convert BigDecimal to int (round to nearest integer)
                return balance != null ? balance.intValue() : null;
            });
    }
    
    /**
     * Deposits coins from a player's inventory to an account.
     * 
     * @param player The player depositing
     * @param accountName The account name (or null for default)
     * @param amount The amount to deposit (in coins, integer)
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> deposit(@NotNull Player player, @Nullable String accountName, int amount) {
        plugin.debug("BankManager.deposit() - player={}, accountName={}, amount={}", player.getName(), accountName, amount);
        
        if (!isBankingEnabled()) {
            plugin.debug("BankManager.deposit() - Banking not enabled, returning false");
            return CompletableFuture.completedFuture(false);
        }
        
        // Get account name (default if null)
        CompletableFuture<String> accountFuture = accountName != null ?
            CompletableFuture.completedFuture(accountName) :
            getDefaultAccount(player);
        
        return accountFuture.thenCompose(accName -> {
            plugin.debug("BankManager.deposit() - Resolved account name: {}", accName);
            
            if (accName == null) {
                plugin.debug("BankManager.deposit() - Account name is null, returning false");
                return CompletableFuture.completedFuture(false);
            }
            
            // Check access
            boolean hasAccess = membershipManager.hasAccess(player.getUniqueId(), accName);
            plugin.debug("BankManager.deposit() - Access check: {}", hasAccess);
            
            if (!hasAccess) {
                plugin.debug("BankManager.deposit() - Player {} does not have access to account '{}'", player.getName(), accName);
                return CompletableFuture.completedFuture(false);
            }
            
            // Count coins in inventory
            int coinsInInventory = countCoinsInInventory(player);
            plugin.debug("BankManager.deposit() - Coins in inventory: {}, required: {}", coinsInInventory, amount);
            
            if (coinsInInventory < amount) {
                plugin.debug("BankManager.deposit() - Insufficient coins in inventory");
                return CompletableFuture.completedFuture(false);
            }
            
            // Remove coins from inventory
            plugin.debug("BankManager.deposit() - Removing {} coins from inventory", amount);
            removeCoinsFromInventory(player, amount);
            
            // Get bank and deposit
            return getAccount(player, accName)
                .thenCompose(bank -> {
                    if (bank == null) {
                        plugin.debug("BankManager.deposit() - Bank is null, refunding coins");
                        // Refund coins if bank access failed
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal depositAmount = BigDecimal.valueOf(amount);
                    plugin.debug("BankManager.deposit() - Depositing {} (BigDecimal) to bank", depositAmount);
                    BigDecimal newBalance = BankReflectionHelper.deposit(bank, depositAmount);
                    
                    if (newBalance == null) {
                        plugin.debug("BankManager.deposit() - Deposit returned null, refunding coins");
                        // Refund coins if deposit failed
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    plugin.debug("BankManager.deposit() - Deposit successful, new balance: {}", newBalance);
                    return CompletableFuture.completedFuture(true);
                });
        });
    }
    
    /**
     * Withdraws coins from an account to a player's inventory.
     * 
     * @param player The player withdrawing
     * @param accountName The account name (or null for default)
     * @param amount The amount to withdraw (in coins, integer)
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> withdraw(@NotNull Player player, @Nullable String accountName, int amount) {
        plugin.debug("BankManager.withdraw() - player={}, accountName={}, amount={}", player.getName(), accountName, amount);
        
        if (!isBankingEnabled()) {
            plugin.debug("BankManager.withdraw() - Banking not enabled, returning false");
            return CompletableFuture.completedFuture(false);
        }
        
        // Get account name (default if null)
        CompletableFuture<String> accountFuture = accountName != null ?
            CompletableFuture.completedFuture(accountName) :
            getDefaultAccount(player);
        
        return accountFuture.thenCompose(accName -> {
            plugin.debug("BankManager.withdraw() - Resolved account name: {}", accName);
            
            if (accName == null) {
                plugin.debug("BankManager.withdraw() - Account name is null, returning false");
                return CompletableFuture.completedFuture(false);
            }
            
            // Check access
            boolean hasAccess = membershipManager.hasAccess(player.getUniqueId(), accName);
            plugin.debug("BankManager.withdraw() - Access check: {}", hasAccess);
            
            if (!hasAccess) {
                plugin.debug("BankManager.withdraw() - Player {} does not have access to account '{}'", player.getName(), accName);
                return CompletableFuture.completedFuture(false);
            }
            
            // Get bank and check balance
            return getAccount(player, accName)
                .thenCompose(bank -> {
                    if (bank == null) {
                        plugin.debug("BankManager.withdraw() - Bank is null, returning false");
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert BigDecimal balance to int for comparison
                    BigDecimal balance = BankReflectionHelper.getBalance(bank);
                    int balanceInt = balance != null ? balance.intValue() : 0;
                    plugin.debug("BankManager.withdraw() - Current balance: {} (BigDecimal: {})", balanceInt, balance);
                    
                    if (balanceInt < amount) {
                        plugin.debug("BankManager.withdraw() - Insufficient balance: {} < {}", balanceInt, amount);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal withdrawAmount = BigDecimal.valueOf(amount);
                    plugin.debug("BankManager.withdraw() - Withdrawing {} (BigDecimal) from bank", withdrawAmount);
                    BigDecimal newBalance = BankReflectionHelper.withdraw(bank, withdrawAmount);
                    
                    if (newBalance != null) {
                        plugin.debug("BankManager.withdraw() - Withdrawal successful, new balance: {}, giving {} coins to player", newBalance, amount);
                        // Give coins to player
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(true);
                    }
                    
                    plugin.debug("BankManager.withdraw() - Withdrawal returned null, returning false");
                    return CompletableFuture.completedFuture(false);
                });
        });
    }
    
    /**
     * Transfers funds between two accounts.
     * 
     * @param from The player initiating the transfer
     * @param fromAccount The source account name
     * @param to The target player
     * @param toAccount The target account name (or null for default)
     * @param amount The amount to transfer (in coins, integer)
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> transfer(@NotNull Player from, @NotNull String fromAccount,
                                               @NotNull Player to, @Nullable String toAccount,
                                               int amount) {
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Check access to source account
        if (!membershipManager.hasAccess(from.getUniqueId(), fromAccount)) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Get target account name
        CompletableFuture<String> toAccountFuture = toAccount != null ?
            CompletableFuture.completedFuture(toAccount) :
            getDefaultAccount(to);
        
        return toAccountFuture.thenCompose(targetAccount -> {
            if (targetAccount == null) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Get both banks
            CompletableFuture<Object> fromBankFuture = getAccount(from, fromAccount);
            CompletableFuture<Object> toBankFuture = getAccount(to, targetAccount);
            
            return CompletableFuture.allOf(fromBankFuture, toBankFuture)
                .thenCompose(v -> {
                    Object fromBank = fromBankFuture.join();
                    Object toBank = toBankFuture.join();
                    
                    if (fromBank == null || toBank == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert BigDecimal balance to int for comparison
                    BigDecimal fromBalance = BankReflectionHelper.getBalance(fromBank);
                    int fromBalanceInt = fromBalance != null ? fromBalance.intValue() : 0;
                    if (fromBalanceInt < amount) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal transferAmount = BigDecimal.valueOf(amount);
                    
                    // Withdraw from source
                    BigDecimal newFromBalance = BankReflectionHelper.withdraw(fromBank, transferAmount);
                    if (newFromBalance == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Deposit to target
                    BigDecimal newToBalance = BankReflectionHelper.deposit(toBank, transferAmount);
                    return CompletableFuture.completedFuture(newToBalance != null);
                });
        });
    }
    
    /**
     * Adds a member to an account (owner only).
     * 
     * @param owner The account owner
     * @param accountName The account name
     * @param newMember The player to add
     * @param role The role to assign
     * @return true if the member was added
     */
    public boolean addMember(@NotNull Player owner, @NotNull String accountName, 
                            @NotNull Player newMember, @NotNull AccountRole role) {
        if (!isBankingEnabled()) {
            return false;
        }
        
        // Check if owner has OWNER role
        AccountRole ownerRole = membershipManager.getRole(owner.getUniqueId(), accountName);
        if (ownerRole != AccountRole.OWNER) {
            return false;
        }
        
        return membershipManager.addMember(accountName, newMember.getUniqueId(), role);
    }
    
    /**
     * Removes a member from an account (owner only).
     * 
     * @param owner The account owner
     * @param accountName The account name
     * @param member The player to remove
     * @return true if the member was removed
     */
    public boolean removeMember(@NotNull Player owner, @NotNull String accountName, @NotNull Player member) {
        if (!isBankingEnabled()) {
            return false;
        }
        
        // Check if owner has OWNER role
        AccountRole ownerRole = membershipManager.getRole(owner.getUniqueId(), accountName);
        if (ownerRole != AccountRole.OWNER) {
            return false;
        }
        
        return membershipManager.removeMember(accountName, member.getUniqueId());
    }
    
    /**
     * Gets all members of an account.
     * 
     * @param accountName The account name
     * @return A set of AccountMembership objects
     */
    @NotNull
    public Set<AccountMembership> getAccountMembers(@NotNull String accountName) {
        return membershipManager.getAccountMemberships(accountName);
    }
    
    /**
     * Deletes an account (owner only).
     * 
     * @param owner The account owner
     * @param accountName The account name
     * @return A CompletableFuture that completes with true if deleted
     */
    @NotNull
    public CompletableFuture<Boolean> deleteAccount(@NotNull Player owner, @NotNull String accountName) {
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Check if owner has OWNER role
        AccountRole ownerRole = membershipManager.getRole(owner.getUniqueId(), accountName);
        if (ownerRole != AccountRole.OWNER) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Delete from BankController
        return BankReflectionHelper.loadBank(bankController, accountName)
            .thenCompose(bank -> {
                if (bank == null) {
                    // Bank doesn't exist, just remove membership
                    membershipManager.deleteAccount(accountName);
                    return CompletableFuture.completedFuture(true);
                }
                
                return BankReflectionHelper.deleteBank(bankController, bank)
                    .thenApply(success -> {
                        if (success) {
                            membershipManager.deleteAccount(accountName);
                        }
                        return success;
                    });
            });
    }
    
    /**
     * Gets the total number of accounts.
     * 
     * @return The account count
     */
    public int getAccountCount() {
        return membershipManager.getAccountCount();
    }
    
    /**
     * Counts the total number of coins in a player's inventory.
     * 
     * @param player The player
     * @return The total number of coins
     */
    private int countCoinsInInventory(@NotNull Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CoinItem.isCoin(item)) {
                total += item.getAmount();
            }
        }
        return total;
    }
    
    /**
     * Removes coins from a player's inventory.
     * 
     * @param player The player
     * @param amount The number of coins to remove
     */
    private void removeCoinsFromInventory(@NotNull Player player, int amount) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CoinItem.isCoin(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= amount) {
                    player.getInventory().removeItem(item);
                    amount -= itemAmount;
                } else {
                    item.setAmount(itemAmount - amount);
                    amount = 0;
                }
                if (amount <= 0) {
                    break;
                }
            }
        }
    }
    
    /**
     * Gives coins to a player, handling inventory overflow.
     * 
     * @param player The player
     * @param amount The number of coins to give
     */
    private void giveCoinsToPlayer(@NotNull Player player, int amount) {
        while (amount > 0) {
            int stackSize = Math.min(amount, 64);
            ItemStack coin = CoinItem.createCoin(stackSize);
            
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(coin);
            if (!overflow.isEmpty()) {
                // Drop overflow on ground
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            amount -= stackSize;
        }
    }
}
