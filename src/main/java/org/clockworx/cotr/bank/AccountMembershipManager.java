package org.clockworx.cotr.bank;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
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
 * account-memberships.yml for durability across server restarts.
 * 
 * This is a custom layer on top of ServiceIO's BankController, which only supports
 * one owner per bank. We use the bank name as the account identifier and maintain
 * our own mapping of which players have access to which accounts.
 */
public class AccountMembershipManager {
    
    private final CoinOfTheRealmPlugin plugin;
    private final Map<String, Set<AccountMembership>> accountMemberships; // accountName -> memberships
    private final Map<UUID, Set<AccountMembership>> playerMemberships; // playerUuid -> memberships
    private final File membershipFile;
    
    /**
     * Creates a new AccountMembershipManager.
     * 
     * @param plugin The plugin instance
     */
    public AccountMembershipManager(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
        this.accountMemberships = new ConcurrentHashMap<>();
        this.playerMemberships = new ConcurrentHashMap<>();
        this.membershipFile = new File(plugin.getDataFolder(), "account-memberships.yml");
    }
    
    /**
     * Loads account memberships from the YAML file.
     * Called on plugin enable.
     */
    public void load() {
        if (!membershipFile.exists()) {
            plugin.getLogger().info("No account-memberships.yml found, starting with empty memberships");
            return;
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(membershipFile);
        
        if (!config.contains("accounts")) {
            plugin.getLogger().info("No accounts found in account-memberships.yml");
            return;
        }
        
        int loadedCount = 0;
        for (String accountName : config.getConfigurationSection("accounts").getKeys(false)) {
            String path = "accounts." + accountName;
            // Owner UUID is stored but we load all members from the members list
            config.getString(path + ".owner"); // Read but don't need to store separately
            List<Map<?, ?>> membersList = config.getMapList(path + ".members");
            
            Set<AccountMembership> memberships = new HashSet<>();
            
            for (Map<?, ?> memberData : membersList) {
                UUID playerUuid = UUID.fromString((String) memberData.get("uuid"));
                AccountRole role = AccountRole.valueOf((String) memberData.get("role"));
                long createdAt = memberData.containsKey("joined") ? 
                    ((Number) memberData.get("joined")).longValue() : System.currentTimeMillis();
                
                AccountMembership membership = new AccountMembership(accountName, playerUuid, role, createdAt);
                memberships.add(membership);
                
                // Add to player index
                playerMemberships.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(membership);
                loadedCount++;
            }
            
            accountMemberships.put(accountName, memberships);
        }
        
        plugin.getLogger().info("Loaded " + loadedCount + " account memberships from " + 
                              accountMemberships.size() + " accounts");
    }
    
    /**
     * Saves account memberships to the YAML file.
     * Called on plugin disable and after membership changes.
     */
    public void save() {
        FileConfiguration config = new YamlConfiguration();
        
        for (Map.Entry<String, Set<AccountMembership>> entry : accountMemberships.entrySet()) {
            String accountName = entry.getKey();
            Set<AccountMembership> memberships = entry.getValue();
            
            // Find the owner (should be the one with OWNER role)
            UUID ownerUuid = memberships.stream()
                .filter(m -> m.getRole() == AccountRole.OWNER)
                .map(AccountMembership::getPlayerUuid)
                .findFirst()
                .orElse(null);
            
            if (ownerUuid == null) {
                plugin.getLogger().warning("Account " + accountName + " has no owner, skipping");
                continue;
            }
            
            String path = "accounts." + accountName;
            config.set(path + ".owner", ownerUuid.toString());
            
            List<Map<String, Object>> membersList = new ArrayList<>();
            for (AccountMembership membership : memberships) {
                Map<String, Object> memberData = new HashMap<>();
                memberData.put("uuid", membership.getPlayerUuid().toString());
                memberData.put("role", membership.getRole().name());
                memberData.put("joined", membership.getCreatedAt());
                membersList.add(memberData);
            }
            
            config.set(path + ".members", membersList);
        }
        
        try {
            config.save(membershipFile);
            plugin.getLogger().info("Saved account memberships to account-memberships.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save account memberships: " + e.getMessage());
        }
    }
    
    /**
     * Creates a new account with the specified owner.
     * 
     * @param accountName The name of the account (must be unique)
     * @param ownerUuid The UUID of the account owner
     * @return true if the account was created, false if it already exists
     */
    public boolean createAccount(@NotNull String accountName, @NotNull UUID ownerUuid) {
        if (accountMemberships.containsKey(accountName)) {
            return false;
        }
        
        AccountMembership ownerMembership = new AccountMembership(accountName, ownerUuid, AccountRole.OWNER);
        Set<AccountMembership> memberships = ConcurrentHashMap.newKeySet();
        memberships.add(ownerMembership);
        
        accountMemberships.put(accountName, memberships);
        playerMemberships.computeIfAbsent(ownerUuid, k -> ConcurrentHashMap.newKeySet()).add(ownerMembership);
        
        save();
        return true;
    }
    
    /**
     * Deletes an account and all its memberships.
     * 
     * @param accountName The name of the account to delete
     * @return true if the account was deleted, false if it didn't exist
     */
    public boolean deleteAccount(@NotNull String accountName) {
        Set<AccountMembership> memberships = accountMemberships.remove(accountName);
        if (memberships == null) {
            return false;
        }
        
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
        
        save();
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
        Set<AccountMembership> memberships = accountMemberships.get(accountName);
        if (memberships == null) {
            return false;
        }
        
        // Check if already a member
        if (memberships.stream().anyMatch(m -> m.getPlayerUuid().equals(playerUuid))) {
            return false;
        }
        
        AccountMembership membership = new AccountMembership(accountName, playerUuid, role);
        memberships.add(membership);
        playerMemberships.computeIfAbsent(playerUuid, k -> ConcurrentHashMap.newKeySet()).add(membership);
        
        save();
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
        
        memberships.remove(membership);
        
        Set<AccountMembership> playerSet = playerMemberships.get(playerUuid);
        if (playerSet != null) {
            playerSet.remove(membership);
            if (playerSet.isEmpty()) {
                playerMemberships.remove(playerUuid);
            }
        }
        
        save();
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
