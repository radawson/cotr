package org.clockworx.cotr.bank.impl;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.bank.storage.BankRecord;
import org.clockworx.cotr.bank.storage.BankStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * CotrBankController - Implementation of ServiceIO's BankController interface
 * 
 * This class provides a complete banking system for Coin of the Realm that:
 * - Implements the full ServiceIO BankController API (via reflection proxy)
 * - Uses database storage for persistence
 * - Provides in-memory caching for performance
 * - Supports world-specific banks
 * - Handles concurrent access safely
 * 
 * The controller manages bank accounts and provides methods for:
 * - Creating and deleting banks
 * - Loading banks by name, owner, or world
 * - Formatting currency amounts
 * - Managing bank memberships (delegated to AccountMembershipManager)
 * 
 * Note: This class does not directly implement BankController to avoid requiring
 * ServiceIO at compile time. A dynamic proxy is created at runtime when ServiceIO
 * is available to implement the interface.
 */
public class CotrBankController {
    
    private final CoinOfTheRealmPlugin plugin;
    private final BankStorage storage;
    
    // In-memory cache for loaded banks
    private final Map<String, CotrBank> bankCache = new ConcurrentHashMap<>();
    private final Map<UUID, CotrBank> ownerCache = new ConcurrentHashMap<>(); // For global banks
    private final Map<String, CotrBank> ownerWorldCache = new ConcurrentHashMap<>(); // Key: "uuid:world"
    
    // Cache for all banks (lazy-loaded)
    // Using Object instead of Bank to avoid compile-time dependency on ServiceIO
    private volatile Set<Object> allBanksCache = null;
    private volatile long allBanksCacheTime = 0;
    private static final long CACHE_TTL = 60000; // 1 minute
    
    /**
     * Creates a new CotrBankController.
     * 
     * @param plugin The plugin instance
     * @param storage The bank storage backend
     */
    public CotrBankController(@NotNull CoinOfTheRealmPlugin plugin, @NotNull BankStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        plugin.debug("CotrBankController created");
    }
    
    /**
     * Gets the name of this controller.
     * 
     * @return The controller name
     */
    @NotNull
    public String getName() {
        return "Coin of the Realm Bank Controller";
    }
    
    /**
     * Gets the plugin instance.
     * 
     * @return The plugin instance
     */
    @NotNull
    public org.bukkit.plugin.Plugin getPlugin() {
        return plugin;
    }
    
    /**
     * Formats a currency amount as a string.
     * 
     * @param amount The amount to format
     * @return Formatted string
     */
    @NotNull
    public String format(@NotNull Number amount) {
        int coins = amount.intValue();
        if (coins == 1) {
            return "1 Coin of the Realm";
        }
        return coins + " Coins of the Realm";
    }
    
    /**
     * Returns the number of fractional digits supported.
     * 
     * @return Always returns 0 (coins are whole numbers)
     */
    public int fractionalDigits() {
        // Coins are whole numbers, no fractional digits
        return 0;
    }
    
    /**
     * Creates a new global bank account.
     * 
     * @param uuid The bank owner's UUID
     * @param name Unique bank name
     * @return CompletableFuture that completes with the created bank
     */
    @NotNull
    public CompletableFuture<Object> createBank(@NotNull UUID uuid, @NotNull String name) {
        return createBank(uuid, name, null);
    }
    
    /**
     * Creates a new world-specific bank account.
     * 
     * Multiple banks can be created for the same owner UUID. Bank names must be unique
     * across all banks. Access control is managed separately via AccountMembershipManager,
     * which supports many-to-many relationships between players and accounts.
     * 
     * @param uuid The bank owner's UUID (technical owner for ServiceIO compatibility)
     * @param name Unique bank name (must be unique across all banks)
     * @param world The world for this bank, or null for global
     * @return CompletableFuture that completes with the created bank
     */
    @NotNull
    public CompletableFuture<Object> createBank(@NotNull UUID uuid, @NotNull String name, @Nullable World world) {
        plugin.debug("CotrBankController.createBank() - uuid={}, name={}, world={}", uuid, name, world != null ? world.getName() : "null");
        
        // Check if bank already exists (name uniqueness check)
        return storage.bankExists(name).thenCompose(exists -> {
            if (exists) {
                plugin.debug("CotrBankController.createBank() - Bank already exists: {}", name);
                CompletableFuture<Object> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("Bank with name '" + name + "' already exists"));
                return future;
            }
            
            // Create the bank
            String worldName = world != null ? world.getName() : null;
            return storage.createBank(name, uuid, worldName, BigDecimal.ZERO).thenApply(success -> {
                if (!success) {
                    throw new IllegalStateException("Failed to create bank: " + name);
                }
                
                // Create CotrBank instance and cache it
                CotrBank bank = new CotrBank(plugin, storage, name, uuid, worldName);
                bankCache.put(name, bank);
                
                // Update owner cache (for backward compatibility with loadBank(UUID) methods)
                // Note: These caches only store one bank per owner, but multiple banks per owner are supported
                if (worldName == null) {
                    ownerCache.put(uuid, bank);
                } else {
                    ownerWorldCache.put(uuid + ":" + worldName, bank);
                }
                
                // Invalidate all banks cache
                invalidateAllBanksCache();
                
                plugin.debug("CotrBankController.createBank() - Bank created and cached: {}", name);
                return bank;
            });
        });
    }
    
    /**
     * Loads a bank by name.
     * 
     * @param name The bank name
     * @return CompletableFuture that completes with the bank, or null if not found
     */
    @NotNull
    public CompletableFuture<Object> loadBank(@NotNull String name) {
        plugin.debug("CotrBankController.loadBank() - name={}", name);
        
        // Check cache first
        CotrBank cached = bankCache.get(name);
        if (cached != null) {
            plugin.debug("CotrBankController.loadBank() - Found in cache: {}", name);
            return CompletableFuture.completedFuture(cached);
        }
        
        // Load from storage
        return storage.loadBank(name).thenApply(recordOpt -> {
            if (recordOpt.isEmpty()) {
                plugin.debug("CotrBankController.loadBank() - Bank not found: {}", name);
                return null;
            }
            
            BankRecord record = recordOpt.get();
            CotrBank bank = new CotrBank(plugin, storage, record);
            
            // Cache it
            bankCache.put(name, bank);
            if (record.getWorldName() == null) {
                ownerCache.put(record.getOwnerUuid(), bank);
            } else {
                ownerWorldCache.put(record.getOwnerUuid() + ":" + record.getWorldName(), bank);
            }
            
            plugin.debug("CotrBankController.loadBank() - Bank loaded and cached: {}", name);
            return bank;
        });
    }
    
    /**
     * Loads a global bank by owner UUID.
     * 
     * @param uuid The owner UUID
     * @return CompletableFuture that completes with the bank, or null if not found
     */
    @NotNull
    public CompletableFuture<Object> loadBank(@NotNull UUID uuid) {
        plugin.debug("CotrBankController.loadBank() - uuid={}", uuid);
        
        // Check cache first
        CotrBank cached = ownerCache.get(uuid);
        if (cached != null) {
            plugin.debug("CotrBankController.loadBank() - Found in cache: {}", uuid);
            return CompletableFuture.completedFuture(cached);
        }
        
        // Load from storage
        return storage.loadBankByOwner(uuid).thenApply(recordOpt -> {
            if (recordOpt.isEmpty()) {
                plugin.debug("CotrBankController.loadBank() - Bank not found for owner: {}", uuid);
                return null;
            }
            
            BankRecord record = recordOpt.get();
            CotrBank bank = new CotrBank(plugin, storage, record);
            
            // Cache it
            bankCache.put(record.getName(), bank);
            ownerCache.put(uuid, bank);
            
            plugin.debug("CotrBankController.loadBank() - Bank loaded and cached: {}", record.getName());
            return bank;
        });
    }
    
    /**
     * Loads a world-specific bank by owner and world.
     * 
     * @param uuid The owner UUID
     * @param world The world
     * @return CompletableFuture that completes with the bank, or null if not found
     */
    @NotNull
    public CompletableFuture<Object> loadBank(@NotNull UUID uuid, @NotNull World world) {
        plugin.debug("CotrBankController.loadBank() - uuid={}, world={}", uuid, world.getName());
        
        String cacheKey = uuid + ":" + world.getName();
        
        // Check cache first
        CotrBank cached = ownerWorldCache.get(cacheKey);
        if (cached != null) {
            plugin.debug("CotrBankController.loadBank() - Found in cache: {}", cacheKey);
            return CompletableFuture.completedFuture(cached);
        }
        
        // Load from storage
        return storage.loadBankByOwner(uuid, world.getName()).thenApply(recordOpt -> {
            if (recordOpt.isEmpty()) {
                plugin.debug("CotrBankController.loadBank() - Bank not found for owner: {}, world: {}", uuid, world.getName());
                return null;
            }
            
            BankRecord record = recordOpt.get();
            CotrBank bank = new CotrBank(plugin, storage, record);
            
            // Cache it
            bankCache.put(record.getName(), bank);
            ownerWorldCache.put(cacheKey, bank);
            
            plugin.debug("CotrBankController.loadBank() - Bank loaded and cached: {}", record.getName());
            return bank;
        });
    }
    
    /**
     * Loads all banks.
     * 
     * @return CompletableFuture that completes with an unmodifiable set of all banks
     */
    @NotNull
    public CompletableFuture<@Unmodifiable Set<Object>> loadBanks() {
        plugin.debug("CotrBankController.loadBanks() - Loading all banks");
        
        // Check cache
        if (allBanksCache != null && (System.currentTimeMillis() - allBanksCacheTime) < CACHE_TTL) {
            plugin.debug("CotrBankController.loadBanks() - Returning cached banks");
            return CompletableFuture.completedFuture(allBanksCache);
        }
        
        // Load from storage
        return storage.loadAllBanks().thenApply(records -> {
            Set<Object> banks = new HashSet<>();
            
            for (BankRecord record : records) {
                // Check if already cached
                CotrBank bank = bankCache.get(record.getName());
                if (bank == null) {
                    bank = new CotrBank(plugin, storage, record);
                    bankCache.put(record.getName(), bank);
                    
                    // Update owner caches
                    if (record.getWorldName() == null) {
                        ownerCache.put(record.getOwnerUuid(), bank);
                    } else {
                        ownerWorldCache.put(record.getOwnerUuid() + ":" + record.getWorldName(), bank);
                    }
                }
                
                banks.add(bank);
            }
            
            // Update cache
            allBanksCache = Collections.unmodifiableSet(banks);
            allBanksCacheTime = System.currentTimeMillis();
            
            plugin.debug("CotrBankController.loadBanks() - Loaded {} banks", banks.size());
            return allBanksCache;
        });
    }
    
    /**
     * Loads all banks for a specific world.
     * 
     * @param world The world
     * @return CompletableFuture that completes with an unmodifiable set of banks
     */
    @NotNull
    public CompletableFuture<@Unmodifiable Set<Object>> loadBanks(@NotNull World world) {
        plugin.debug("CotrBankController.loadBanks() - world={}", world.getName());
        
        return storage.loadBanksByWorld(world.getName()).thenApply(records -> {
            Set<Object> banks = new HashSet<>();
            
            for (BankRecord record : records) {
                // Check if already cached
                CotrBank bank = bankCache.get(record.getName());
                if (bank == null) {
                    bank = new CotrBank(plugin, storage, record);
                    bankCache.put(record.getName(), bank);
                    
                    // Update owner cache
                    ownerWorldCache.put(record.getOwnerUuid() + ":" + record.getWorldName(), bank);
                }
                
                banks.add(bank);
            }
            
            plugin.debug("CotrBankController.loadBanks() - Loaded {} banks for world: {}", banks.size(), world.getName());
            return Collections.unmodifiableSet(banks);
        });
    }
    
    /**
     * Deletes a bank by name.
     * 
     * @param name The bank name
     * @return CompletableFuture that completes with true if deleted, false if not found
     */
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull String name) {
        plugin.debug("CotrBankController.deleteBank() - name={}", name);
        
        return storage.deleteBank(name).thenApply(success -> {
            if (success) {
                // Remove from cache
                CotrBank removed = bankCache.remove(name);
                if (removed != null) {
                    // Remove from owner caches
                    ownerCache.remove(removed.getOwner());
                    String worldName = removed.getWorldName();
                    if (worldName != null) {
                        ownerWorldCache.remove(removed.getOwner() + ":" + worldName);
                    }
                }
                
                // Invalidate all banks cache
                invalidateAllBanksCache();
                
                plugin.debug("CotrBankController.deleteBank() - Bank deleted and removed from cache: {}", name);
            } else {
                plugin.debug("CotrBankController.deleteBank() - Bank not found: {}", name);
            }
            
            return success;
        });
    }
    
    /**
     * Deletes a global bank by owner UUID.
     * 
     * @param uuid The owner UUID
     * @return CompletableFuture that completes with true if deleted, false if not found
     */
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull UUID uuid) {
        plugin.debug("CotrBankController.deleteBank() - uuid={}", uuid);
        
        // Find the bank first
        return loadBank(uuid).thenCompose(bank -> {
            if (bank == null) {
                return CompletableFuture.completedFuture(false);
            }
            // bank is Object, but we know it's a CotrBank
            if (bank instanceof CotrBank) {
                return deleteBank(((CotrBank) bank).getName());
            }
            // Fallback: use reflection to get name
            try {
                java.lang.reflect.Method getNameMethod = bank.getClass().getMethod("getName");
                String bankName = (String) getNameMethod.invoke(bank);
                return deleteBank(bankName);
            } catch (Exception e) {
                plugin.debug("CotrBankController.deleteBank() - Failed to get bank name: {}", e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        });
    }
    
    /**
     * Deletes a world-specific bank by owner and world.
     * 
     * @param uuid The owner UUID
     * @param world The world
     * @return CompletableFuture that completes with true if deleted, false if not found
     */
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull UUID uuid, @NotNull World world) {
        plugin.debug("CotrBankController.deleteBank() - uuid={}, world={}", uuid, world.getName());
        
        // Find the bank first
        return loadBank(uuid, world).thenCompose(bank -> {
            if (bank == null) {
                return CompletableFuture.completedFuture(false);
            }
            // bank is Object, but we know it's a CotrBank
            if (bank instanceof CotrBank) {
                return deleteBank(((CotrBank) bank).getName());
            }
            // Fallback: use reflection to get name
            try {
                java.lang.reflect.Method getNameMethod = bank.getClass().getMethod("getName");
                String bankName = (String) getNameMethod.invoke(bank);
                return deleteBank(bankName);
            } catch (Exception e) {
                plugin.debug("CotrBankController.deleteBank() - Failed to get bank name: {}", e.getMessage());
                return CompletableFuture.completedFuture(false);
            }
        });
    }
    
    /**
     * Gets all cached banks.
     * 
     * @return An unmodifiable set of all cached banks
     */
    @NotNull
    @Unmodifiable
    public Set<Object> getBanks() {
        plugin.debug("CotrBankController.getBanks() - Getting all cached banks");
        
        // Return cached banks if available and fresh
        if (allBanksCache != null && (System.currentTimeMillis() - allBanksCacheTime) < CACHE_TTL) {
            return allBanksCache;
        }
        
        // Otherwise return what we have in cache
        return Collections.unmodifiableSet(new HashSet<>(bankCache.values()));
    }
    
    /**
     * Gets all cached banks for a specific world.
     * 
     * @param world The world
     * @return An unmodifiable set of banks for the world
     */
    @NotNull
    @Unmodifiable
    public Set<Object> getBanks(@NotNull World world) {
        plugin.debug("CotrBankController.getBanks() - world={}", world.getName());
        
        String worldName = world.getName();
        return bankCache.values().stream()
            .filter(bank -> {
                Optional<World> bankWorld = bank.getWorld();
                return bankWorld.isPresent() && bankWorld.get().getName().equals(worldName);
            })
            .collect(Collectors.toUnmodifiableSet());
    }
    
    /**
     * Gets a cached bank by name.
     * 
     * @param name The bank name
     * @return Optional containing the bank if cached
     */
    @NotNull
    public Optional<Object> getBank(@NotNull String name) {
        plugin.debug("CotrBankController.getBank() - name={}", name);
        return Optional.ofNullable(bankCache.get(name));
    }
    
    /**
     * Gets a cached global bank by owner UUID.
     * 
     * @param uuid The owner UUID
     * @return Optional containing the bank if cached
     */
    @NotNull
    public Optional<Object> getBank(@NotNull UUID uuid) {
        plugin.debug("CotrBankController.getBank() - uuid={}", uuid);
        return Optional.ofNullable(ownerCache.get(uuid));
    }
    
    /**
     * Gets a cached world-specific bank by owner and world.
     * 
     * @param uuid The owner UUID
     * @param world The world
     * @return Optional containing the bank if cached
     */
    @NotNull
    public Optional<Object> getBank(@NotNull UUID uuid, @NotNull World world) {
        plugin.debug("CotrBankController.getBank() - uuid={}, world={}", uuid, world.getName());
        String cacheKey = uuid + ":" + world.getName();
        return Optional.ofNullable(ownerWorldCache.get(cacheKey));
    }
    
    /**
     * Invalidates the all-banks cache.
     */
    private void invalidateAllBanksCache() {
        allBanksCache = null;
        allBanksCacheTime = 0;
    }
    
    /**
     * Clears all caches. Useful for debugging or when data might be inconsistent.
     */
    public void clearCache() {
        plugin.debug("CotrBankController.clearCache() - Clearing all caches");
        bankCache.clear();
        ownerCache.clear();
        ownerWorldCache.clear();
        invalidateAllBanksCache();
    }
}
