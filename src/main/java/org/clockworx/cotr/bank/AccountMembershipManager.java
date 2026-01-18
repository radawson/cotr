package org.clockworx.cotr.bank;

import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.storage.DatabaseBankStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AccountMembershipManager - Manages the many-to-many relationship between players and accounts
 * 
 * This class maintains the membership data that allows:
 * - Players to own multiple accounts
 * - Accounts to have multiple players (shared/guild accounts)
 * 
 * The manager stores memberships in memory for fast access and persists them to
 * the database for durability across server restarts.
 * 
 * This is a custom layer on top of ServiceIO's BankController, which only supports
 * one owner per bank. We use the bank name as the account identifier and maintain
 * our own mapping of which players have access to which accounts.
 */
public class AccountMembershipManager {
    
    private final CoinOfTheRealmPlugin plugin;
    private final DatabaseBankStorage storage;
    private final Map<String, Set<AccountMembership>> accountMemberships; // accountName -> memberships
    private final Map<UUID, Set<AccountMembership>> playerMemberships; // playerUuid -> memberships
    
    /**
     * Creates a new AccountMembershipManager.
     * 
     * @param plugin The plugin instance
     * @param storage The database storage (can be null if not yet initialized)
     */
    public AccountMembershipManager(@NotNull CoinOfTheRealmPlugin plugin, @Nullable DatabaseBankStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.accountMemberships = new ConcurrentHashMap<>();
        this.playerMemberships = new ConcurrentHashMap<>();
    }
    
    /**
     * Loads account memberships from the database.
     * Called on plugin enable.
     * Also migrates from YAML if it exists.
     */
    public void load() {
        if (storage == null) {
            plugin.getLogger().warning("Database storage not available, cannot load memberships");
            return;
        }
        
        plugin.debug("AccountMembershipManager.load() - Loading memberships from database");
        
        // First, try to migrate from YAML if it exists (synchronously)
        migrateFromYaml();
        
        // Load all memberships from database (synchronously wait for completion)
        try {
            List<DatabaseBankStorage.MembershipRecord> allRecords = storage.loadAllMemberships().join();
            
            // Group memberships by account name
            Map<String, Set<AccountMembership>> membershipsByAccount = new HashMap<>();
            
            for (DatabaseBankStorage.MembershipRecord record : allRecords) {
                AccountRole role = AccountRole.valueOf(record.getRole());
                AccountMembership membership = new AccountMembership(
                    record.getAccountName(),
                    record.getPlayerUuid(),
                    role,
                    record.getCreatedAt()
                );
                
                // Add to account index
                membershipsByAccount.computeIfAbsent(record.getAccountName(), 
                    k -> ConcurrentHashMap.newKeySet()).add(membership);
                
                // Add to player index
                playerMemberships.computeIfAbsent(record.getPlayerUuid(), 
                    k -> ConcurrentHashMap.newKeySet()).add(membership);
            }
            
            // Update account memberships map
            accountMemberships.putAll(membershipsByAccount);
            
            int totalMemberships = accountMemberships.values().stream().mapToInt(Set::size).sum();
            plugin.getLogger().info("Loaded " + totalMemberships + " account memberships from " + 
                accountMemberships.size() + " accounts");
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load memberships from database", e);
        }
    }
    
    /**
     * Migrates membership data from YAML to database if YAML file exists.
     */
    private void migrateFromYaml() {
        File yamlFile = new File(plugin.getDataFolder(), "account-memberships.yml");
        if (!yamlFile.exists()) {
            plugin.debug("AccountMembershipManager.migrateFromYaml() - No YAML file found, skipping migration");
            return;
        }
        
        plugin.getLogger().info("Found account-memberships.yml, migrating to database...");
        
        try {
            org.bukkit.configuration.file.FileConfiguration config = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(yamlFile);
            
            if (!config.contains("accounts")) {
                plugin.debug("AccountMembershipManager.migrateFromYaml() - No accounts in YAML file");
                return;
            }
            
            List<CompletableFuture<Boolean>> migrationFutures = new ArrayList<>();
            int[] migratedCount = {0};
            
            for (String accountName : config.getConfigurationSection("accounts").getKeys(false)) {
                String path = "accounts." + accountName;
                List<Map<?, ?>> membersList = config.getMapList(path + ".members");
                
                for (Map<?, ?> memberData : membersList) {
                    UUID playerUuid = UUID.fromString((String) memberData.get("uuid"));
                    String role = (String) memberData.get("role");
                    long createdAt = memberData.containsKey("joined") ? 
                        ((Number) memberData.get("joined")).longValue() : System.currentTimeMillis();
                    
                    // Check if already exists in database, then create if needed
                    CompletableFuture<Boolean> future = storage.membershipExists(accountName, playerUuid)
                        .thenCompose(exists -> {
                            if (!exists) {
                                return storage.createMembership(accountName, playerUuid, role, createdAt);
                            }
                            return CompletableFuture.completedFuture(false);
                        });
                    migrationFutures.add(future);
                    migratedCount[0]++;
                }
            }
            
            // Wait for all migrations to complete
            CompletableFuture.allOf(migrationFutures.toArray(new CompletableFuture[0])).join();
            
            plugin.getLogger().info("Migrated " + migratedCount[0] + " memberships from YAML to database");
            plugin.getLogger().info("You can now delete account-memberships.yml if desired");
            
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to migrate from YAML", e);
        }
    }
    
    /**
     * Saves account memberships to the database.
     * Called on plugin disable. Note: Memberships are saved immediately when changed,
     * so this is mainly for ensuring consistency.
     */
    public void save() {
        // Memberships are saved immediately when changed, so this is a no-op
        // Kept for API compatibility
        plugin.debug("AccountMembershipManager.save() - Memberships are persisted immediately, no action needed");
    }
    
    /**
     * Creates a new account with the specified owner.
     * 
     * @param accountName The name of the account (must be unique)
     * @param ownerUuid The UUID of the account owner
     * @return true if the account was created, false if it already exists
     */
    public boolean createAccount(@NotNull String accountName, @NotNull UUID ownerUuid) {
        if (storage == null) {
            plugin.getLogger().warning("Database storage not available, cannot create account");
            return false;
        }
        
        if (accountMemberships.containsKey(accountName)) {
            return false;
        }
        
        AccountMembership ownerMembership = new AccountMembership(accountName, ownerUuid, AccountRole.OWNER);
        
        // Save to database
        storage.createMembership(accountName, ownerUuid, AccountRole.OWNER.name(), ownerMembership.getCreatedAt())
            .thenAccept(success -> {
                if (!success) {
                    plugin.getLogger().warning("Failed to create membership in database for account: " + accountName);
                }
            });
        
        // Update in-memory cache
        Set<AccountMembership> memberships = ConcurrentHashMap.newKeySet();
        memberships.add(ownerMembership);
        accountMemberships.put(accountName, memberships);
        playerMemberships.computeIfAbsent(ownerUuid, k -> ConcurrentHashMap.newKeySet()).add(ownerMembership);
        
        return true;
    }
    
    /**
     * Deletes an account and all its memberships.
     * 
     * @param accountName The name of the account to delete
     * @return true if the account was deleted, false if it didn't exist
     */
    public boolean deleteAccount(@NotNull String accountName) {
        if (storage == null) {
            plugin.getLogger().warning("Database storage not available, cannot delete account");
            return false;
        }
        
        Set<AccountMembership> memberships = accountMemberships.remove(accountName);
        if (memberships == null) {
            return false;
        }
        
        // Delete from database
        storage.deleteAllMemberships(accountName);
        
        // Remove from player index
        for (AccountMembership membership : memberships) {
            Set<AccountMembership> playerSet = playerMemberships.get(membership.getPlayerUuid());
            if (playerSet != null) {
                playerSet.remove(membership);
                if (playerSet.isEmpty()) {
                    playerMemberships.remove(membership.getPlayerUuid());
                }
            }
        }
        
        return true;
    }
    
    /**
     * Adds a member to an account.
     * 
     * @param accountName The account name
     * @param playerUuid The UUID of the player to add
     * @param role The role to assign
     * @return true if the member was added, false if already a member
     */
    public boolean addMember(@NotNull String accountName, @NotNull UUID playerUuid, @NotNull AccountRole role) {
        if (storage == null) {
            plugin.getLogger().warning("Database storage not available, cannot add member");
            return false;
        }
        
        Set<AccountMembership> memberships = accountMemberships.get(accountName);
        if (memberships == null) {
            return false;
        }
        
        // Check if already a member
        if (memberships.stream().anyMatch(m -> m.getPlayerUuid().equals(playerUuid))) {
            return false;
        }
        
        AccountMembership membership = new AccountMembership(accountName, playerUuid, role);
        
        // Save to database
        storage.createMembership(accountName, playerUuid, role.name(), membership.getCreatedAt())
            .thenAccept(success -> {
                if (!success) {
                    plugin.getLogger().warning("Failed to add membership in database");
                }
            });
        
        // Update in-memory cache
        memberships.add(membership);
        playerMemberships.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(membership);
        
        return true;
    }
    
    /**
     * Removes a member from an account.
     * 
     * @param accountName The account name
     * @param playerUuid The UUID of the player to remove
     * @return true if the member was removed, false if not a member
     */
    public boolean removeMember(@NotNull String accountName, @NotNull UUID playerUuid) {
        if (storage == null) {
            plugin.getLogger().warning("Database storage not available, cannot remove member");
            return false;
        }
        
        Set<AccountMembership> memberships = accountMemberships.get(accountName);
        if (memberships == null) {
            return false;
        }
        
        AccountMembership membership = memberships.stream()
            .filter(m -> m.getPlayerUuid().equals(playerUuid))
            .findFirst()
            .orElse(null);
        
        if (membership == null) {
            return false;
        }
        
        // Don't allow removing the owner if they're the only member
        if (membership.getRole() == AccountRole.OWNER && memberships.size() == 1) {
            return false;
        }
        
        // Delete from database
        storage.deleteMembership(accountName, playerUuid);
        
        // Update in-memory cache
        memberships.remove(membership);
        
        Set<AccountMembership> playerSet = playerMemberships.get(playerUuid);
        if (playerSet != null) {
            playerSet.remove(membership);
            if (playerSet.isEmpty()) {
                playerMemberships.remove(playerUuid);
            }
        }
        
        return true;
    }
    
    /**
     * Gets all accounts a player has access to.
     * 
     * @param playerUuid The player's UUID
     * @return A set of account names the player has access to
     */
    @NotNull
    public Set<String> getAccountsForPlayer(@NotNull UUID playerUuid) {
        Set<AccountMembership> memberships = playerMemberships.get(playerUuid);
        if (memberships == null) {
            return Collections.emptySet();
        }
        
        return memberships.stream()
            .map(AccountMembership::getAccountName)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets all players with access to an account.
     * 
     * @param accountName The account name
     * @return A set of player UUIDs with access to the account
     */
    @NotNull
    public Set<UUID> getMembersForAccount(@NotNull String accountName) {
        Set<AccountMembership> memberships = accountMemberships.get(accountName);
        if (memberships == null) {
            return Collections.emptySet();
        }
        
        return memberships.stream()
            .map(AccountMembership::getPlayerUuid)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets all memberships for an account.
     * 
     * @param accountName The account name
     * @return A set of AccountMembership objects, or empty set if account doesn't exist
     */
    @NotNull
    public Set<AccountMembership> getAccountMemberships(@NotNull String accountName) {
        Set<AccountMembership> memberships = accountMemberships.get(accountName);
        return memberships != null ? new HashSet<>(memberships) : Collections.emptySet();
    }
    
    /**
     * Checks if a player has access to an account.
     * 
     * @param playerUuid The player's UUID
     * @param accountName The account name
     * @return true if the player has access
     */
    public boolean hasAccess(@NotNull UUID playerUuid, @NotNull String accountName) {
        Set<AccountMembership> memberships = playerMemberships.get(playerUuid);
        if (memberships == null) {
            return false;
        }
        
        return memberships.stream()
            .anyMatch(m -> m.getAccountName().equals(accountName));
    }
    
    /**
     * Gets a player's role in an account.
     * 
     * @param playerUuid The player's UUID
     * @param accountName The account name
     * @return The player's role, or null if not a member
     */
    @Nullable
    public AccountRole getRole(@NotNull UUID playerUuid, @NotNull String accountName) {
        Set<AccountMembership> memberships = playerMemberships.get(playerUuid);
        if (memberships == null) {
            return null;
        }
        
        return memberships.stream()
            .filter(m -> m.getAccountName().equals(accountName))
            .map(AccountMembership::getRole)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Gets the total number of accounts.
     * 
     * @return The number of accounts
     */
    public int getAccountCount() {
        return accountMemberships.size();
    }
}
