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

## Commands

- `/cotr drop <amount>` - Drop coins at your location
- `/cotr give <player> <amount>` - Give coins to a player
- Aliases: `/coin`, `/coins`

## Permissions

- `cotr.command.use` - Use the `/cotr` command
- `cotr.command.drop` - Use `/cotr drop` command
- `cotr.command.give` - Use `/cotr give` command

## Integration

This plugin is designed to be easily integrated with economy plugins. The currency items are identified using NBT data (`cotr:coin`), making them easy to detect and count programmatically.

### For Developers

The plugin provides a simple API for creating and identifying coins:

```java
// Create a coin
ItemStack coin = CoinItem.createCoin(amount);

// Check if an item is a coin
boolean isCoin = CoinItem.isCoin(itemStack);

// Get the amount of coins in a stack
int amount = CoinItem.getCoinAmount(itemStack);
```

## Requirements

- **Minecraft**: 1.21.11+
- **Server**: Paper or compatible fork

## Installation

1. Download the latest release
2. Place the JAR file in your server's `plugins` folder
3. Restart your server
4. The plugin will create necessary configuration files on first run

## Configuration

The plugin uses a simple, configurable system that allows server administrators to customize the currency to their needs. All currency items are identified through NBT data, ensuring compatibility with various economy systems.

## License

See [LICENSE](LICENSE) file for details.

## Author

**RADawson**

- GitHub: [@radawson](https://github.com/radawson)
- Website: https://github.com/radawson/cotr

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

