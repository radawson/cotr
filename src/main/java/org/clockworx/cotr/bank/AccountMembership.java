package org.clockworx.cotr.bank;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

/**
 * AccountMembership - Represents a player's membership in a bank account
 * 
 * This class is part of the many-to-many relationship system that allows:
 * - Players to own multiple accounts
 * - Accounts to have multiple players (e.g., guild accounts)
 * 
 * Each membership links a player (UUID) to an account (by name) with a specific role.
 * The role determines what actions the player can perform on the account.
 */
public class AccountMembership {
    
    private final String accountName;
    private final UUID playerUuid;
    private final AccountRole role;
    private final long createdAt;
    
    /**
     * Creates a new AccountMembership.
     * 
     * @param accountName The name of the account (must match BankController bank name)
     * @param playerUuid The UUID of the player with this membership
     * @param role The role the player has in this account
     * @param createdAt Timestamp when this membership was created (milliseconds since epoch)
     */
    public AccountMembership(@NotNull String accountName, @NotNull UUID playerUuid, 
                            @NotNull AccountRole role, long createdAt) {
        this.accountName = accountName;
        this.playerUuid = playerUuid;
        this.role = role;
        this.createdAt = createdAt;
    }
    
    /**
     * Creates a new AccountMembership with the current timestamp.
     * 
     * @param accountName The name of the account
     * @param playerUuid The UUID of the player
     * @param role The role the player has
     */
    public AccountMembership(@NotNull String accountName, @NotNull UUID playerUuid, 
                            @NotNull AccountRole role) {
        this(accountName, playerUuid, role, System.currentTimeMillis());
    }
    
    /**
     * Gets the account name this membership is for.
     * 
     * @return The account name
     */
    @NotNull
    public String getAccountName() {
        return accountName;
    }
    
    /**
     * Gets the UUID of the player with this membership.
     * 
     * @return The player UUID
     */
    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }
    
    /**
     * Gets the role the player has in this account.
     * 
     * @return The account role
     */
    @NotNull
    public AccountRole getRole() {
        return role;
    }
    
    /**
     * Gets the timestamp when this membership was created.
     * 
     * @return The creation timestamp in milliseconds since epoch
     */
    public long getCreatedAt() {
        return createdAt;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AccountMembership that = (AccountMembership) o;
        return Objects.equals(accountName, that.accountName) &&
               Objects.equals(playerUuid, that.playerUuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accountName, playerUuid);
    }
    
    @Override
    public String toString() {
        return "AccountMembership{" +
               "accountName='" + accountName + '\'' +
               ", playerUuid=" + playerUuid +
               ", role=" + role +
               ", createdAt=" + createdAt +
               '}';
    }
}
