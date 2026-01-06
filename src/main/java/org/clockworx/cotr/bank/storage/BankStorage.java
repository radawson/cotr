package org.clockworx.cotr.bank.storage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BankStorage - Interface for bank data persistence
 * 
 * This interface abstracts the storage layer for bank accounts, allowing
 * different implementations (database, YAML, etc.) to be used interchangeably.
 * 
 * All methods return CompletableFuture for asynchronous operations, which is
 * important for database operations that may block.
 */
public interface BankStorage {
    
    /**
     * Initializes the storage system (creates tables, loads data, etc.).
     * Must be called before any other operations.
     * 
     * @return A CompletableFuture that completes when initialization is done
     */
    @NotNull
    CompletableFuture<Void> initialize();
    
    /**
     * Closes the storage system and releases resources.
     * 
     * @return A CompletableFuture that completes when shutdown is done
     */
    @NotNull
    CompletableFuture<Void> shutdown();
    
    /**
     * Creates a new bank account.
     * 
     * @param name The bank account name (must be unique)
     * @param ownerUuid The UUID of the bank owner
     * @param worldName The world name (null for global banks)
     * @param initialBalance The initial balance (typically BigDecimal.ZERO)
     * @return A CompletableFuture that completes with true if created, false if already exists
     */
    @NotNull
    CompletableFuture<Boolean> createBank(@NotNull String name, 
                                          @NotNull UUID ownerUuid,
                                          @Nullable String worldName,
                                          @NotNull BigDecimal initialBalance);
    
    /**
     * Loads a bank account by name.
     * 
     * @param name The bank account name
     * @return A CompletableFuture that completes with the BankRecord, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<BankRecord>> loadBank(@NotNull String name);
    
    /**
     * Loads a bank account by owner UUID (for global banks).
     * 
     * @param ownerUuid The owner UUID
     * @return A CompletableFuture that completes with the BankRecord, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid);
    
    /**
     * Loads a bank account by owner UUID and world.
     * 
     * @param ownerUuid The owner UUID
     * @param worldName The world name
     * @return A CompletableFuture that completes with the BankRecord, or empty if not found
     */
    @NotNull
    CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid, @NotNull String worldName);
    
    /**
     * Loads all bank accounts.
     * 
     * @return A CompletableFuture that completes with a list of all BankRecords
     */
    @NotNull
    CompletableFuture<List<BankRecord>> loadAllBanks();
    
    /**
     * Loads all bank accounts in a specific world.
     * 
     * @param worldName The world name
     * @return A CompletableFuture that completes with a list of BankRecords in that world
     */
    @NotNull
    CompletableFuture<List<BankRecord>> loadBanksByWorld(@NotNull String worldName);
    
    /**
     * Updates a bank account's balance.
     * 
     * @param name The bank account name
     * @param newBalance The new balance
     * @return A CompletableFuture that completes with true if updated, false if bank not found
     */
    @NotNull
    CompletableFuture<Boolean> updateBalance(@NotNull String name, @NotNull BigDecimal newBalance);
    
    /**
     * Deletes a bank account.
     * 
     * @param name The bank account name
     * @return A CompletableFuture that completes with true if deleted, false if not found
     */
    @NotNull
    CompletableFuture<Boolean> deleteBank(@NotNull String name);
    
    /**
     * Checks if a bank account exists.
     * 
     * @param name The bank account name
     * @return A CompletableFuture that completes with true if exists, false otherwise
     */
    @NotNull
    CompletableFuture<Boolean> bankExists(@NotNull String name);
    
    /**
     * Gets all bank account names.
     * 
     * @return A CompletableFuture that completes with a set of all bank names
     */
    @NotNull
    CompletableFuture<Set<String>> getAllBankNames();
}
