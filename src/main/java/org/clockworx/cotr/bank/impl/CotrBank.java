package org.clockworx.cotr.bank.impl;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.bank.storage.BankRecord;
import org.clockworx.cotr.bank.storage.BankStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CotrBank - Bank account implementation for Coin of the Realm
 * 
 * This class represents a bank account in the Coin of the Realm system.
 * It provides the same API as ServiceIO's Bank interface to allow interoperability
 * with other plugins that use ServiceIO's banking API.
 * 
 * Note: This class does not directly implement Bank to avoid requiring ServiceIO
 * at compile time. The methods match the Bank interface signature and will work
 * when accessed through reflection or when ServiceIO is available.
 * 
 * The bank is backed by BankStorage for persistence and provides
 * thread-safe operations for balance management.
 */
public class CotrBank {
    
    private final CoinOfTheRealmPlugin plugin;
    private final BankStorage storage;
    private final String name;
    private volatile UUID ownerUuid; // Volatile for thread-safety, can be changed via setOwner
    @Nullable
    private final String worldName;
    private volatile BigDecimal balance; // Volatile for thread-safety
    private final long createdAt;
    private volatile long updatedAt;
    
    /**
     * Creates a new CotrBank instance from a BankRecord.
     * 
     * @param plugin The plugin instance
     * @param storage The storage backend
     * @param record The bank record from storage
     */
    public CotrBank(@NotNull CoinOfTheRealmPlugin plugin,
                   @NotNull BankStorage storage,
                   @NotNull BankRecord record) {
        this.plugin = plugin;
        this.storage = storage;
        this.name = record.getName();
        this.ownerUuid = record.getOwnerUuid();
        this.worldName = record.getWorldName();
        this.balance = record.getBalance();
        this.createdAt = record.getCreatedAt();
        this.updatedAt = record.getUpdatedAt();
    }
    
    /**
     * Creates a new CotrBank instance (for new banks).
     * 
     * @param plugin The plugin instance
     * @param storage The storage backend
     * @param name The bank name
     * @param ownerUuid The owner UUID
     * @param worldName The world name (null for global banks)
     */
    public CotrBank(@NotNull CoinOfTheRealmPlugin plugin,
                   @NotNull BankStorage storage,
                   @NotNull String name,
                   @NotNull UUID ownerUuid,
                   @Nullable String worldName) {
        this.plugin = plugin;
        this.storage = storage;
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.balance = BigDecimal.ZERO;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }
    
    /**
     * Gets the bank owner's UUID.
     * 
     * @return The owner UUID
     */
    @NotNull
    public UUID getOwner() {
        return ownerUuid;
    }
    
    /**
     * Gets the bank name.
     * 
     * @return The bank name
     */
    @NotNull
    public String getName() {
        return name;
    }
    
    /**
     * Gets the world for this bank (if world-specific).
     * 
     * @return The world, or empty for global banks
     */
    @NotNull
    public java.util.Optional<World> getWorld() {
        if (worldName == null) {
            return java.util.Optional.empty();
        }
        World world = Bukkit.getWorld(worldName);
        return java.util.Optional.ofNullable(world);
    }
    
    /**
     * Gets the current balance.
     * 
     * @return The balance
     */
    @NotNull
    public BigDecimal getBalance() {
        return balance;
    }
    
    /**
     * Sets the bank owner.
     * 
     * @param ownerUuid The new owner UUID
     * @return true if successful
     */
    public boolean setOwner(@NotNull UUID ownerUuid) {
        plugin.debug("CotrBank.setOwner() - name={}, newOwner={}", name, ownerUuid);
        synchronized (this) {
            this.ownerUuid = ownerUuid;
            updatedAt = System.currentTimeMillis();
            // Note: We don't update storage here as the Bank interface doesn't require it
            // The owner is typically set only during bank creation
            return true; // Always succeeds for our implementation
        }
    }
    
    /**
     * Removes a member from the bank.
     * 
     * @param memberUuid The member UUID
     * @return true if removed
     */
    public boolean removeMember(@NotNull UUID memberUuid) {
        plugin.debug("CotrBank.removeMember() - name={}, member={}", name, memberUuid);
        // Note: Our implementation uses AccountMembershipManager for member management
        // This method is part of the Bank interface but we delegate to AccountMembershipManager
        // For now, we'll return false as member management is handled separately
        // This could be enhanced to integrate with AccountMembershipManager if needed
        return false;
    }
    
    /**
     * Checks if a UUID is a member of the bank.
     * 
     * @param memberUuid The member UUID
     * @return true if member
     */
    public boolean isMember(@NotNull UUID memberUuid) {
        plugin.debug("CotrBank.isMember() - name={}, member={}", name, memberUuid);
        // Note: Our implementation uses AccountMembershipManager for member management
        // This method is part of the Bank interface but we delegate to AccountMembershipManager
        // For now, we'll return true only if the member is the owner
        // This could be enhanced to integrate with AccountMembershipManager if needed
        return ownerUuid.equals(memberUuid);
    }
    
    /**
     * Adds a member to the bank.
     * 
     * @param memberUuid The member UUID
     * @return true if added
     */
    public boolean addMember(@NotNull UUID memberUuid) {
        plugin.debug("CotrBank.addMember() - name={}, member={}", name, memberUuid);
        // Note: Our implementation uses AccountMembershipManager for member management
        // This method is part of the Bank interface but we delegate to AccountMembershipManager
        // For now, we'll return false as member management is handled separately
        // This could be enhanced to integrate with AccountMembershipManager if needed
        return false;
    }
    
    /**
     * Gets all member UUIDs.
     * 
     * @return Set of member UUIDs
     */
    @NotNull
    public java.util.Set<UUID> getMembers() {
        plugin.debug("CotrBank.getMembers() - name={}", name);
        // Note: Our implementation uses AccountMembershipManager for member management
        // This method is part of the Bank interface but we delegate to AccountMembershipManager
        // For now, we'll return only the owner
        // This could be enhanced to integrate with AccountMembershipManager if needed
        java.util.Set<UUID> members = new java.util.HashSet<>();
        members.add(ownerUuid);
        return members;
    }
    
    /**
     * Sets the balance directly.
     * 
     * @param amount The new balance
     */
    public void setBalance(Number amount) {
        plugin.debug("CotrBank.setBalance() - name={}, amount={}", name, amount);
        BigDecimal newBalance = amount instanceof BigDecimal ? (BigDecimal) amount : BigDecimal.valueOf(amount.doubleValue());
        
        synchronized (this) {
            balance = newBalance;
            updatedAt = System.currentTimeMillis();
            
            // Persist to storage asynchronously
            storage.updateBalance(name, newBalance).thenAccept(success -> {
                if (success) {
                    plugin.debug("CotrBank.setBalance() - Balance persisted: {}", newBalance);
                } else {
                    plugin.getLogger().warning("Failed to persist balance update for bank: " + name);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error persisting balance for bank: " + name, ex);
                return null;
            });
        }
    }
    
    /**
     * Deposits funds into the bank.
     * 
     * @param amount The amount to deposit
     * @return The new balance
     * @throws IllegalArgumentException if amount is negative
     */
    @NotNull
    public BigDecimal deposit(Number amount) {
        BigDecimal bigDecimalAmount = amount instanceof BigDecimal ? (BigDecimal) amount : BigDecimal.valueOf(amount.doubleValue());
        return depositBigDecimal(bigDecimalAmount);
    }
    
    @NotNull
    public BigDecimal depositBigDecimal(@NotNull BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Deposit amount cannot be negative");
        }
        
        plugin.debug("CotrBank.deposit() - name={}, amount={}, currentBalance={}", name, amount, balance);
        
        synchronized (this) {
            BigDecimal newBalance = balance.add(amount);
            balance = newBalance;
            updatedAt = System.currentTimeMillis();
            
            // Persist to storage asynchronously
            storage.updateBalance(name, newBalance).thenAccept(success -> {
                if (success) {
                    plugin.debug("CotrBank.deposit() - Balance persisted: {}", newBalance);
                } else {
                    plugin.getLogger().warning("Failed to persist balance update for bank: " + name);
                    // Rollback balance (though this is unlikely to happen)
                    balance = balance.subtract(amount);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error persisting deposit for bank: " + name, ex);
                // Rollback balance
                balance = balance.subtract(amount);
                return null;
            });
            
            plugin.debug("CotrBank.deposit() - New balance: {}", newBalance);
            return newBalance;
        }
    }
    
    /**
     * Withdraws funds from the bank.
     * 
     * @param amount The amount to withdraw
     * @return The new balance
     * @throws IllegalArgumentException if amount is negative
     * @throws IllegalStateException if insufficient funds
     */
    @NotNull
    public BigDecimal withdraw(Number amount) {
        BigDecimal bigDecimalAmount = amount instanceof BigDecimal ? (BigDecimal) amount : BigDecimal.valueOf(amount.doubleValue());
        return withdrawBigDecimal(bigDecimalAmount);
    }
    
    @NotNull
    public BigDecimal withdrawBigDecimal(@NotNull BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Withdrawal amount cannot be negative");
        }
        
        plugin.debug("CotrBank.withdraw() - name={}, amount={}, currentBalance={}", name, amount, balance);
        
        synchronized (this) {
            if (balance.compareTo(amount) < 0) {
                throw new IllegalStateException("Insufficient funds. Balance: " + balance + ", Requested: " + amount);
            }
            
            BigDecimal newBalance = balance.subtract(amount);
            balance = newBalance;
            updatedAt = System.currentTimeMillis();
            
            // Persist to storage asynchronously
            storage.updateBalance(name, newBalance).thenAccept(success -> {
                if (success) {
                    plugin.debug("CotrBank.withdraw() - Balance persisted: {}", newBalance);
                } else {
                    plugin.getLogger().warning("Failed to persist balance update for bank: " + name);
                    // Rollback balance
                    balance = balance.add(amount);
                }
            }).exceptionally(ex -> {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error persisting withdrawal for bank: " + name, ex);
                // Rollback balance
                balance = balance.add(amount);
                return null;
            });
            
            plugin.debug("CotrBank.withdraw() - New balance: {}", newBalance);
            return newBalance;
        }
    }
    
    /**
     * Gets the creation timestamp.
     * 
     * @return The creation timestamp in milliseconds
     */
    public long getCreatedAt() {
        return createdAt;
    }
    
    /**
     * Gets the last update timestamp.
     * 
     * @return The last update timestamp in milliseconds
     */
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Gets the world name (if world-specific).
     * 
     * @return The world name, or null for global banks
     */
    @Nullable
    public String getWorldName() {
        return worldName;
    }
    
    /**
     * Checks if this is a world-specific bank.
     * 
     * @return true if worldName is not null
     */
    public boolean isWorldSpecific() {
        return worldName != null;
    }
    
    /**
     * Refreshes the balance from storage.
     * Useful for ensuring we have the latest balance.
     * 
     * @return A CompletableFuture that completes when the balance is refreshed
     */
    @NotNull
    public CompletableFuture<Void> refreshBalance() {
        return storage.loadBank(name).thenAccept(recordOpt -> {
            if (recordOpt.isPresent()) {
                BankRecord record = recordOpt.get();
                synchronized (this) {
                    balance = record.getBalance();
                    updatedAt = record.getUpdatedAt();
                }
                plugin.debug("CotrBank.refreshBalance() - Balance refreshed: {}", balance);
            } else {
                plugin.getLogger().warning("Bank not found in storage during refresh: " + name);
            }
        });
    }
    
    @Override
    public String toString() {
        return "CotrBank{" +
                "name='" + name + '\'' +
                ", ownerUuid=" + ownerUuid +
                ", worldName=" + worldName +
                ", balance=" + balance +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
