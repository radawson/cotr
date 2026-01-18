package org.clockworx.cotr.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * BankRecord - Data class representing a bank account record in storage
 * 
 * This class represents the persistent data for a bank account:
 * - Account identification (name, owner UUID)
 * - World association (nullable for global banks)
 * - Balance information
 * - Timestamps for auditing
 */
public class BankRecord {
    
    private final String name;
    private final UUID ownerUuid;
    @Nullable
    private final String worldName;
    private final BigDecimal balance;
    private final long createdAt;
    private final long updatedAt;
    
    /**
     * Creates a new BankRecord.
     * 
     * @param name The bank account name (must be unique)
     * @param ownerUuid The UUID of the bank owner
     * @param worldName The world name (null for global banks)
     * @param balance The current balance
     * @param createdAt Creation timestamp (milliseconds since epoch)
     * @param updatedAt Last update timestamp (milliseconds since epoch)
     */
    public BankRecord(@NotNull String name, 
                     @NotNull UUID ownerUuid, 
                     @Nullable String worldName,
                     @NotNull BigDecimal balance,
                     long createdAt,
                     long updatedAt) {
        this.name = name;
        this.ownerUuid = ownerUuid;
        this.worldName = worldName;
        this.balance = balance;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
    
    /**
     * Creates a new BankRecord with current timestamps.
     * 
     * @param name The bank account name
     * @param ownerUuid The UUID of the bank owner
     * @param worldName The world name (null for global banks)
     * @param balance The current balance
     */
    public BankRecord(@NotNull String name, 
                     @NotNull UUID ownerUuid, 
                     @Nullable String worldName,
                     @NotNull BigDecimal balance) {
        this(name, ownerUuid, worldName, balance, System.currentTimeMillis(), System.currentTimeMillis());
    }
    
    /**
     * Creates a copy of this BankRecord with an updated balance and timestamp.
     * 
     * @param newBalance The new balance
     * @return A new BankRecord with updated balance and updatedAt timestamp
     */
    @NotNull
    public BankRecord withBalance(@NotNull BigDecimal newBalance) {
        return new BankRecord(name, ownerUuid, worldName, newBalance, createdAt, System.currentTimeMillis());
    }
    
    @NotNull
    public String getName() {
        return name;
    }
    
    @NotNull
    public UUID getOwnerUuid() {
        return ownerUuid;
    }
    
    @Nullable
    public String getWorldName() {
        return worldName;
    }
    
    @NotNull
    public BigDecimal getBalance() {
        return balance;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    public long getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Checks if this bank is world-specific.
     * 
     * @return true if worldName is not null
     */
    public boolean isWorldSpecific() {
        return worldName != null;
    }
    
    @Override
    public String toString() {
        return "BankRecord{" +
                "name='" + name + '\'' +
                ", ownerUuid=" + ownerUuid +
                ", worldName=" + worldName +
                ", balance=" + balance +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
