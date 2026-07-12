package org.clockworx.cotr.storage;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;

import org.bukkit.plugin.IllegalPluginAccessException;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.entity.AccountMembershipEntity;
import org.clockworx.cotr.entity.BankEntity;
import org.clockworx.cotr.entity.DailyTransactionEntity;
import org.clockworx.cotr.entity.EmeraldExchangeRateEntity;
import org.clockworx.cotr.entity.EmeraldRegionStatsEntity;
import org.clockworx.data.flyway.FlywayMigrator;
import org.clockworx.data.hibernate.HibernateSessionManager;
import org.hibernate.Session;
import org.hibernate.exception.ConstraintViolationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Database-backed implementation of {@link BankStorage} using the shared
 * clockworx-data layer (Flyway migrations + Hibernate ORM).
 */
public class DatabaseBankStorage implements BankStorage {

    private final CoinOfTheRealmPlugin plugin;
    private HibernateSessionManager sessions;
    private final Executor asyncExecutor;
    // Dedicated daemon pool for ALL DB work. We deliberately do NOT use Bukkit's async scheduler:
    // its async tasks are only dispatched once the server starts ticking (after onEnable), so any
    // DB future joined during plugin enable (storage init, membership migration, ...) deadlocks the
    // main thread. A plain executor runs immediately at both enable- and run-time.
    private final java.util.concurrent.ExecutorService dbPool =
            java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread th = new Thread(r, "cotr-db");
                th.setDaemon(true);
                return th;
            });

    /**
     * Creates a new DatabaseBankStorage.
     *
     * @param plugin The plugin instance
     */
    public DatabaseBankStorage(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
        this.asyncExecutor = task -> {
            try {
                dbPool.execute(task);
            } catch (java.util.concurrent.RejectedExecutionException e) {
                task.run(); // pool shut down during disable — run inline
            }
        };
    }

    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            plugin.debug("DatabaseBankStorage.initialize() - Initializing database storage");

            try {
                FlywayMigrator.migrate(
                        plugin.getClass().getClassLoader(),
                        plugin.getConfigManager().getDatabaseSettings(),
                        plugin.getLogger());

                sessions = new HibernateSessionManager(
                        plugin.getConfigManager().getDatabaseSettings(),
                        List.of(
                                BankEntity.class,
                                AccountMembershipEntity.class,
                                DailyTransactionEntity.class,
                                EmeraldRegionStatsEntity.class,
                                EmeraldExchangeRateEntity.class),
                        asyncExecutor,
                        plugin.getLogger());

                plugin.getLogger().info("Database storage initialized: "
                        + plugin.getConfigManager().getDatabaseType());
                plugin.debug("DatabaseBankStorage.initialize() - Schema migrated and session manager ready");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to initialize database storage", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }); // run on the common ForkJoinPool, NOT `asyncExecutor`: during onEnable that executor
            // routes to the Bukkit scheduler, which cannot drain the task while the main thread
            // is joined on it -> startup deadlock. asyncExecutor is still used for runtime txns.
    }

    @Override
    @NotNull
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            plugin.debug("DatabaseBankStorage.shutdown() - Shutting down database storage");
            if (sessions != null) {
                sessions.shutdown();
            }
        }, asyncExecutor).whenComplete((v, ex) -> dbPool.shutdown());
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> createBank(@NotNull String name,
                                                 @NotNull UUID ownerUuid,
                                                 @Nullable String worldName,
                                                 @NotNull BigDecimal initialBalance) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.createBank() - name={}, owner={}, world={}, balance={}",
                    name, ownerUuid, worldName, initialBalance);

            long now = System.currentTimeMillis();
            BankEntity entity = new BankEntity(
                    name,
                    ownerUuid.toString(),
                    worldName,
                    initialBalance,
                    now,
                    now);
            try {
                session.persist(entity);
                plugin.debug("DatabaseBankStorage.createBank() - Bank created: true");
                return true;
            } catch (ConstraintViolationException e) {
                plugin.debug("DatabaseBankStorage.createBank() - Bank already exists: {}", name);
                return false;
            }
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBank(@NotNull String name) {
        return executeRead("Failed to load bank: " + name, session -> {
            plugin.debug("DatabaseBankStorage.loadBank() - name={}", name);

            BankEntity entity = session.createQuery(
                            "FROM BankEntity WHERE name = :name", BankEntity.class)
                    .setParameter("name", name)
                    .uniqueResult();

            if (entity == null) {
                plugin.debug("DatabaseBankStorage.loadBank() - Bank not found: {}", name);
                return Optional.empty();
            }

            BankRecord record = toBankRecord(entity);
            plugin.debug("DatabaseBankStorage.loadBank() - Bank found: {}", record);
            return Optional.of(record);
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid) {
        return executeRead("Failed to load bank by owner: " + ownerUuid, session -> {
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - owner={}, world=null", ownerUuid);

            BankEntity entity = session.createQuery(
                            "FROM BankEntity WHERE ownerUuid = :ownerUuid AND worldName IS NULL",
                            BankEntity.class)
                    .setParameter("ownerUuid", ownerUuid.toString())
                    .setMaxResults(1)
                    .uniqueResult();

            if (entity == null) {
                plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank not found for owner: {}", ownerUuid);
                return Optional.empty();
            }

            BankRecord record = toBankRecord(entity);
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank found: {}", record);
            return Optional.of(record);
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid,
                                                                  @NotNull String worldName) {
        return executeRead("Failed to load bank by owner and world: " + ownerUuid + ", " + worldName, session -> {
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - owner={}, world={}", ownerUuid, worldName);

            BankEntity entity = session.createQuery(
                            "FROM BankEntity WHERE ownerUuid = :ownerUuid AND worldName = :worldName",
                            BankEntity.class)
                    .setParameter("ownerUuid", ownerUuid.toString())
                    .setParameter("worldName", worldName)
                    .setMaxResults(1)
                    .uniqueResult();

            if (entity == null) {
                plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank not found for owner: {}, world: {}",
                        ownerUuid, worldName);
                return Optional.empty();
            }

            BankRecord record = toBankRecord(entity);
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank found: {}", record);
            return Optional.of(record);
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<BankRecord>> loadAllBanks() {
        return executeRead("Failed to load all banks", session -> {
            plugin.debug("DatabaseBankStorage.loadAllBanks() - Loading all banks");

            List<BankEntity> entities = session.createQuery("FROM BankEntity", BankEntity.class).list();
            List<BankRecord> records = new ArrayList<>(entities.size());
            for (BankEntity entity : entities) {
                records.add(toBankRecord(entity));
            }

            plugin.debug("DatabaseBankStorage.loadAllBanks() - Loaded {} banks", records.size());
            return records;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<BankRecord>> loadBanksByWorld(@NotNull String worldName) {
        return executeRead("Failed to load banks by world: " + worldName, session -> {
            plugin.debug("DatabaseBankStorage.loadBanksByWorld() - world={}", worldName);

            List<BankEntity> entities = session.createQuery(
                            "FROM BankEntity WHERE worldName = :worldName", BankEntity.class)
                    .setParameter("worldName", worldName)
                    .list();

            List<BankRecord> records = new ArrayList<>(entities.size());
            for (BankEntity entity : entities) {
                records.add(toBankRecord(entity));
            }

            plugin.debug("DatabaseBankStorage.loadBanksByWorld() - Loaded {} banks for world: {}",
                    records.size(), worldName);
            return records;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> updateBalance(@NotNull String name, @NotNull BigDecimal newBalance) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.updateBalance() - name={}, newBalance={}", name, newBalance);

            int rows = session.createMutationQuery(
                            "UPDATE BankEntity SET balance = :balance, updatedAt = :updatedAt WHERE name = :name")
                    .setParameter("balance", newBalance)
                    .setParameter("updatedAt", System.currentTimeMillis())
                    .setParameter("name", name)
                    .executeUpdate();

            boolean updated = rows > 0;
            plugin.debug("DatabaseBankStorage.updateBalance() - Balance updated: {}", updated);
            return updated;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull String name) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.deleteBank() - name={}", name);

            int rows = session.createMutationQuery("DELETE FROM BankEntity WHERE name = :name")
                    .setParameter("name", name)
                    .executeUpdate();

            boolean deleted = rows > 0;
            plugin.debug("DatabaseBankStorage.deleteBank() - Bank deleted: {}", deleted);
            return deleted;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Boolean> bankExists(@NotNull String name) {
        return executeRead("Failed to check if bank exists: " + name, session -> {
            plugin.debug("DatabaseBankStorage.bankExists() - name={}", name);

            Long count = session.createQuery(
                            "SELECT COUNT(b) FROM BankEntity b WHERE b.name = :name", Long.class)
                    .setParameter("name", name)
                    .uniqueResult();

            boolean exists = count != null && count > 0;
            plugin.debug("DatabaseBankStorage.bankExists() - Bank exists: {}", exists);
            return exists;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Set<String>> getAllBankNames() {
        return executeRead("Failed to get all bank names", session -> {
            plugin.debug("DatabaseBankStorage.getAllBankNames() - Loading all bank names");

            List<String> names = session.createQuery("SELECT b.name FROM BankEntity b", String.class).list();
            Set<String> nameSet = new HashSet<>(names);

            plugin.debug("DatabaseBankStorage.getAllBankNames() - Loaded {} bank names", nameSet.size());
            return nameSet;
        });
    }

    // ========== Account Membership Methods ==========

    /**
     * Creates a membership record in the database.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @param role The account role
     * @param createdAt Creation timestamp
     * @return A CompletableFuture that completes with true if created, false if already exists
     */
    @NotNull
    public CompletableFuture<Boolean> createMembership(@NotNull String accountName,
                                                       @NotNull UUID playerUuid,
                                                       @NotNull String role,
                                                       long createdAt) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.createMembership() - account={}, player={}, role={}",
                    accountName, playerUuid, role);

            AccountMembershipEntity entity = new AccountMembershipEntity(
                    accountName,
                    playerUuid.toString(),
                    role,
                    createdAt);
            try {
                session.persist(entity);
                plugin.debug("DatabaseBankStorage.createMembership() - Membership created: true");
                return true;
            } catch (ConstraintViolationException e) {
                plugin.debug("DatabaseBankStorage.createMembership() - Membership already exists");
                return false;
            }
        });
    }

    /**
     * Deletes a membership record.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @return A CompletableFuture that completes with true if deleted, false if not found
     */
    @NotNull
    public CompletableFuture<Boolean> deleteMembership(@NotNull String accountName, @NotNull UUID playerUuid) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.deleteMembership() - account={}, player={}", accountName, playerUuid);

            int rows = session.createMutationQuery(
                            "DELETE FROM AccountMembershipEntity "
                                    + "WHERE accountName = :accountName AND playerUuid = :playerUuid")
                    .setParameter("accountName", accountName)
                    .setParameter("playerUuid", playerUuid.toString())
                    .executeUpdate();

            boolean deleted = rows > 0;
            plugin.debug("DatabaseBankStorage.deleteMembership() - Membership deleted: {}", deleted);
            return deleted;
        });
    }

    /**
     * Deletes all memberships for an account.
     *
     * @param accountName The account name
     * @return A CompletableFuture that completes with the number of memberships deleted
     */
    @NotNull
    public CompletableFuture<Integer> deleteAllMemberships(@NotNull String accountName) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.deleteAllMemberships() - account={}", accountName);

            int rows = session.createMutationQuery(
                            "DELETE FROM AccountMembershipEntity WHERE accountName = :accountName")
                    .setParameter("accountName", accountName)
                    .executeUpdate();

            plugin.debug("DatabaseBankStorage.deleteAllMemberships() - Deleted {} memberships", rows);
            return rows;
        });
    }

    /**
     * Loads all memberships for an account.
     *
     * @param accountName The account name
     * @return A CompletableFuture that completes with a list of membership data
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadMemberships(@NotNull String accountName) {
        return executeRead("Failed to load memberships", session -> {
            plugin.debug("DatabaseBankStorage.loadMemberships() - account={}", accountName);

            List<AccountMembershipEntity> entities = session.createQuery(
                            "FROM AccountMembershipEntity WHERE accountName = :accountName",
                            AccountMembershipEntity.class)
                    .setParameter("accountName", accountName)
                    .list();

            List<MembershipRecord> records = toMembershipRecords(entities);
            plugin.debug("DatabaseBankStorage.loadMemberships() - Loaded {} memberships", records.size());
            return records;
        });
    }

    /**
     * Loads all memberships from the database.
     *
     * @return A CompletableFuture that completes with a list of all membership records
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadAllMemberships() {
        return executeRead("Failed to load all memberships", session -> {
            plugin.debug("DatabaseBankStorage.loadAllMemberships() - Loading all memberships");

            List<AccountMembershipEntity> entities = session.createQuery(
                            "FROM AccountMembershipEntity", AccountMembershipEntity.class)
                    .list();

            List<MembershipRecord> records = toMembershipRecords(entities);
            plugin.debug("DatabaseBankStorage.loadAllMemberships() - Loaded {} memberships", records.size());
            return records;
        });
    }

    /**
     * Loads all memberships for a player.
     *
     * @param playerUuid The player UUID
     * @return A CompletableFuture that completes with a list of membership data
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadMembershipsByPlayer(@NotNull UUID playerUuid) {
        return executeRead("Failed to load memberships by player", session -> {
            plugin.debug("DatabaseBankStorage.loadMembershipsByPlayer() - player={}", playerUuid);

            List<AccountMembershipEntity> entities = session.createQuery(
                            "FROM AccountMembershipEntity WHERE playerUuid = :playerUuid",
                            AccountMembershipEntity.class)
                    .setParameter("playerUuid", playerUuid.toString())
                    .list();

            List<MembershipRecord> records = toMembershipRecords(entities);
            plugin.debug("DatabaseBankStorage.loadMembershipsByPlayer() - Loaded {} memberships", records.size());
            return records;
        });
    }

    /**
     * Checks if a membership exists.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @return A CompletableFuture that completes with true if exists, false otherwise
     */
    @NotNull
    public CompletableFuture<Boolean> membershipExists(@NotNull String accountName, @NotNull UUID playerUuid) {
        return executeRead("Failed to check membership existence", session -> {
            plugin.debug("DatabaseBankStorage.membershipExists() - account={}, player={}", accountName, playerUuid);

            Long count = session.createQuery(
                            "SELECT COUNT(m) FROM AccountMembershipEntity m "
                                    + "WHERE m.accountName = :accountName AND m.playerUuid = :playerUuid",
                            Long.class)
                    .setParameter("accountName", accountName)
                    .setParameter("playerUuid", playerUuid.toString())
                    .uniqueResult();

            boolean exists = count != null && count > 0;
            plugin.debug("DatabaseBankStorage.membershipExists() - Membership exists: {}", exists);
            return exists;
        });
    }

    /**
     * Updates a membership role.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @param newRole The new role
     * @return A CompletableFuture that completes with true if updated, false if not found
     */
    @NotNull
    public CompletableFuture<Boolean> updateMembershipRole(@NotNull String accountName,
                                                           @NotNull UUID playerUuid,
                                                           @NotNull String newRole) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.updateMembershipRole() - account={}, player={}, role={}",
                    accountName, playerUuid, newRole);

            int rows = session.createMutationQuery(
                            "UPDATE AccountMembershipEntity SET role = :role "
                                    + "WHERE accountName = :accountName AND playerUuid = :playerUuid")
                    .setParameter("role", newRole)
                    .setParameter("accountName", accountName)
                    .setParameter("playerUuid", playerUuid.toString())
                    .executeUpdate();

            boolean updated = rows > 0;
            plugin.debug("DatabaseBankStorage.updateMembershipRole() - Role updated: {}", updated);
            return updated;
        });
    }

    // ========== Daily Transaction Limits Methods ==========

    /**
     * Gets the daily transaction totals for a player and account on a specific date.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @param date The date in YYYY-MM-DD format
     * @return A CompletableFuture that completes with DailyTransactionRecord, or empty if not found
     */
    @NotNull
    public CompletableFuture<Optional<DailyTransactionRecord>> getDailyTransactions(@NotNull String accountName,
                                                                                    @NotNull UUID playerUuid,
                                                                                    @NotNull String date) {
        return executeRead("Failed to get daily transactions", session -> {
            plugin.debug("DatabaseBankStorage.getDailyTransactions() - account={}, player={}, date={}",
                    accountName, playerUuid, date);

            DailyTransactionEntity entity = session.get(
                    DailyTransactionEntity.class,
                    new DailyTransactionEntity.PK(accountName, playerUuid.toString(), date));

            if (entity == null) {
                plugin.debug("DatabaseBankStorage.getDailyTransactions() - No record found");
                return Optional.empty();
            }

            DailyTransactionRecord record = new DailyTransactionRecord(
                    entity.getAccountName(),
                    UUID.fromString(entity.getPlayerUuid()),
                    entity.getDate(),
                    entity.getDepositTotal(),
                    entity.getWithdrawTotal());
            plugin.debug("DatabaseBankStorage.getDailyTransactions() - Found record: deposit={}, withdraw={}",
                    record.getDepositTotal(), record.getWithdrawTotal());
            return Optional.of(record);
        });
    }

    /**
     * Updates the daily transaction totals for a player and account.
     * Creates a new record if one doesn't exist for the date.
     *
     * @param accountName The account name
     * @param playerUuid The player UUID
     * @param date The date in YYYY-MM-DD format
     * @param depositAmount The deposit amount to add (can be 0)
     * @param withdrawAmount The withdrawal amount to add (can be 0)
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> updateDailyTransactions(@NotNull String accountName,
                                                              @NotNull UUID playerUuid,
                                                              @NotNull String date,
                                                              int depositAmount,
                                                              int withdrawAmount) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.updateDailyTransactions() - account={}, player={}, date={}, deposit={}, withdraw={}",
                    accountName, playerUuid, date, depositAmount, withdrawAmount);

            DailyTransactionEntity.PK pk = new DailyTransactionEntity.PK(
                    accountName, playerUuid.toString(), date);
            DailyTransactionEntity entity = session.get(DailyTransactionEntity.class, pk);

            if (entity != null) {
                entity.setDepositTotal(entity.getDepositTotal() + depositAmount);
                entity.setWithdrawTotal(entity.getWithdrawTotal() + withdrawAmount);
                session.merge(entity);
                plugin.debug("DatabaseBankStorage.updateDailyTransactions() - Updated existing record");
                return true;
            }

            DailyTransactionEntity newEntity = new DailyTransactionEntity(
                    accountName,
                    playerUuid.toString(),
                    date,
                    depositAmount,
                    withdrawAmount);
            session.persist(newEntity);
            plugin.debug("DatabaseBankStorage.updateDailyTransactions() - Created new record: true");
            return true;
        });
    }

    // ========== Emerald Exchange Tracking Methods ==========

    /**
     * Increments emerald tracking counters for a region.
     * All values are additive deltas (use 0 for fields you are not changing).
     *
     * @param regionId The region ID
     * @param minedDelta Delta for mined emeralds
     * @param lootDelta Delta for loot emeralds
     * @param mobDelta Delta for mob drop emeralds
     * @param tradingDelta Delta for emeralds gained via trading
     * @param bankInDelta Delta for emeralds deposited into the bank
     * @param bankOutDelta Delta for emeralds withdrawn from the bank
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> incrementEmeraldStats(@NotNull String regionId,
                                                            int minedDelta,
                                                            int lootDelta,
                                                            int mobDelta,
                                                            int tradingDelta,
                                                            int bankInDelta,
                                                            int bankOutDelta) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.incrementEmeraldStats() - region={}, mined={}, loot={}, mob={}, trade={}, bankIn={}, bankOut={}",
                    regionId, minedDelta, lootDelta, mobDelta, tradingDelta, bankInDelta, bankOutDelta);

            long now = System.currentTimeMillis();
            EmeraldRegionStatsEntity entity = session.get(EmeraldRegionStatsEntity.class, regionId);

            if (entity == null) {
                entity = new EmeraldRegionStatsEntity(regionId);
                entity.setMinedTotal(minedDelta);
                entity.setLootTotal(lootDelta);
                entity.setMobTotal(mobDelta);
                entity.setTradingTotal(tradingDelta);
                entity.setBankInTotal(bankInDelta);
                entity.setBankOutTotal(bankOutDelta);
                entity.setUpdatedAt(now);
                session.persist(entity);
            } else {
                entity.setMinedTotal(entity.getMinedTotal() + minedDelta);
                entity.setLootTotal(entity.getLootTotal() + lootDelta);
                entity.setMobTotal(entity.getMobTotal() + mobDelta);
                entity.setTradingTotal(entity.getTradingTotal() + tradingDelta);
                entity.setBankInTotal(entity.getBankInTotal() + bankInDelta);
                entity.setBankOutTotal(entity.getBankOutTotal() + bankOutDelta);
                entity.setUpdatedAt(now);
                session.merge(entity);
            }

            plugin.debug("DatabaseBankStorage.incrementEmeraldStats() - Updated: true");
            return true;
        });
    }

    /**
     * Loads emerald region stats (returns zeroed totals if none exist).
     *
     * @param regionId The region ID
     * @return A CompletableFuture with EmeraldRegionStats
     */
    @NotNull
    public CompletableFuture<EmeraldRegionStats> getEmeraldRegionStats(@NotNull String regionId) {
        return executeRead("Failed to load emerald stats", session -> {
            plugin.debug("DatabaseBankStorage.getEmeraldRegionStats() - region={}", regionId);

            EmeraldRegionStatsEntity entity = session.get(EmeraldRegionStatsEntity.class, regionId);
            if (entity == null) {
                return EmeraldRegionStats.empty(regionId);
            }

            return new EmeraldRegionStats(
                    entity.getRegionId(),
                    entity.getMinedTotal(),
                    entity.getLootTotal(),
                    entity.getMobTotal(),
                    entity.getTradingTotal(),
                    entity.getBankInTotal(),
                    entity.getBankOutTotal(),
                    entity.getUpdatedAt());
        });
    }

    /**
     * Stores the current exchange rate for a region.
     *
     * @param regionId The region ID
     * @param rate The current exchange rate (coins per emerald)
     * @return A CompletableFuture that completes with true if successful
     */
    @NotNull
    public CompletableFuture<Boolean> upsertEmeraldExchangeRate(@NotNull String regionId, int rate) {
        return sessions.executeTransaction(session -> {
            plugin.debug("DatabaseBankStorage.upsertEmeraldExchangeRate() - region={}, rate={}", regionId, rate);

            long now = System.currentTimeMillis();
            EmeraldExchangeRateEntity entity = session.get(EmeraldExchangeRateEntity.class, regionId);

            if (entity == null) {
                entity = new EmeraldExchangeRateEntity(regionId, rate, now);
                session.persist(entity);
            } else {
                entity.setCurrentRate(rate);
                entity.setUpdatedAt(now);
                session.merge(entity);
            }

            plugin.debug("DatabaseBankStorage.upsertEmeraldExchangeRate() - Updated: true");
            return true;
        });
    }

    /**
     * Loads the stored exchange rate for a region.
     *
     * @param regionId The region ID
     * @return A CompletableFuture with an Optional exchange rate record
     */
    @NotNull
    public CompletableFuture<Optional<EmeraldExchangeRateRecord>> getEmeraldExchangeRate(@NotNull String regionId) {
        return executeRead("Failed to load exchange rate", session -> {
            plugin.debug("DatabaseBankStorage.getEmeraldExchangeRate() - region={}", regionId);

            EmeraldExchangeRateEntity entity = session.get(EmeraldExchangeRateEntity.class, regionId);
            if (entity == null) {
                return Optional.empty();
            }

            return Optional.of(new EmeraldExchangeRateRecord(
                    entity.getRegionId(),
                    entity.getCurrentRate(),
                    entity.getUpdatedAt()));
        });
    }

    /**
     * Data class for daily transaction records.
     */
    public static class DailyTransactionRecord {
        private final String accountName;
        private final UUID playerUuid;
        private final String date;
        private final int depositTotal;
        private final int withdrawTotal;

        public DailyTransactionRecord(@NotNull String accountName, @NotNull UUID playerUuid,
                                      @NotNull String date, int depositTotal, int withdrawTotal) {
            this.accountName = accountName;
            this.playerUuid = playerUuid;
            this.date = date;
            this.depositTotal = depositTotal;
            this.withdrawTotal = withdrawTotal;
        }

        @NotNull
        public String getAccountName() {
            return accountName;
        }

        @NotNull
        public UUID getPlayerUuid() {
            return playerUuid;
        }

        @NotNull
        public String getDate() {
            return date;
        }

        public int getDepositTotal() {
            return depositTotal;
        }

        public int getWithdrawTotal() {
            return withdrawTotal;
        }
    }

    /**
     * Data class for emerald region tracking totals.
     */
    public static class EmeraldRegionStats {
        private final String regionId;
        private final int minedTotal;
        private final int lootTotal;
        private final int mobTotal;
        private final int tradingTotal;
        private final int bankInTotal;
        private final int bankOutTotal;
        private final long updatedAt;

        public EmeraldRegionStats(@NotNull String regionId,
                                  int minedTotal,
                                  int lootTotal,
                                  int mobTotal,
                                  int tradingTotal,
                                  int bankInTotal,
                                  int bankOutTotal,
                                  long updatedAt) {
            this.regionId = regionId;
            this.minedTotal = minedTotal;
            this.lootTotal = lootTotal;
            this.mobTotal = mobTotal;
            this.tradingTotal = tradingTotal;
            this.bankInTotal = bankInTotal;
            this.bankOutTotal = bankOutTotal;
            this.updatedAt = updatedAt;
        }

        @NotNull
        public static EmeraldRegionStats empty(@NotNull String regionId) {
            return new EmeraldRegionStats(regionId, 0, 0, 0, 0, 0, 0, System.currentTimeMillis());
        }

        @NotNull
        public String getRegionId() {
            return regionId;
        }

        public int getMinedTotal() {
            return minedTotal;
        }

        public int getLootTotal() {
            return lootTotal;
        }

        public int getMobTotal() {
            return mobTotal;
        }

        public int getTradingTotal() {
            return tradingTotal;
        }

        public int getBankInTotal() {
            return bankInTotal;
        }

        public int getBankOutTotal() {
            return bankOutTotal;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }

    /**
     * Data class for stored exchange rates.
     */
    public static class EmeraldExchangeRateRecord {
        private final String regionId;
        private final int currentRate;
        private final long updatedAt;

        public EmeraldExchangeRateRecord(@NotNull String regionId, int currentRate, long updatedAt) {
            this.regionId = regionId;
            this.currentRate = currentRate;
            this.updatedAt = updatedAt;
        }

        @NotNull
        public String getRegionId() {
            return regionId;
        }

        public int getCurrentRate() {
            return currentRate;
        }

        public long getUpdatedAt() {
            return updatedAt;
        }
    }

    /**
     * Data class for membership records.
     */
    public static class MembershipRecord {
        private final String accountName;
        private final UUID playerUuid;
        private final String role;
        private final long createdAt;

        public MembershipRecord(@NotNull String accountName, @NotNull UUID playerUuid,
                                @NotNull String role, long createdAt) {
            this.accountName = accountName;
            this.playerUuid = playerUuid;
            this.role = role;
            this.createdAt = createdAt;
        }

        @NotNull
        public String getAccountName() {
            return accountName;
        }

        @NotNull
        public UUID getPlayerUuid() {
            return playerUuid;
        }

        @NotNull
        public String getRole() {
            return role;
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Executes a read-only database operation asynchronously.
     */
    private <T> CompletableFuture<T> executeRead(String errorMessage, HibernateSessionManager.TransactionFunction<T> function) {
        return CompletableFuture.supplyAsync(() -> {
            try (Session session = sessions.getSessionFactory().openSession()) {
                return function.apply(session);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, errorMessage, e);
                throw new RuntimeException(errorMessage, e);
            }
        }, asyncExecutor);
    }

    @NotNull
    private BankRecord toBankRecord(@NotNull BankEntity entity) {
        return new BankRecord(
                entity.getName(),
                UUID.fromString(entity.getOwnerUuid()),
                entity.getWorldName(),
                entity.getBalance(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    @NotNull
    private List<MembershipRecord> toMembershipRecords(@NotNull List<AccountMembershipEntity> entities) {
        List<MembershipRecord> records = new ArrayList<>(entities.size());
        for (AccountMembershipEntity entity : entities) {
            records.add(new MembershipRecord(
                    entity.getAccountName(),
                    UUID.fromString(entity.getPlayerUuid()),
                    entity.getRole(),
                    entity.getCreatedAt()));
        }
        return records;
    }
}
