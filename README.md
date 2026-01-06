# Coin of the Realm (COTR)

A flexible, easy-to-use currency system plugin for Minecraft servers that provides a configurable currency item that can be seamlessly integrated with any economy plugin.

## Goal

**Coin of the Realm** aims to create an easy-to-use currency system that anyone could plug into any of the economy plugins (Vault, ServiceIO, etc.). The goal of this plugin is to create a configurable currency system that can be used as:

- **Drops**: Physical currency items that can be dropped in the world and picked up by players
- **Bank Items**: Currency that can be stored in chests, banks, or other storage systems
- **Inventory Placeholders**: Currency items that players can hold in their inventories for any currency someone might want to use

## Features

### 🪙 Custom Currency Items
- Creates unique, identifiable currency items using NBT data
- Custom display name and lore for easy recognition
- Resource pack support for custom textures (with fallback appearance)
- Persistent across server restarts

### 🎮 Flexible Integration
- Designed to work with any economy plugin (Vault, ServiceIO, etc.)
- Easy-to-use API for developers to integrate with their plugins
- NBT-based identification system for reliable currency detection

### 🌍 World Integration
- Drop coins in the world as custom display entities
- Automatic proximity-based pickup system
- Natural item drop fallback support

### 📦 Inventory Management
- Give coins directly to player inventories
- Stackable currency items (up to 64 per stack)
- Safe item handling with overflow protection

### 🏦 Banking System (Optional)
- Integration with ServiceIO for bank account management
- Many-to-many account relationships (players can own multiple accounts, accounts can have multiple members)
- Role-based access control (OWNER, MEMBER, VIEWER)
- Deposit and withdraw coins from bank accounts
- Transfer funds between accounts
- Account management (create, delete, manage members)
- Automatic default account creation
- Account membership persistence

## Commands

### Physical Coin Operations
- `/cotr drop <amount>` - Drop coins at your location
- `/cotr give <player> <amount>` - Give physical coins to a player

### Banking Operations (requires ServiceIO)
- `/cotr deposit [account] <amount>` - Deposit coins from inventory to bank account
- `/cotr withdraw [account] <amount>` - Withdraw coins from bank account to inventory
- `/cotr balance [account]` - View bank account balance(s)
- `/cotr give <player> [account] <amount>` - Transfer coins between bank accounts
- `/cotr request <player> [account] <amount>` - Request coins from another player

### Account Management (requires ServiceIO)
- `/cotr account create <name>` - Create a new bank account
- `/cotr account list` - List all your bank accounts
- `/cotr account members <account>` - View members of an account
- `/cotr account add <account> <player> [role]` - Add a member to an account
- `/cotr account remove <account> <player>` - Remove a member from an account
- `/cotr account delete <account>` - Delete an account (owner only)

### Admin Commands
- `/cotr info` - View plugin information and statistics

**Aliases**: `/coin`, `/coins`

## Permissions

### Base Permissions
- `cotr.command.use` - Use the `/cotr` command (default: op)
- `cotr.command.drop` - Use `/cotr drop` command (default: op)
- `cotr.command.give` - Use `/cotr give` command (default: op)

### Banking Permissions
- `cotr.command.deposit` - Use `/cotr deposit` command (default: true)
- `cotr.command.withdraw` - Use `/cotr withdraw` command (default: true)
- `cotr.command.balance` - Use `/cotr balance` command (default: true)
- `cotr.command.request` - Use `/cotr request` command (default: true)
- `cotr.command.account` - Use `/cotr account` commands (default: true)
- `cotr.command.account.manage` - Manage account memberships (default: true)

### Admin Permissions
- `cotr.command.info` - Use `/cotr info` command (default: op)

**Permission Groups**:
- `cotr.*` - All permissions (default: op)
- `cotr.command.*` - All command permissions (default: op)

## Integration

This plugin is designed to be easily integrated with economy plugins. The currency items are identified using NBT data (`cotr:coin`), making them easy to detect and count programmatically.

### ServiceIO Integration

Coin of the Realm integrates with [ServiceIO](https://github.com/thenextlvl/service-io) to provide banking functionality. ServiceIO is an optional dependency:

- **Without ServiceIO**: The plugin works normally for physical coin operations (drop, give). Banking features are disabled.
- **With ServiceIO**: Full banking functionality is available, including deposits, withdrawals, transfers, and account management.

The plugin uses reflection to integrate with ServiceIO, so it will gracefully handle the absence of ServiceIO without errors. Banking features are automatically enabled when ServiceIO is detected and `banking.enabled` is set to `true` in `config.yml`.

See [INSTALLATION.md](docs/INSTALLATION.md) for ServiceIO setup instructions.

### For Developers

The plugin provides a comprehensive API for creating and managing coins, as well as banking operations:

#### Coin Item API

```java
// Create a coin
ItemStack coin = CoinItem.createCoin(amount);

// Check if an item is a coin
boolean isCoin = CoinItem.isCoin(itemStack);

// Get the amount of coins in a stack
int amount = CoinItem.getCoinAmount(itemStack);
```

#### Banking API

```java
CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
BankManager bankManager = plugin.getBankManager();

// Check if banking is enabled
if (bankManager != null && bankManager.isBankingEnabled()) {
    // Get or create default account
    CompletableFuture<String> accountFuture = bankManager.getDefaultAccount(player);
    
    // Deposit coins
    bankManager.deposit(player, accountName, amount);
    
    // Withdraw coins
    bankManager.withdraw(player, accountName, amount);
    
    // Get balance
    CompletableFuture<Integer> balanceFuture = bankManager.getBalance(player, accountName);
    
    // Transfer between accounts
    bankManager.transfer(fromPlayer, fromAccount, toPlayer, toAccount, amount);
}
```

For detailed API documentation, see [docs/API.md](docs/API.md).

## Requirements

- **Minecraft**: 1.21.11+
- **Server**: Paper or compatible fork
- **Java**: 21+
- **ServiceIO** (optional): Required for banking features. Version 2.3.1 or higher recommended.

## Installation

### Basic Installation (Physical Coins Only)

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. The plugin will create necessary configuration files on first run

### Installation with Banking Features

1. Follow the basic installation steps above
2. Download and install [ServiceIO](https://github.com/thenextlvl/service-io) plugin
3. Ensure `banking.enabled` is set to `true` in `config.yml`
4. Restart your server
5. Banking features will be automatically enabled if ServiceIO is detected

For detailed installation instructions, including ServiceIO setup and troubleshooting, see [docs/INSTALLATION.md](docs/INSTALLATION.md).

## Configuration

The plugin uses a YAML configuration file (`config.yml`) that allows server administrators to customize:

- **Coin appearance**: Material, display name, lore, and resource pack model
- **Banking settings**: Enable/disable banking, account naming patterns, storage backend

For complete configuration documentation, see [docs/CONFIGURATION.md](docs/CONFIGURATION.md).

## Architecture

Coin of the Realm is built with a modular architecture:

- **Core Plugin**: Manages plugin lifecycle and coordinates components
- **Configuration System**: Handles config loading and validation
- **Coin Item System**: Creates and manages currency items with NBT identification
- **Entity Management**: Handles coin display entities in the world
- **Banking System**: Integrates with ServiceIO for account management
- **Account Membership**: Manages many-to-many player-account relationships

For detailed architecture documentation, see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## License

See [LICENSE](LICENSE) file for details.

## Author

**RADawson**

- GitHub: [@radawson](https://github.com/radawson)
- Website: https://github.com/radawson/cotr

## Documentation

- [Installation Guide](docs/INSTALLATION.md) - Detailed installation and setup instructions
- [Configuration Reference](docs/CONFIGURATION.md) - Complete config.yml documentation
- [API Documentation](docs/API.md) - Developer API reference
- [Architecture Documentation](docs/ARCHITECTURE.md) - System design and architecture
- [Development Guide](docs/DEVELOPMENT.md) - Building and contributing to the project

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

For development setup and guidelines, see [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
