package org.clockworx.cotr.bank.storage;

// Use original package names - shadowJar will relocate these at build time
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * DatabaseBankStorage - Database-backed implementation of BankStorage
 * 
 * This implementation uses SQLite (with optional MySQL support) to store bank data.
 * Uses HikariCP for connection pooling and provides asynchronous operations.
 */
public class DatabaseBankStorage implements BankStorage {
    
    private final CoinOfTheRealmPlugin plugin;
    private final String databaseType;
    private final String connectionString;
    private final String tablePrefix;
    private final String username;
    private final String password;
    private HikariDataSource dataSource;
    private ExecutorService executorService;
    private static final int CURRENT_SCHEMA_VERSION = 3;
    
    /**
     * Creates a new DatabaseBankStorage.
     * 
     * @param plugin The plugin instance
     * @param databaseType The database type ("sqlite" or "mysql")
     * @param connectionString The database connection string
     * @param tablePrefix The table prefix (empty string for no prefix)
     * @param username The database username (for MySQL, null for SQLite)
     * @param password The database password (for MySQL, null for SQLite)
     */
    public DatabaseBankStorage(@NotNull CoinOfTheRealmPlugin plugin,
                              @NotNull String databaseType,
                              @NotNull String connectionString,
                              @NotNull String tablePrefix,
                              @Nullable String username,
                              @Nullable String password) {
        this.plugin = plugin;
        this.databaseType = databaseType.toLowerCase();
        this.connectionString = connectionString;
        this.tablePrefix = tablePrefix != null ? tablePrefix : "";
        this.username = username;
        this.password = password;
    }
    
    /**
     * Gets the table name with prefix applied.
     * 
     * @param baseName The base table name (e.g., "banks")
     * @return The prefixed table name (e.g., "cotr_banks" if prefix is "cotr_")
     */
    @NotNull
    private String getTableName(@NotNull String baseName) {
        return tablePrefix + baseName;
    }
    
    @Override
    @NotNull
    public CompletableFuture<Void> initialize() {
        // Create executor service first (before using it in runAsync)
        executorService = Executors.newFixedThreadPool(4);
        
        return CompletableFuture.runAsync(() -> {
            plugin.debug("DatabaseBankStorage.initialize() - Initializing database storage: type={}", databaseType);
            
            try {
                
                // Setup HikariCP connection pool
                HikariConfig config = new HikariConfig();
                
                if ("sqlite".equals(databaseType)) {
                    String sqliteUrl = "jdbc:sqlite:" + connectionString;
                    config.setJdbcUrl(sqliteUrl);
                    // Use relocated class name (relocated by shadowJar)
                    config.setDriverClassName("org.clockworx.cotr.libs.sqlite.JDBC");
                    plugin.debug("DatabaseBankStorage.initialize() - Final SQLite connection URL: {}", sqliteUrl);
                    plugin.getLogger().info("Connecting to SQLite database: " + connectionString);
                    config.setMaximumPoolSize(1); // SQLite doesn't support multiple connections well
                    config.setConnectionTimeout(30000);
                    config.setIdleTimeout(600000);
                    config.setMaxLifetime(1800000);
                } else if ("mysql".equals(databaseType)) {
                    // Connection string already includes useSSL=false&autoReconnect=true from ConfigManager
                    config.setJdbcUrl(connectionString);
                    // Use relocated class name (relocated by shadowJar)
                    config.setDriverClassName("org.clockworx.cotr.libs.mysql.jdbc.Driver");
                    
                    // Set username and password (required for MySQL)
                    if (username != null && !username.isEmpty()) {
                        config.setUsername(username);
                        plugin.debug("DatabaseBankStorage.initialize() - MySQL username set: '{}'", username);
                    } else {
                        plugin.getLogger().warning("MySQL username is empty or null. Connection may fail.");
                    }
                    
                    if (password != null) {
                        config.setPassword(password);
                        plugin.debug("DatabaseBankStorage.initialize() - MySQL password set: {}",
                            password.isEmpty() ? "(empty)" : "***");
                    } else {
                        plugin.getLogger().warning("MySQL password is null. Connection may fail.");
                    }
                    
                    // Log final connection URL for debugging (without password)
                    String debugUrl = connectionString;
                    if (username != null && !username.isEmpty()) {
                        debugUrl = debugUrl.replaceFirst("jdbc:mysql://", 
                            "jdbc:mysql://" + username + "@");
                    }
                    plugin.debug("DatabaseBankStorage.initialize() - Final MySQL connection URL: {}", debugUrl);
                    plugin.getLogger().info("Connecting to MySQL database: " + 
                        connectionString.replaceFirst("jdbc:mysql://", "").split("\\?")[0]);
                    
                    config.setMaximumPoolSize(10);
                    config.setConnectionTimeout(30000);
                    config.setIdleTimeout(600000);
                    config.setMaxLifetime(1800000);
                } else {
                    throw new IllegalArgumentException("Unsupported database type: " + databaseType);
                }
                
                dataSource = new HikariDataSource(config);
                plugin.debug("DatabaseBankStorage.initialize() - Connection pool created");
                
                // Create schema
                createSchema();
                plugin.debug("DatabaseBankStorage.initialize() - Schema created/verified");
                
                plugin.getLogger().info("Database storage initialized: " + databaseType);
            } catch (Exception e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to initialize database storage", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }, executorService);
    }
    
    /**
     * Creates the database schema if it doesn't exist.
     */
    private void createSchema() throws SQLException {
        plugin.debug("DatabaseBankStorage.createSchema() - Creating database schema with prefix: '{}'", tablePrefix);
        
        String banksTable = getTableName("banks");
        String membershipsTable = getTableName("account_memberships");
        String dailyTransactionsTable = getTableName("daily_transactions");
        String schemaVersionTable = getTableName("schema_version");
        String emeraldStatsTable = getTableName("emerald_region_stats");
        String emeraldRatesTable = getTableName("emerald_exchange_rates");
        
        String createTableSql;
        if ("sqlite".equals(databaseType)) {
            createTableSql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    owner_uuid VARCHAR(36) NOT NULL,
                    world_name VARCHAR(255),
                    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS idx_%s_owner ON %s(owner_uuid);
                CREATE INDEX IF NOT EXISTS idx_%s_world ON %s(world_name);
                CREATE INDEX IF NOT EXISTS idx_%s_owner_world ON %s(owner_uuid, world_name);
                CREATE TABLE IF NOT EXISTS %s (
                    account_name VARCHAR(255) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (account_name, player_uuid)
                );
                CREATE INDEX IF NOT EXISTS idx_%s_account ON %s(account_name);
                CREATE INDEX IF NOT EXISTS idx_%s_player ON %s(player_uuid);
                CREATE TABLE IF NOT EXISTS %s (
                    account_name VARCHAR(255) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    date VARCHAR(10) NOT NULL,
                    deposit_total INT NOT NULL DEFAULT 0,
                    withdraw_total INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (account_name, player_uuid, date)
                );
                CREATE INDEX IF NOT EXISTS idx_%s_account_date ON %s(account_name, date);
                CREATE INDEX IF NOT EXISTS idx_%s_player_date ON %s(player_uuid, date);
                CREATE TABLE IF NOT EXISTS %s (
                    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                    mined_total INT NOT NULL DEFAULT 0,
                    loot_total INT NOT NULL DEFAULT 0,
                    mob_total INT NOT NULL DEFAULT 0,
                    trading_total INT NOT NULL DEFAULT 0,
                    bank_in_total INT NOT NULL DEFAULT 0,
                    bank_out_total INT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL
                );
                CREATE TABLE IF NOT EXISTS %s (
                    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                    current_rate INT NOT NULL,
                    updated_at BIGINT NOT NULL
                );
                """, banksTable, banksTable, banksTable, banksTable, banksTable, banksTable, banksTable,
                membershipsTable, membershipsTable, membershipsTable, membershipsTable, membershipsTable,
                dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable,
                emeraldStatsTable, emeraldRatesTable);
        } else {
            // MySQL
            createTableSql = String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    id INT AUTO_INCREMENT PRIMARY KEY,
                    name VARCHAR(255) NOT NULL UNIQUE,
                    owner_uuid VARCHAR(36) NOT NULL,
                    world_name VARCHAR(255),
                    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
                    created_at BIGINT NOT NULL,
                    updated_at BIGINT NOT NULL,
                    INDEX idx_%s_owner (owner_uuid),
                    INDEX idx_%s_world (world_name),
                    INDEX idx_%s_owner_world (owner_uuid, world_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                CREATE TABLE IF NOT EXISTS %s (
                    account_name VARCHAR(255) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    role VARCHAR(20) NOT NULL,
                    created_at BIGINT NOT NULL,
                    PRIMARY KEY (account_name, player_uuid),
                    INDEX idx_%s_account (account_name),
                    INDEX idx_%s_player (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                CREATE TABLE IF NOT EXISTS %s (
                    account_name VARCHAR(255) NOT NULL,
                    player_uuid VARCHAR(36) NOT NULL,
                    date VARCHAR(10) NOT NULL,
                    deposit_total INT NOT NULL DEFAULT 0,
                    withdraw_total INT NOT NULL DEFAULT 0,
                    PRIMARY KEY (account_name, player_uuid, date),
                    INDEX idx_%s_account_date (account_name, date),
                    INDEX idx_%s_player_date (player_uuid, date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                CREATE TABLE IF NOT EXISTS %s (
                    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                    mined_total INT NOT NULL DEFAULT 0,
                    loot_total INT NOT NULL DEFAULT 0,
                    mob_total INT NOT NULL DEFAULT 0,
                    trading_total INT NOT NULL DEFAULT 0,
                    bank_in_total INT NOT NULL DEFAULT 0,
                    bank_out_total INT NOT NULL DEFAULT 0,
                    updated_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                CREATE TABLE IF NOT EXISTS %s (
                    region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                    current_rate INT NOT NULL,
                    updated_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """, banksTable, banksTable, banksTable, banksTable, membershipsTable, membershipsTable, membershipsTable,
                dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable,
                emeraldStatsTable, emeraldRatesTable);
        }
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Execute each statement separately for SQLite compatibility
            String[] statements = createTableSql.split(";");
            for (String sql : statements) {
                sql = sql.trim();
                if (!sql.isEmpty()) {
                    stmt.execute(sql);
                }
            }
            
            // Create schema version table
            String schemaVersionSql = "sqlite".equals(databaseType) ?
                String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    version INT NOT NULL PRIMARY KEY,
                    applied_at BIGINT NOT NULL
                );
                """, schemaVersionTable) :
                String.format("""
                CREATE TABLE IF NOT EXISTS %s (
                    version INT NOT NULL PRIMARY KEY,
                    applied_at BIGINT NOT NULL
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """, schemaVersionTable);
            
            stmt.execute(schemaVersionSql);
            
            // Check and update schema version
            ensureSchemaVersion();
        }
    }
    
    /**
     * Ensures the schema version is set correctly and runs migrations if needed.
     */
    private void ensureSchemaVersion() throws SQLException {
        String schemaVersionTable = getTableName("schema_version");
        try (Connection conn = dataSource.getConnection()) {
            // Get current schema version
            int currentVersion = 0;
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT MAX(version) as max_version FROM " + schemaVersionTable)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next() && !rs.wasNull()) {
                        currentVersion = rs.getInt("max_version");
                    }
                }
            }
            
            plugin.debug("DatabaseBankStorage.ensureSchemaVersion() - Current version: {}, Target version: {}", 
                currentVersion, CURRENT_SCHEMA_VERSION);
            
            // Run migrations if needed
            if (currentVersion < CURRENT_SCHEMA_VERSION) {
                runMigrations(conn, currentVersion, CURRENT_SCHEMA_VERSION);
            }
            
            // Ensure current version is recorded
            if (currentVersion < CURRENT_SCHEMA_VERSION) {
                try (PreparedStatement insertStmt = conn.prepareStatement(
                        "INSERT INTO " + schemaVersionTable + " (version, applied_at) VALUES (?, ?)")) {
                    insertStmt.setInt(1, CURRENT_SCHEMA_VERSION);
                    insertStmt.setLong(2, System.currentTimeMillis());
                    insertStmt.executeUpdate();
                    plugin.debug("DatabaseBankStorage.ensureSchemaVersion() - Schema version {} recorded", CURRENT_SCHEMA_VERSION);
                }
            }
        }
    }
    
    /**
     * Runs database migrations from one version to another.
     * 
     * @param conn The database connection
     * @param fromVersion The current schema version
     * @param toVersion The target schema version
     */
    private void runMigrations(Connection conn, int fromVersion, int toVersion) throws SQLException {
        plugin.debug("DatabaseBankStorage.runMigrations() - Migrating from version {} to {}", fromVersion, toVersion);
        
        // Migration from version 1 to 2: Add daily_transactions table
        if (fromVersion < 2 && toVersion >= 2) {
            plugin.getLogger().info("Running database migration: version 1 -> 2 (adding daily_transactions table)");
            
            String dailyTransactionsTable = getTableName("daily_transactions");
            String createTableSql;
            
            if ("sqlite".equals(databaseType)) {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        account_name VARCHAR(255) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        date VARCHAR(10) NOT NULL,
                        deposit_total INT NOT NULL DEFAULT 0,
                        withdraw_total INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (account_name, player_uuid, date)
                    );
                    CREATE INDEX IF NOT EXISTS idx_%s_account_date ON %s(account_name, date);
                    CREATE INDEX IF NOT EXISTS idx_%s_player_date ON %s(player_uuid, date);
                    """, dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable, 
                    dailyTransactionsTable, dailyTransactionsTable);
            } else {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        account_name VARCHAR(255) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        date VARCHAR(10) NOT NULL,
                        deposit_total INT NOT NULL DEFAULT 0,
                        withdraw_total INT NOT NULL DEFAULT 0,
                        PRIMARY KEY (account_name, player_uuid, date),
                        INDEX idx_%s_account_date (account_name, date),
                        INDEX idx_%s_player_date (player_uuid, date)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """, dailyTransactionsTable, dailyTransactionsTable, dailyTransactionsTable);
            }
            
            try (Statement stmt = conn.createStatement()) {
                String[] statements = createTableSql.split(";");
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
                plugin.getLogger().info("Migration 1->2 completed successfully");
            }
        }
        
        // Migration from version 2 to 3: Add emerald exchange tracking tables
        if (fromVersion < 3 && toVersion >= 3) {
            plugin.getLogger().info("Running database migration: version 2 -> 3 (adding emerald exchange tables)");
            
            String emeraldStatsTable = getTableName("emerald_region_stats");
            String emeraldRatesTable = getTableName("emerald_exchange_rates");
            String createTableSql;
            
            if ("sqlite".equals(databaseType)) {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        mined_total INT NOT NULL DEFAULT 0,
                        loot_total INT NOT NULL DEFAULT 0,
                        mob_total INT NOT NULL DEFAULT 0,
                        trading_total INT NOT NULL DEFAULT 0,
                        bank_in_total INT NOT NULL DEFAULT 0,
                        bank_out_total INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL
                    );
                    CREATE TABLE IF NOT EXISTS %s (
                        region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        current_rate INT NOT NULL,
                        updated_at BIGINT NOT NULL
                    );
                    """, emeraldStatsTable, emeraldRatesTable);
            } else {
                createTableSql = String.format("""
                    CREATE TABLE IF NOT EXISTS %s (
                        region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        mined_total INT NOT NULL DEFAULT 0,
                        loot_total INT NOT NULL DEFAULT 0,
                        mob_total INT NOT NULL DEFAULT 0,
                        trading_total INT NOT NULL DEFAULT 0,
                        bank_in_total INT NOT NULL DEFAULT 0,
                        bank_out_total INT NOT NULL DEFAULT 0,
                        updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    CREATE TABLE IF NOT EXISTS %s (
                        region_id VARCHAR(255) NOT NULL PRIMARY KEY,
                        current_rate INT NOT NULL,
                        updated_at BIGINT NOT NULL
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                    """, emeraldStatsTable, emeraldRatesTable);
            }
            
            try (Statement stmt = conn.createStatement()) {
                String[] statements = createTableSql.split(";");
                for (String sql : statements) {
                    sql = sql.trim();
                    if (!sql.isEmpty()) {
                        stmt.execute(sql);
                    }
                }
                plugin.getLogger().info("Migration 2->3 completed successfully");
            }
        }
    }
    
    @Override
    @NotNull
    public CompletableFuture<Void> shutdown() {
        return CompletableFuture.runAsync(() -> {
            plugin.debug("DatabaseBankStorage.shutdown() - Shutting down database storage");
            
            if (dataSource != null) {
                dataSource.close();
                plugin.debug("DatabaseBankStorage.shutdown() - Connection pool closed");
            }
            
            if (executorService != null) {
                executorService.shutdown();
                plugin.debug("DatabaseBankStorage.shutdown() - Executor service shut down");
            }
        });
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> createBank(@NotNull String name, 
                                                 @NotNull UUID ownerUuid,
                                                 @Nullable String worldName,
                                                 @NotNull BigDecimal initialBalance) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.createBank() - name={}, owner={}, world={}, balance={}", 
                name, ownerUuid, worldName, initialBalance);
            
            String banksTable = getTableName("banks");
            String sql = "INSERT INTO " + banksTable + " (name, owner_uuid, world_name, balance, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                stmt.setString(2, ownerUuid.toString());
                stmt.setString(3, worldName);
                stmt.setBigDecimal(4, initialBalance);
                long now = System.currentTimeMillis();
                stmt.setLong(5, now);
                stmt.setLong(6, now);
                
                int rows = stmt.executeUpdate();
                boolean created = rows > 0;
                plugin.debug("DatabaseBankStorage.createBank() - Bank created: {}", created);
                return created;
            } catch (SQLException e) {
                // Check if it's a duplicate key error
                if (e.getErrorCode() == 19 || e.getSQLState().equals("23505") || 
                    e.getMessage().contains("UNIQUE constraint") || e.getMessage().contains("Duplicate entry")) {
                    plugin.debug("DatabaseBankStorage.createBank() - Bank already exists: {}", name);
                    return false;
                }
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create bank: " + name, e);
                throw new RuntimeException("Failed to create bank", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBank(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadBank() - name={}", name);
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name, owner_uuid, world_name, balance, created_at, updated_at FROM " + banksTable + " WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        BankRecord record = mapResultSetToRecord(rs);
                        plugin.debug("DatabaseBankStorage.loadBank() - Bank found: {}", record);
                        return Optional.of(record);
                    } else {
                        plugin.debug("DatabaseBankStorage.loadBank() - Bank not found: {}", name);
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load bank: " + name, e);
                throw new RuntimeException("Failed to load bank", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - owner={}, world=null", ownerUuid);
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name, owner_uuid, world_name, balance, created_at, updated_at FROM " + banksTable + " WHERE owner_uuid = ? AND world_name IS NULL LIMIT 1";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, ownerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        BankRecord record = mapResultSetToRecord(rs);
                        plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank found: {}", record);
                        return Optional.of(record);
                    } else {
                        plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank not found for owner: {}", ownerUuid);
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load bank by owner: " + ownerUuid, e);
                throw new RuntimeException("Failed to load bank by owner", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Optional<BankRecord>> loadBankByOwner(@NotNull UUID ownerUuid, @NotNull String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadBankByOwner() - owner={}, world={}", ownerUuid, worldName);
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name, owner_uuid, world_name, balance, created_at, updated_at FROM " + banksTable + " WHERE owner_uuid = ? AND world_name = ? LIMIT 1";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, ownerUuid.toString());
                stmt.setString(2, worldName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        BankRecord record = mapResultSetToRecord(rs);
                        plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank found: {}", record);
                        return Optional.of(record);
                    } else {
                        plugin.debug("DatabaseBankStorage.loadBankByOwner() - Bank not found for owner: {}, world: {}", ownerUuid, worldName);
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load bank by owner and world: " + ownerUuid + ", " + worldName, e);
                throw new RuntimeException("Failed to load bank by owner and world", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<List<BankRecord>> loadAllBanks() {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadAllBanks() - Loading all banks");
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name, owner_uuid, world_name, balance, created_at, updated_at FROM " + banksTable;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                List<BankRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(mapResultSetToRecord(rs));
                }
                
                plugin.debug("DatabaseBankStorage.loadAllBanks() - Loaded {} banks", records.size());
                return records;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load all banks", e);
                throw new RuntimeException("Failed to load all banks", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<List<BankRecord>> loadBanksByWorld(@NotNull String worldName) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadBanksByWorld() - world={}", worldName);
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name, owner_uuid, world_name, balance, created_at, updated_at FROM " + banksTable + " WHERE world_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, worldName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<BankRecord> records = new ArrayList<>();
                    while (rs.next()) {
                        records.add(mapResultSetToRecord(rs));
                    }
                    
                    plugin.debug("DatabaseBankStorage.loadBanksByWorld() - Loaded {} banks for world: {}", records.size(), worldName);
                    return records;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load banks by world: " + worldName, e);
                throw new RuntimeException("Failed to load banks by world", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> updateBalance(@NotNull String name, @NotNull BigDecimal newBalance) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.updateBalance() - name={}, newBalance={}", name, newBalance);
            
            String banksTable = getTableName("banks");
            String sql = "UPDATE " + banksTable + " SET balance = ?, updated_at = ? WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setBigDecimal(1, newBalance);
                stmt.setLong(2, System.currentTimeMillis());
                stmt.setString(3, name);
                
                int rows = stmt.executeUpdate();
                boolean updated = rows > 0;
                plugin.debug("DatabaseBankStorage.updateBalance() - Balance updated: {}", updated);
                return updated;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update balance for bank: " + name, e);
                throw new RuntimeException("Failed to update balance", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> deleteBank(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.deleteBank() - name={}", name);
            
            String banksTable = getTableName("banks");
            String sql = "DELETE FROM " + banksTable + " WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                
                int rows = stmt.executeUpdate();
                boolean deleted = rows > 0;
                plugin.debug("DatabaseBankStorage.deleteBank() - Bank deleted: {}", deleted);
                return deleted;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete bank: " + name, e);
                throw new RuntimeException("Failed to delete bank", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Boolean> bankExists(@NotNull String name) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.bankExists() - name={}", name);
            
            String banksTable = getTableName("banks");
            String sql = "SELECT 1 FROM " + banksTable + " WHERE name = ? LIMIT 1";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, name);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean exists = rs.next();
                    plugin.debug("DatabaseBankStorage.bankExists() - Bank exists: {}", exists);
                    return exists;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to check if bank exists: " + name, e);
                throw new RuntimeException("Failed to check if bank exists", e);
            }
        }, executorService);
    }
    
    @Override
    @NotNull
    public CompletableFuture<Set<String>> getAllBankNames() {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.getAllBankNames() - Loading all bank names");
            
            String banksTable = getTableName("banks");
            String sql = "SELECT name FROM " + banksTable;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                Set<String> names = new HashSet<>();
                while (rs.next()) {
                    names.add(rs.getString("name"));
                }
                
                plugin.debug("DatabaseBankStorage.getAllBankNames() - Loaded {} bank names", names.size());
                return names;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to get all bank names", e);
                throw new RuntimeException("Failed to get all bank names", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.createMembership() - account={}, player={}, role={}", 
                accountName, playerUuid, role);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "INSERT INTO " + membershipsTable + " (account_name, player_uuid, role, created_at) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, role);
                stmt.setLong(4, createdAt);
                
                int rows = stmt.executeUpdate();
                boolean created = rows > 0;
                plugin.debug("DatabaseBankStorage.createMembership() - Membership created: {}", created);
                return created;
            } catch (SQLException e) {
                // Check if it's a duplicate key error
                if (e.getErrorCode() == 19 || e.getSQLState().equals("23505") || 
                    e.getMessage().contains("UNIQUE constraint") || e.getMessage().contains("Duplicate entry")) {
                    plugin.debug("DatabaseBankStorage.createMembership() - Membership already exists");
                    return false;
                }
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to create membership", e);
                throw new RuntimeException("Failed to create membership", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.deleteMembership() - account={}, player={}", accountName, playerUuid);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "DELETE FROM " + membershipsTable + " WHERE account_name = ? AND player_uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                stmt.setString(2, playerUuid.toString());
                
                int rows = stmt.executeUpdate();
                boolean deleted = rows > 0;
                plugin.debug("DatabaseBankStorage.deleteMembership() - Membership deleted: {}", deleted);
                return deleted;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete membership", e);
                throw new RuntimeException("Failed to delete membership", e);
            }
        }, executorService);
    }
    
    /**
     * Deletes all memberships for an account.
     * 
     * @param accountName The account name
     * @return A CompletableFuture that completes with the number of memberships deleted
     */
    @NotNull
    public CompletableFuture<Integer> deleteAllMemberships(@NotNull String accountName) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.deleteAllMemberships() - account={}", accountName);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "DELETE FROM " + membershipsTable + " WHERE account_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                
                int rows = stmt.executeUpdate();
                plugin.debug("DatabaseBankStorage.deleteAllMemberships() - Deleted {} memberships", rows);
                return rows;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to delete all memberships", e);
                throw new RuntimeException("Failed to delete all memberships", e);
            }
        }, executorService);
    }
    
    /**
     * Loads all memberships for an account.
     * 
     * @param accountName The account name
     * @return A CompletableFuture that completes with a list of membership data (accountName, playerUuid, role, createdAt)
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadMemberships(@NotNull String accountName) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadMemberships() - account={}", accountName);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "SELECT account_name, player_uuid, role, created_at FROM " + membershipsTable + " WHERE account_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<MembershipRecord> records = new ArrayList<>();
                    while (rs.next()) {
                        records.add(new MembershipRecord(
                            rs.getString("account_name"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("role"),
                            rs.getLong("created_at")
                        ));
                    }
                    
                    plugin.debug("DatabaseBankStorage.loadMemberships() - Loaded {} memberships", records.size());
                    return records;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load memberships", e);
                throw new RuntimeException("Failed to load memberships", e);
            }
        }, executorService);
    }
    
    /**
     * Loads all memberships from the database.
     * 
     * @return A CompletableFuture that completes with a list of all membership records
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadAllMemberships() {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadAllMemberships() - Loading all memberships");
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "SELECT account_name, player_uuid, role, created_at FROM " + membershipsTable;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                List<MembershipRecord> records = new ArrayList<>();
                while (rs.next()) {
                    records.add(new MembershipRecord(
                        rs.getString("account_name"),
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("role"),
                        rs.getLong("created_at")
                    ));
                }
                
                plugin.debug("DatabaseBankStorage.loadAllMemberships() - Loaded {} memberships", records.size());
                return records;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load all memberships", e);
                throw new RuntimeException("Failed to load all memberships", e);
            }
        }, executorService);
    }
    
    /**
     * Loads all memberships for a player.
     * 
     * @param playerUuid The player UUID
     * @return A CompletableFuture that completes with a list of membership data
     */
    @NotNull
    public CompletableFuture<List<MembershipRecord>> loadMembershipsByPlayer(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.loadMembershipsByPlayer() - player={}", playerUuid);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "SELECT account_name, player_uuid, role, created_at FROM " + membershipsTable + " WHERE player_uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    List<MembershipRecord> records = new ArrayList<>();
                    while (rs.next()) {
                        records.add(new MembershipRecord(
                            rs.getString("account_name"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("role"),
                            rs.getLong("created_at")
                        ));
                    }
                    
                    plugin.debug("DatabaseBankStorage.loadMembershipsByPlayer() - Loaded {} memberships", records.size());
                    return records;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load memberships by player", e);
                throw new RuntimeException("Failed to load memberships by player", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.membershipExists() - account={}, player={}", accountName, playerUuid);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "SELECT 1 FROM " + membershipsTable + " WHERE account_name = ? AND player_uuid = ? LIMIT 1";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                stmt.setString(2, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    boolean exists = rs.next();
                    plugin.debug("DatabaseBankStorage.membershipExists() - Membership exists: {}", exists);
                    return exists;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to check membership existence", e);
                throw new RuntimeException("Failed to check membership existence", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.updateMembershipRole() - account={}, player={}, role={}", 
                accountName, playerUuid, newRole);
            
            String membershipsTable = getTableName("account_memberships");
            String sql = "UPDATE " + membershipsTable + " SET role = ? WHERE account_name = ? AND player_uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, newRole);
                stmt.setString(2, accountName);
                stmt.setString(3, playerUuid.toString());
                
                int rows = stmt.executeUpdate();
                boolean updated = rows > 0;
                plugin.debug("DatabaseBankStorage.updateMembershipRole() - Role updated: {}", updated);
                return updated;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update membership role", e);
                throw new RuntimeException("Failed to update membership role", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.getDailyTransactions() - account={}, player={}, date={}", 
                accountName, playerUuid, date);
            
            String dailyTransactionsTable = getTableName("daily_transactions");
            String sql = "SELECT account_name, player_uuid, date, deposit_total, withdraw_total FROM " + 
                dailyTransactionsTable + " WHERE account_name = ? AND player_uuid = ? AND date = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, accountName);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, date);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        DailyTransactionRecord record = new DailyTransactionRecord(
                            rs.getString("account_name"),
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("date"),
                            rs.getInt("deposit_total"),
                            rs.getInt("withdraw_total")
                        );
                        plugin.debug("DatabaseBankStorage.getDailyTransactions() - Found record: deposit={}, withdraw={}", 
                            record.getDepositTotal(), record.getWithdrawTotal());
                        return Optional.of(record);
                    } else {
                        plugin.debug("DatabaseBankStorage.getDailyTransactions() - No record found");
                        return Optional.empty();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to get daily transactions", e);
                throw new RuntimeException("Failed to get daily transactions", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.updateDailyTransactions() - account={}, player={}, date={}, deposit={}, withdraw={}", 
                accountName, playerUuid, date, depositAmount, withdrawAmount);
            
            String dailyTransactionsTable = getTableName("daily_transactions");
            
            // Try to update existing record first
            String updateSql = "UPDATE " + dailyTransactionsTable + 
                " SET deposit_total = deposit_total + ?, withdraw_total = withdraw_total + ? " +
                " WHERE account_name = ? AND player_uuid = ? AND date = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                
                stmt.setInt(1, depositAmount);
                stmt.setInt(2, withdrawAmount);
                stmt.setString(3, accountName);
                stmt.setString(4, playerUuid.toString());
                stmt.setString(5, date);
                
                int rows = stmt.executeUpdate();
                
                if (rows > 0) {
                    plugin.debug("DatabaseBankStorage.updateDailyTransactions() - Updated existing record");
                    return true;
                }
                
                // No existing record, create a new one
                String insertSql = "INSERT INTO " + dailyTransactionsTable + 
                    " (account_name, player_uuid, date, deposit_total, withdraw_total) VALUES (?, ?, ?, ?, ?)";
                
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, accountName);
                    insertStmt.setString(2, playerUuid.toString());
                    insertStmt.setString(3, date);
                    insertStmt.setInt(4, depositAmount);
                    insertStmt.setInt(5, withdrawAmount);
                    
                    int insertRows = insertStmt.executeUpdate();
                    boolean inserted = insertRows > 0;
                    plugin.debug("DatabaseBankStorage.updateDailyTransactions() - Created new record: {}", inserted);
                    return inserted;
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update daily transactions", e);
                throw new RuntimeException("Failed to update daily transactions", e);
            }
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.incrementEmeraldStats() - region={}, mined={}, loot={}, mob={}, trade={}, bankIn={}, bankOut={}",
                regionId, minedDelta, lootDelta, mobDelta, tradingDelta, bankInDelta, bankOutDelta);
            
            String emeraldStatsTable = getTableName("emerald_region_stats");
            long now = System.currentTimeMillis();
            String sql;
            
            if ("sqlite".equals(databaseType)) {
                sql = "INSERT INTO " + emeraldStatsTable +
                    " (region_id, mined_total, loot_total, mob_total, trading_total, bank_in_total, bank_out_total, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(region_id) DO UPDATE SET " +
                    "mined_total = mined_total + excluded.mined_total, " +
                    "loot_total = loot_total + excluded.loot_total, " +
                    "mob_total = mob_total + excluded.mob_total, " +
                    "trading_total = trading_total + excluded.trading_total, " +
                    "bank_in_total = bank_in_total + excluded.bank_in_total, " +
                    "bank_out_total = bank_out_total + excluded.bank_out_total, " +
                    "updated_at = excluded.updated_at";
            } else {
                sql = "INSERT INTO " + emeraldStatsTable +
                    " (region_id, mined_total, loot_total, mob_total, trading_total, bank_in_total, bank_out_total, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "mined_total = mined_total + VALUES(mined_total), " +
                    "loot_total = loot_total + VALUES(loot_total), " +
                    "mob_total = mob_total + VALUES(mob_total), " +
                    "trading_total = trading_total + VALUES(trading_total), " +
                    "bank_in_total = bank_in_total + VALUES(bank_in_total), " +
                    "bank_out_total = bank_out_total + VALUES(bank_out_total), " +
                    "updated_at = VALUES(updated_at)";
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, regionId);
                stmt.setInt(2, minedDelta);
                stmt.setInt(3, lootDelta);
                stmt.setInt(4, mobDelta);
                stmt.setInt(5, tradingDelta);
                stmt.setInt(6, bankInDelta);
                stmt.setInt(7, bankOutDelta);
                stmt.setLong(8, now);
                
                int rows = stmt.executeUpdate();
                boolean updated = rows > 0;
                plugin.debug("DatabaseBankStorage.incrementEmeraldStats() - Updated: {}", updated);
                return updated;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to update emerald stats for region: " + regionId, e);
                throw new RuntimeException("Failed to update emerald stats", e);
            }
        }, executorService);
    }
    
    /**
     * Loads emerald region stats (returns zeroed totals if none exist).
     *
     * @param regionId The region ID
     * @return A CompletableFuture with EmeraldRegionStats
     */
    @NotNull
    public CompletableFuture<EmeraldRegionStats> getEmeraldRegionStats(@NotNull String regionId) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.getEmeraldRegionStats() - region={}", regionId);
            
            String emeraldStatsTable = getTableName("emerald_region_stats");
            String sql = "SELECT region_id, mined_total, loot_total, mob_total, trading_total, " +
                "bank_in_total, bank_out_total, updated_at FROM " + emeraldStatsTable + " WHERE region_id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, regionId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new EmeraldRegionStats(
                            rs.getString("region_id"),
                            rs.getInt("mined_total"),
                            rs.getInt("loot_total"),
                            rs.getInt("mob_total"),
                            rs.getInt("trading_total"),
                            rs.getInt("bank_in_total"),
                            rs.getInt("bank_out_total"),
                            rs.getLong("updated_at")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load emerald stats for region: " + regionId, e);
                throw new RuntimeException("Failed to load emerald stats", e);
            }
            
            return EmeraldRegionStats.empty(regionId);
        }, executorService);
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
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.upsertEmeraldExchangeRate() - region={}, rate={}", regionId, rate);
            
            String emeraldRatesTable = getTableName("emerald_exchange_rates");
            long now = System.currentTimeMillis();
            String sql;
            
            if ("sqlite".equals(databaseType)) {
                sql = "INSERT INTO " + emeraldRatesTable +
                    " (region_id, current_rate, updated_at) VALUES (?, ?, ?) " +
                    "ON CONFLICT(region_id) DO UPDATE SET " +
                    "current_rate = excluded.current_rate, " +
                    "updated_at = excluded.updated_at";
            } else {
                sql = "INSERT INTO " + emeraldRatesTable +
                    " (region_id, current_rate, updated_at) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "current_rate = VALUES(current_rate), " +
                    "updated_at = VALUES(updated_at)";
            }
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, regionId);
                stmt.setInt(2, rate);
                stmt.setLong(3, now);
                
                int rows = stmt.executeUpdate();
                boolean updated = rows > 0;
                plugin.debug("DatabaseBankStorage.upsertEmeraldExchangeRate() - Updated: {}", updated);
                return updated;
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to upsert exchange rate for region: " + regionId, e);
                throw new RuntimeException("Failed to update exchange rate", e);
            }
        }, executorService);
    }
    
    /**
     * Loads the stored exchange rate for a region.
     *
     * @param regionId The region ID
     * @return A CompletableFuture with an Optional exchange rate record
     */
    @NotNull
    public CompletableFuture<Optional<EmeraldExchangeRateRecord>> getEmeraldExchangeRate(@NotNull String regionId) {
        return CompletableFuture.supplyAsync(() -> {
            plugin.debug("DatabaseBankStorage.getEmeraldExchangeRate() - region={}", regionId);
            
            String emeraldRatesTable = getTableName("emerald_exchange_rates");
            String sql = "SELECT region_id, current_rate, updated_at FROM " + emeraldRatesTable + " WHERE region_id = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, regionId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(new EmeraldExchangeRateRecord(
                            rs.getString("region_id"),
                            rs.getInt("current_rate"),
                            rs.getLong("updated_at")
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load exchange rate for region: " + regionId, e);
                throw new RuntimeException("Failed to load exchange rate", e);
            }
            
            return Optional.empty();
        }, executorService);
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
        public String getAccountName() { return accountName; }
        
        @NotNull
        public UUID getPlayerUuid() { return playerUuid; }
        
        @NotNull
        public String getDate() { return date; }
        
        public int getDepositTotal() { return depositTotal; }
        
        public int getWithdrawTotal() { return withdrawTotal; }
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
        public String getRegionId() { return regionId; }
        
        public int getMinedTotal() { return minedTotal; }
        
        public int getLootTotal() { return lootTotal; }
        
        public int getMobTotal() { return mobTotal; }
        
        public int getTradingTotal() { return tradingTotal; }
        
        public int getBankInTotal() { return bankInTotal; }
        
        public int getBankOutTotal() { return bankOutTotal; }
        
        public long getUpdatedAt() { return updatedAt; }
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
        public String getRegionId() { return regionId; }
        
        public int getCurrentRate() { return currentRate; }
        
        public long getUpdatedAt() { return updatedAt; }
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
        public String getAccountName() { return accountName; }
        
        @NotNull
        public UUID getPlayerUuid() { return playerUuid; }
        
        @NotNull
        public String getRole() { return role; }
        
        public long getCreatedAt() { return createdAt; }
    }
    
    /**
     * Maps a ResultSet row to a BankRecord.
     */
    @NotNull
    private BankRecord mapResultSetToRecord(@NotNull ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        UUID ownerUuid = UUID.fromString(rs.getString("owner_uuid"));
        String worldName = rs.getString("world_name");
        BigDecimal balance = rs.getBigDecimal("balance");
        long createdAt = rs.getLong("created_at");
        long updatedAt = rs.getLong("updated_at");
        
        return new BankRecord(name, ownerUuid, worldName, balance, createdAt, updatedAt);
    }
}
