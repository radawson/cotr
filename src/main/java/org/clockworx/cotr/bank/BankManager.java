package org.clockworx.cotr.bank;

import net.thenextlvl.service.api.economy.bank.Bank;
import net.thenextlvl.service.api.economy.bank.BankController;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.config.ConfigManager;
import org.clockworx.cotr.item.CoinItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private BankController bankController;
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
     */
    private void initializeBankController() {
        if (!configManager.isBankingEnabled()) {
            bankingEnabled = false;
            plugin.getLogger().info("Banking is disabled in config.yml");
            return;
        }
        
        bankController = plugin.getServer().getServicesManager().load(BankController.class);
        
        if (bankController == null) {
            bankingEnabled = false;
            plugin.getLogger().warning("ServiceIO BankController not found. Banking features disabled.");
            plugin.getLogger().warning("Install ServiceIO plugin to enable banking features.");
        } else {
            bankingEnabled = true;
            plugin.getLogger().info("BankController loaded successfully. Banking features enabled.");
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
    public BankController getBankController() {
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
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Check if account already exists in membership system
        if (membershipManager.getAccountMemberships(accountName).size() > 0) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Create membership entry first
        boolean membershipCreated = membershipManager.createAccount(accountName, owner.getUniqueId());
        if (!membershipCreated) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Create bank in BankController (owner is the player UUID)
        return bankController.createBank(owner.getUniqueId(), accountName)
            .thenApply(bank -> {
                if (bank != null) {
                    plugin.getLogger().info("Created account '" + accountName + "' for player " + owner.getName());
                    return true;
                }
                // Rollback membership if bank creation failed
                membershipManager.deleteAccount(accountName);
                return false;
            })
            .exceptionally(ex -> {
                plugin.getLogger().warning("Failed to create bank account '" + accountName + "': " + ex.getMessage());
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
     * @return A CompletableFuture that completes with the Bank, or null if no access
     */
    @NotNull
    public CompletableFuture<Bank> getAccount(@NotNull Player player, @NotNull String accountName) {
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        
        // Check access via membership manager
        if (!membershipManager.hasAccess(player.getUniqueId(), accountName)) {
            return CompletableFuture.completedFuture(null);
        }
        
        return bankController.loadBank(accountName)
            .exceptionally(ex -> {
                plugin.getLogger().warning("Failed to load bank account '" + accountName + "': " + ex.getMessage());
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
        return getAccount(player, accountName)
            .thenApply(bank -> {
                if (bank == null) {
                    return null;
                }
                BigDecimal balance = bank.getBalance();
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
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Get account name (default if null)
        CompletableFuture<String> accountFuture = accountName != null ?
            CompletableFuture.completedFuture(accountName) :
            getDefaultAccount(player);
        
        return accountFuture.thenCompose(accName -> {
            if (accName == null) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Check access
            if (!membershipManager.hasAccess(player.getUniqueId(), accName)) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Count coins in inventory
            int coinsInInventory = countCoinsInInventory(player);
            if (coinsInInventory < amount) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Remove coins from inventory
            removeCoinsFromInventory(player, amount);
            
            // Get bank and deposit
            return getAccount(player, accName)
                .thenCompose(bank -> {
                    if (bank == null) {
                        // Refund coins if bank access failed
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal depositAmount = BigDecimal.valueOf(amount);
                    BigDecimal newBalance = bank.deposit(depositAmount);
                    if (newBalance == null) {
                        // Refund coins if deposit failed
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(false);
                    }
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
        if (!isBankingEnabled()) {
            return CompletableFuture.completedFuture(false);
        }
        
        // Get account name (default if null)
        CompletableFuture<String> accountFuture = accountName != null ?
            CompletableFuture.completedFuture(accountName) :
            getDefaultAccount(player);
        
        return accountFuture.thenCompose(accName -> {
            if (accName == null) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Check access
            if (!membershipManager.hasAccess(player.getUniqueId(), accName)) {
                return CompletableFuture.completedFuture(false);
            }
            
            // Get bank and check balance
            return getAccount(player, accName)
                .thenCompose(bank -> {
                    if (bank == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert BigDecimal balance to int for comparison
                    BigDecimal balance = bank.getBalance();
                    int balanceInt = balance != null ? balance.intValue() : 0;
                    if (balanceInt < amount) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal withdrawAmount = BigDecimal.valueOf(amount);
                    BigDecimal newBalance = bank.withdraw(withdrawAmount);
                    if (newBalance != null) {
                        // Give coins to player
                        giveCoinsToPlayer(player, amount);
                        return CompletableFuture.completedFuture(true);
                    }
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
            CompletableFuture<Bank> fromBankFuture = getAccount(from, fromAccount);
            CompletableFuture<Bank> toBankFuture = getAccount(to, targetAccount);
            
            return CompletableFuture.allOf(fromBankFuture, toBankFuture)
                .thenCompose(v -> {
                    Bank fromBank = fromBankFuture.join();
                    Bank toBank = toBankFuture.join();
                    
                    if (fromBank == null || toBank == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert BigDecimal balance to int for comparison
                    BigDecimal fromBalance = fromBank.getBalance();
                    int fromBalanceInt = fromBalance != null ? fromBalance.intValue() : 0;
                    if (fromBalanceInt < amount) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Convert int to BigDecimal for API call
                    BigDecimal transferAmount = BigDecimal.valueOf(amount);
                    
                    // Withdraw from source
                    BigDecimal newFromBalance = fromBank.withdraw(transferAmount);
                    if (newFromBalance == null) {
                        return CompletableFuture.completedFuture(false);
                    }
                    
                    // Deposit to target
                    BigDecimal newToBalance = toBank.deposit(transferAmount);
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
        return bankController.loadBank(accountName)
            .thenCompose(bank -> {
                if (bank == null) {
                    // Bank doesn't exist, just remove membership
                    membershipManager.deleteAccount(accountName);
                    return CompletableFuture.completedFuture(true);
                }
                
                return bankController.deleteBank(bank)
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
