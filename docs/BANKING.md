# Banking System Documentation

This document provides comprehensive information about the Coin of the Realm banking system, including how to use it, configure it, and integrate with it.

## Overview

The Coin of the Realm banking system provides a complete banking solution that:
- Stores coins in bank accounts (persistent across server restarts)
- Supports multiple accounts per player
- Supports shared/guild accounts (multiple players per account)
- Integrates with ServiceIO for interoperability with other plugins
- Uses database storage for reliability and performance
- Supports world-specific banks for multi-world servers

## Architecture

### Components

1. **BankController**: The core banking API (implements ServiceIO's BankController interface)
2. **Bank**: Represents individual bank accounts (implements ServiceIO's Bank interface)
3. **BankStorage**: Abstract storage layer (database implementation)
4. **AccountMembershipManager**: Manages many-to-many player-account relationships
5. **BankManager**: Bridges AccountMembershipManager with BankController

### Data Flow

```
Player Command
    ↓
BankManager (access control via AccountMembershipManager)
    ↓
BankController (our implementation or external)
    ↓
Bank (account operations)
    ↓
BankStorage (database persistence)
```

## Configuration

### Basic Banking Configuration

Banking configuration is found in:

**bank.yml** - Banking-specific settings:
```yaml
banking:
  enabled: true
  default-account-pattern: "{player-uuid}-main"
  
  # Use our own BankController or external one
  use-own-controller: true
```

### Configuration Options

#### `banking.enabled`
- **Type**: Boolean
- **Default**: `true`
- **Description**: Enables or disables banking features

#### `banking.use-own-controller`
- **Type**: Boolean
- **Default**: `true`
- **Description**: If `true`, registers Coin of the Realm's BankController with ServiceIO. If `false`, only uses external BankControllers.

### Storage Configuration

Storage configuration is **global** and located in `config.yml` under the `storage` key. It applies to all persistence operations including banking, account memberships, exchange tracking, and daily transaction limits.

#### `storage.type` (in config.yml)
- **Type**: String
- **Default**: `"database"`
- **Options**: `"database"` (YAML support coming soon)
- **Description**: Storage backend for all persistence operations

#### `storage.database.type` (in config.yml)
- **Type**: String
- **Default**: `"sqlite"`
- **Options**: `"sqlite"`, `"mysql"`
- **Description**: Database type for storage

#### `storage.database.prefix` (in config.yml)
- **Type**: String
- **Default**: `""` (empty string)
- **Description**: Table prefix for shared databases. Useful when multiple plugins share the same database. Example: `"cotr_"` creates tables like `cotr_banks`, `cotr_schema_version`. Leave empty for no prefix.

#### `storage.database.file` (in config.yml, SQLite)
- **Type**: String
- **Default**: `"banks.db"`
- **Description**: SQLite database file path (relative to plugin data folder)

#### `storage.database.host` (in config.yml, MySQL)
- **Type**: String
- **Default**: `"localhost"`
- **Description**: MySQL server hostname

#### `storage.database.port` (in config.yml, MySQL)
- **Type**: Integer
- **Default**: `3306`
- **Description**: MySQL server port

#### `storage.database.database` (in config.yml, MySQL)
- **Type**: String
- **Default**: `"cotr"`
- **Description**: MySQL database name

#### `storage.database.username` (in config.yml, MySQL)
- **Type**: String
- **Default**: `"cotr"`
- **Description**: MySQL username

#### `storage.database.password` (in config.yml, MySQL)
- **Type**: String
- **Default**: `""`
- **Description**: MySQL password

## Commands

### `/cotr deposit [account] <amount>`
Deposits coins from your inventory into a bank account.

**Examples**:
- `/cotr deposit 10` - Deposit 10 coins to your default account
- `/cotr deposit savings 50` - Deposit 50 coins to "savings" account

### `/cotr withdraw [account] <amount>`
Withdraws coins from a bank account to your inventory.

**Examples**:
- `/cotr withdraw 5` - Withdraw 5 coins from your default account
- `/cotr withdraw savings 20` - Withdraw 20 coins from "savings" account

### `/cotr balance [account]`
Shows your bank balance(s).

**Examples**:
- `/cotr balance` - List all your accounts and balances
- `/cotr balance savings` - Show balance for "savings" account

### `/cotr account create <name>`
Creates a new bank account.

**Examples**:
- `/cotr account create savings` - Create "savings" account

### `/cotr account list`
Lists all accounts you have access to.

### `/cotr account members <account>`
Shows all members of an account.

### `/cotr account add <account> <player> [role]`
Adds a player to an account with the specified role.

**Examples**:
- `/cotr account add savings Alice` - Adds Alice as MEMBER (default)
- `/cotr account add treasury Bob OWNER` - Adds Bob as OWNER
- `/cotr account add donations Charlie CONTRIBUTOR` - Adds Charlie as CONTRIBUTOR (can only deposit)
- `/cotr account add limited Dave USER` - Adds Dave as USER (with daily limits)

**Available roles**: OWNER, MEMBER, USER, CONTRIBUTOR, VIEWER

### `/cotr account remove <account> <player>`
Removes a player from an account.

### `/cotr account delete <account>`
Deletes an account (owner only).

## Account Management

### Default Accounts

When a player first uses banking features, a default account is automatically created using the pattern specified in `banking.default-account-pattern`. The default pattern is `{player-uuid}-main`, which creates accounts like `550e8400-e29b-41d4-a716-446655440000-main`.

### Account Roles

The banking system supports five distinct roles with varying permission levels:

- **OWNER**: Full control over the account
  - Can: deposit, withdraw, transfer, view balance
  - Can: add/remove members, delete account
  - Full administrative access

- **MEMBER**: Full transaction access without management
  - Can: deposit, withdraw, transfer, view balance
  - Cannot: manage members, delete account
  - Perfect for trusted account users

- **USER**: Limited transaction access with daily limits
  - Can: deposit, withdraw (subject to daily limits), view balance
  - Cannot: manage members, delete account
  - Daily limits: Configurable per account or globally (default: 1000 coins/day)
  - Perfect for accounts where you want to limit transaction amounts

- **CONTRIBUTOR**: Deposit-only access (unlimited)
  - Can: deposit (unlimited), view balance
  - Cannot: withdraw, manage members, delete account
  - Perfect for: guild dues, donation accounts, kingdom contributions, or any scenario where players should contribute but not withdraw

- **VIEWER**: Read-only access
  - Can: view balance only
  - Cannot: deposit, withdraw, manage members, delete account
  - Reserved for future use cases requiring read-only access

### Shared Accounts

Multiple players can have access to the same account, making it perfect for:
- Guild/faction treasuries
- Shop accounts
- Team funds
- Shared savings

## ServiceIO Integration

### For Plugin Developers

Coin of the Realm's BankController is registered with ServiceIO, allowing other plugins to discover and use it:

```java
import net.thenextlvl.service.api.economy.bank.BankController;
import org.bukkit.plugin.ServicesManager;

// Get BankController from ServiceIO
ServicesManager servicesManager = getServer().getServicesManager();
BankController bankController = servicesManager.load(BankController.class);

if (bankController != null) {
    // Use the BankController
    UUID playerUuid = player.getUniqueId();
    CompletableFuture<Bank> bankFuture = bankController.createBank(playerUuid, "my-account");
    
    bankFuture.thenAccept(bank -> {
        if (bank != null) {
            // Bank created successfully
            BigDecimal newBalance = bank.deposit(BigDecimal.valueOf(100));
        }
    });
}
```

### BankController API

The BankController implements the full ServiceIO BankController interface:

- `createBank(UUID, String)` - Create a global bank
- `createBank(UUID, String, World)` - Create a world-specific bank
- `loadBank(String)` - Load bank by name
- `loadBank(UUID)` - Load bank by owner (global)
- `loadBank(UUID, World)` - Load bank by owner and world
- `deleteBank(String/UUID/UUID+World)` - Delete banks
- `getBank(String/UUID/UUID+World)` - Get cached banks
- `getBanks()` - Get all banks
- `format(Number)` - Format currency amounts
- `fractionalDigits()` - Returns 0 (coins are whole numbers)

### Bank API

The Bank interface provides account operations:

- `getOwner()` - Get bank owner UUID
- `getName()` - Get bank name
- `getWorld()` - Get world (Optional, empty for global banks)
- `getBalance()` - Get current balance
- `deposit(Number)` - Deposit funds
- `withdraw(Number)` - Withdraw funds
- `setBalance(Number)` - Set balance directly
- `addMember(UUID)` - Add a member
- `removeMember(UUID)` - Remove a member
- `isMember(UUID)` - Check membership
- `getMembers()` - Get all members

## Database Schema

### Table Prefix

If a table prefix is configured (e.g., `"cotr_"`), all table names will be prefixed. For example:
- `banks` becomes `cotr_banks`
- `account_memberships` becomes `cotr_account_memberships`
- `schema_version` becomes `cotr_schema_version`
- Index names are also prefixed: `idx_cotr_banks_owner`

### Banks Table

**Table Name**: `{prefix}banks` (e.g., `banks` or `cotr_banks`)

```sql
CREATE TABLE {prefix}banks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    owner_uuid VARCHAR(36) NOT NULL,
    world_name VARCHAR(255),
    balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE INDEX idx_{prefix}banks_owner ON {prefix}banks(owner_uuid);
CREATE INDEX idx_{prefix}banks_world ON {prefix}banks(world_name);
CREATE INDEX idx_{prefix}banks_owner_world ON {prefix}banks(owner_uuid, world_name);
```

### Account Memberships Table

**Table Name**: `{prefix}account_memberships` (e.g., `account_memberships` or `cotr_account_memberships`)

```sql
CREATE TABLE {prefix}account_memberships (
    account_name VARCHAR(255) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at BIGINT NOT NULL,
    PRIMARY KEY (account_name, player_uuid)
);

CREATE INDEX idx_{prefix}account_memberships_account ON {prefix}account_memberships(account_name);
CREATE INDEX idx_{prefix}account_memberships_player ON {prefix}account_memberships(player_uuid);
```

### Daily Transactions Table

**Table Name**: `{prefix}daily_transactions` (e.g., `daily_transactions` or `cotr_daily_transactions`)

This table tracks daily transaction totals for USER role accounts to enforce daily limits.

```sql
CREATE TABLE {prefix}daily_transactions (
    account_name VARCHAR(255) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    date VARCHAR(10) NOT NULL,
    deposit_total INT NOT NULL DEFAULT 0,
    withdraw_total INT NOT NULL DEFAULT 0,
    PRIMARY KEY (account_name, player_uuid, date)
);

CREATE INDEX idx_{prefix}daily_transactions_account_date ON {prefix}daily_transactions(account_name, date);
CREATE INDEX idx_{prefix}daily_transactions_player_date ON {prefix}daily_transactions(player_uuid, date);
```

**Note**: The `date` column uses YYYY-MM-DD format (e.g., "2024-01-15").

### Schema Version Table

**Table Name**: `{prefix}schema_version` (e.g., `schema_version` or `cotr_schema_version`)

```sql
CREATE TABLE {prefix}schema_version (
    version INT NOT NULL PRIMARY KEY,
    applied_at BIGINT NOT NULL
);
```

**Note**: Replace `{prefix}` with your configured prefix (or empty string if no prefix).

### Migration from YAML

If you have an existing `account-memberships.yml` file, the plugin will automatically migrate the data to the database on first startup. After migration, you can safely delete the YAML file.

## Troubleshooting

### Banking Not Working

**Symptoms**: Commands return "Banking is not available"

**Solutions**:
1. Check `banking.enabled` in `config.yml` is `true`
2. Verify ServiceIO plugin is installed and enabled
3. Check server logs for initialization errors
4. Enable debug mode: `debug: true` in `config.yml`

### Database Connection Issues

**Symptoms**: Errors about database connection

**Solutions**:
1. For SQLite: Check file permissions on `banks.db`
2. For MySQL: Verify connection settings (host, port, username, password)
3. Check database exists (MySQL)
4. Verify user has CREATE TABLE permissions (MySQL)

### BankController Not Registered

**Symptoms**: "No BankController service is registered"

**Solutions**:
1. Set `banking.use-own-controller: true` in `config.yml`
2. Restart the server
3. Check server logs for registration errors
4. Verify ServiceIO is installed

### Performance Issues

**Symptoms**: Slow banking operations

**Solutions**:
1. Enable debug mode to see detailed timing
2. Check database connection pool settings
3. Consider using MySQL instead of SQLite for high-traffic servers
4. Review cache hit rates in debug logs

## Best Practices

1. **Use Default Accounts**: Let players use default accounts for simplicity
2. **Shared Accounts**: Use shared accounts for guilds/teams
3. **World-Specific Banks**: Use world-specific banks for multi-world economies
4. **Regular Backups**: Backup the database regularly
5. **Monitor Performance**: Use debug mode during development to identify bottlenecks

## Migration

### From External BankController

If you were using an external BankController and want to switch to CotR's:

1. Set `banking.use-own-controller: true` in `config.yml`
2. Restart the server
3. CotR's BankController will be registered
4. Existing accounts in the external system will remain there
5. New accounts will use CotR's system

### Database Migration

Future versions may include migration tools for:
- Upgrading database schema
- Migrating from YAML to database
- Converting between SQLite and MySQL

## API Examples

### Creating a Bank Account

```java
BankController bankController = // ... get from ServiceIO
UUID playerUuid = player.getUniqueId();

CompletableFuture<Bank> future = bankController.createBank(playerUuid, "savings");

future.thenAccept(bank -> {
    if (bank != null) {
        player.sendMessage("Account created!");
    } else {
        player.sendMessage("Failed to create account");
    }
}).exceptionally(ex -> {
    player.sendMessage("Error: " + ex.getMessage());
    return null;
});
```

### Depositing Funds

```java
Bank bank = // ... get bank instance
BigDecimal amount = BigDecimal.valueOf(50);

try {
    BigDecimal newBalance = bank.deposit(amount);
    player.sendMessage("Deposited! New balance: " + newBalance);
} catch (IllegalArgumentException e) {
    player.sendMessage("Invalid amount");
}
```

### Withdrawing Funds

```java
Bank bank = // ... get bank instance
BigDecimal amount = BigDecimal.valueOf(25);

try {
    BigDecimal newBalance = bank.withdraw(amount);
    player.sendMessage("Withdrew! New balance: " + newBalance);
} catch (IllegalStateException e) {
    player.sendMessage("Insufficient funds");
}
```

## Security Considerations

1. **Access Control**: AccountMembershipManager enforces access control
2. **Balance Validation**: All operations validate balances
3. **Transaction Safety**: Database transactions ensure data integrity
4. **Input Validation**: All amounts are validated before processing

## Performance

- **Caching**: In-memory cache for frequently accessed banks
- **Connection Pooling**: HikariCP for efficient database connections
- **Async Operations**: All storage operations are asynchronous
- **Indexed Queries**: Database indexes for fast lookups

## Future Enhancements

- YAML storage backend option
- Database migration tools
- Transaction history
- Interest calculations
- Loan system
- Multi-currency support
