package org.clockworx.cotr.bank.impl;

import net.thenextlvl.service.api.Controller;
import net.thenextlvl.service.api.economy.bank.Bank;
import net.thenextlvl.service.api.economy.bank.BankController;
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
 * - Implements the full ServiceIO BankController API
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
 */
public class CotrBankController implements BankController, Controller {
    
    private final CoinOfTheRealmPlugin plugin;
    private final BankStorage storage;
    
    // In-memory cache for loaded banks
    private final Map<String, CotrBank> bankCache = new ConcurrentHashMap<>();
    private final Map<UUID, CotrBank> ownerCache = new ConcurrentHashMap<>(); // For global banks
    private final Map<String, CotrBank> ownerWorldCache = new ConcurrentHashMap<>(); // Key: "uuid:world"
    
    // Cache for all banks (lazy-loaded)
    private volatile Set<Bank> allBanksCache = null;
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
    
    @Override
    @NotNull
    public String getName() {
        return "Coin of the Realm Bank Controller";
    }
    
    @Override
    @NotNull
    public org.bukkit.plugin.Plugin getPlugin() {
        return plugin;
    }
    
    @Override
    @NotNull
    public String format(@NotNull Number amount) {
        int coins = amount.intValue();
        if (coins == 1) {
            return "1 Coin of the Realm";
        }
        return coins + " Coins of the Realm";
    }
    
    @Override
    public int fractionalDigits() {
        // Coins are whole numbers, no fractional digits
        return 0;
    }
    
    @Override
    @NotNull
    public CompletableFuture<Bank> createBank(@NotNull UUID uuid, @NotNull String name) {
        return createBank(uuid, name, null);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Bank> createBank(@NotNull UUID uuid, @NotNull String name, @Nullable World world) {
        plugin.debug("CotrBankController.createBank() - uuid={}, name={}, world={}", uuid, name, world != null ? world.getName() : "null");
        
        // Check if bank already exists
        return storage.bankExists(name).thenCompose(exists -> {
            if (exists) {
                plugin.debug("CotrBankController.createBank() - Bank already exists: {}", name);
                CompletableFuture<Bank> future = new CompletableFuture<>();
                future.completeExceptionally(new IllegalStateException("Bank with name '" + name + "' already exists"));
                return future;
            }
            
            // Check if owner already has a bank in this world (or global)
            String worldName = world != null ? world.getName() : null;
            CompletableFuture<Optional<BankRecord>> existingBankFuture = worldName != null ?
                storage.loadBankByOwner(uuid, worldName) :
                storage.loadBankByOwner(uuid);
            
            return existingBankFuture.thenCompose(existingBank -> {
                if (existingBank.isPresent()) {
                    plugin.debug("CotrBankController.createBank() - Owner already has a bank in this world");
                    CompletableFuture<Bank> future = new CompletableFuture<>();
                    future.completeExceptionally(new IllegalStateException("Owner already has a bank" + (worldName != null ? " in world " + worldName : "")));
                    return future;
                }
                
                // Create the bank
                return storage.createBank(name, uuid, worldName, BigDecimal.ZERO).thenApply(success -> {
                    if (!success) {
                        throw new IllegalStateException("Failed to create bank: " + name);
                    }
                    
                    // Create CotrBank instance and cache it
                    CotrBank bank = new CotrBank(plugin, storage, name, uuid, worldName);
                    bankCache.put(name, bank);
                    
                    // Update owner cache
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
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Bank> loadBank(@NotNull String name) {
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
    
    @Override
    @NotNull
    public CompletableFuture<Bank> loadBank(@NotNull UUID uuid) {
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
    
    @Override
    @NotNull
    public CompletableFuture<Bank> loadBank(@NotNull UUID uuid, @NotNull World world) {
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
    
    @Override
    @NotNull
    public CompletableFuture<@Unmodifiable Set<Bank>> loadBanks() {
        plugin.debug("CotrBankController.loadBanks() - Loading all banks");
        
        // Check cache
        if (allBanksCache != null && (System.currentTimeMillis() - allBanksCacheTime) < CACHE_TTL) {
            plugin.debug("CotrBankController.loadBanks() - Returning cached banks");
            return CompletableFuture.completedFuture(allBanksCache);
        }
        
        // Load from storage
        return storage.loadAllBanks().thenApply(records -> {
            Set<Bank> banks = new HashSet<>();
            
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
    
    @Override
    @NotNull
    public CompletableFuture<@Unmodifiable Set<Bank>> loadBanks(@NotNull World world) {
        plugin.debug("CotrBankController.loadBanks() - world={}", world.getName());
        
        return storage.loadBanksByWorld(world.getName()).thenApply(records -> {
            Set<Bank> banks = new HashSet<>();
            
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
    
    @Override
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
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull UUID uuid) {
        plugin.debug("CotrBankController.deleteBank() - uuid={}", uuid);
        
        // Find the bank first
        return loadBank(uuid).thenCompose(bank -> {
            if (bank == null) {
                return CompletableFuture.completedFuture(false);
            }
            return deleteBank(bank.getName());
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull UUID uuid, @NotNull World world) {
        plugin.debug("CotrBankController.deleteBank() - uuid={}, world={}", uuid, world.getName());
        
        // Find the bank first
        return loadBank(uuid, world).thenCompose(bank -> {
            if (bank == null) {
                return CompletableFuture.completedFuture(false);
            }
            return deleteBank(bank.getName());
        });
    }
    
    @Override
    @NotNull
    @Unmodifiable
    public Set<Bank> getBanks() {
        plugin.debug("CotrBankController.getBanks() - Getting all cached banks");
        
        // Return cached banks if available and fresh
        if (allBanksCache != null && (System.currentTimeMillis() - allBanksCacheTime) < CACHE_TTL) {
            return allBanksCache;
        }
        
        // Otherwise return what we have in cache
        return Collections.unmodifiableSet(new HashSet<>(bankCache.values()));
    }
    
    @Override
    @NotNull
    @Unmodifiable
    public Set<Bank> getBanks(@NotNull World world) {
        plugin.debug("CotrBankController.getBanks() - world={}", world.getName());
        
        String worldName = world.getName();
        return bankCache.values().stream()
            .filter(bank -> {
                Optional<World> bankWorld = bank.getWorld();
                return bankWorld.isPresent() && bankWorld.get().getName().equals(worldName);
            })
            .collect(Collectors.toUnmodifiableSet());
    }
    
    @Override
    @NotNull
    public Optional<Bank> getBank(@NotNull String name) {
        plugin.debug("CotrBankController.getBank() - name={}", name);
        return Optional.ofNullable(bankCache.get(name));
    }
    
    @Override
    @NotNull
    public Optional<Bank> getBank(@NotNull UUID uuid) {
        plugin.debug("CotrBankController.getBank() - uuid={}", uuid);
        return Optional.ofNullable(ownerCache.get(uuid));
    }
    
    @Override
    @NotNull
    public Optional<Bank> getBank(@NotNull UUID uuid, @NotNull World world) {
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
