# Configuration Reference

This document provides a complete reference for the `config.yml` configuration file used by Coin of the Realm.

## Configuration File Location

The configuration file is located at:
```
plugins/CoinOfTheRealm/config.yml
```

The file is automatically created on first plugin startup with default values if it doesn't exist.

## Configuration Structure

The configuration file is divided into two main sections:

1. **Coin Configuration** - Settings for the physical coin item
2. **Banking Configuration** - Settings for the banking system (optional)

## Coin Configuration

The `coin` section controls the appearance and behavior of the currency item.

### `coin.item`

**Type**: String (namespaced key)  
**Default**: `"minecraft:gold_nugget"`  
**Required**: Yes

Specifies the base material or item to use for the coin. This serves as the fallback appearance for players who don't have the resource pack installed.

**Format**: `namespace:key` or just `key` for vanilla items

**Examples**:
```yaml
coin:
  item: "minecraft:gold_nugget"  # Vanilla gold nugget
  item: "minecraft:gold_ingot"    # Vanilla gold ingot
  item: "custom:my_coin"          # Custom item (requires resource pack)
```

**Notes**:
- For vanilla Minecraft items, use the `minecraft:` namespace
- Custom items require a resource pack to display correctly
- The material must be a valid Minecraft item type
- If an invalid item is specified, the plugin will fall back to `minecraft:gold_nugget`

### `coin.model`

**Type**: String (namespaced key)  
**Default**: `"cotr:coin"`  
**Required**: No

Specifies the item model to use from the resource pack. This references the model file in your resource pack at `assets/<namespace>/models/item/<key>.json`.

**Format**: `namespace:key`

**Examples**:
```yaml
coin:
  model: "cotr:coin"           # References assets/cotr/models/item/coin.json
  model: "custom:gold_coin"    # References assets/custom/models/item/gold_coin.json
```

**Notes**:
- This is the modern way to specify custom item models (replaces CustomModelData)
- If not specified, the plugin will fall back to CustomModelData
- The model must exist in your resource pack for players to see the custom texture
- Players without the resource pack will see the base material specified in `coin.item`

### `coin.display-name`

**Type**: String  
**Default**: `"Coin of the Realm"`  
**Required**: No

The display name shown when players hover over the coin item in their inventory.

**Examples**:
```yaml
coin:
  display-name: "Coin of the Realm"
  display-name: "Gold Piece"
  display-name: "&6&lRoyal Coin"  # Supports color codes (if your server supports them)
```

**Notes**:
- The display name is shown in gold color by default
- Color codes may work depending on your server configuration
- Keep names concise for better inventory readability

### `coin.lore`

**Type**: List of strings  
**Default**: 
```yaml
- "A valuable currency"
- "used throughout the realm."
```
**Required**: No

A list of lore lines displayed below the display name when hovering over the coin item.

**Examples**:
```yaml
coin:
  lore:
    - "A valuable currency"
    - "used throughout the realm."
    - ""
    - "&7Worth its weight in gold"
```

**Notes**:
- Each list item becomes a separate line of lore
- Empty strings create blank lines
- Lore lines are displayed in gray color
- Color codes may work depending on your server configuration

### `coin.custom-model-data` (Deprecated)

**Type**: Integer  
**Default**: `1000`  
**Required**: No  
**Status**: Deprecated

Legacy method for specifying custom item models. Use `coin.model` instead.

**Notes**:
- This option is kept for backward compatibility
- The plugin will use `coin.model` if specified, otherwise falls back to CustomModelData
- CustomModelData requires matching values in your resource pack's item model JSON

## Banking Configuration

The `banking` section controls the banking system integration with ServiceIO.

### `banking.enabled`

**Type**: Boolean  
**Default**: `true`  
**Required**: No

Enables or disables banking features. When set to `false`, all banking commands and features are disabled, even if ServiceIO is installed.

**Examples**:
```yaml
banking:
  enabled: true   # Banking features enabled (requires ServiceIO)
  enabled: false  # Banking features disabled
```

**Notes**:
- When disabled, the plugin will only support physical coin operations
- ServiceIO is still required for banking features even when enabled
- The plugin will log a message if banking is disabled or ServiceIO is not found

### `banking.default-account-pattern`

**Type**: String (with placeholder)  
**Default**: `"{player-uuid}-main"`  
**Required**: No

Defines the naming pattern for automatically created default accounts. The `{player-uuid}` placeholder is replaced with the player's UUID.

**Examples**:
```yaml
banking:
  default-account-pattern: "{player-uuid}-main"      # e.g., "550e8400-e29b-41d4-a716-446655440000-main"
  default-account-pattern: "account-{player-uuid}"    # e.g., "account-550e8400-e29b-41d4-a716-446655440000"
  default-account-pattern: "player-{player-uuid}"    # e.g., "player-550e8400-e29b-41d4-a716-446655440000"
```

**Notes**:
- The `{player-uuid}` placeholder is required and will be replaced automatically
- Account names must be unique across the server
- Default accounts are created automatically when a player first uses banking features
- Players can create additional accounts with custom names using `/cotr account create <name>`

**Note**: Account membership data is now stored in the database alongside bank data. The `membership-storage` configuration option has been removed. All banking data (banks and memberships) is stored in the same database.
- The membership file is located at `plugins/CoinOfTheRealm/account-memberships.yml`

## Complete Configuration Example

Here's a complete example configuration file with all options:

```yaml
coin:
  # Base material (fallback appearance without resource pack)
  item: "minecraft:gold_nugget"
  
  # Item model for resource pack (namespace:key format)
  # This references assets/cotr/items/coin.json in the resource pack
  model: "cotr:coin"
  
  # Display name for the coin
  display-name: "Coin of the Realm"
  
  # Lore lines for the coin
  lore:
    - "A valuable currency"
    - "used throughout the realm."

banking:
  # Enable or disable banking features
  enabled: true
  
  # Default account naming pattern
  # {player-uuid} will be replaced with the player's UUID
  default-account-pattern: "{player-uuid}-main"
  
  # Storage backend for account memberships
  membership-storage: "yaml"
```

## Configuration Validation

The plugin performs validation on configuration values:

- **Invalid item keys**: Falls back to `minecraft:gold_nugget` with a warning
- **Missing required fields**: Uses default values
- **Invalid banking settings**: Logs warnings and disables affected features

## Reloading Configuration

To reload the configuration without restarting the server:

1. Edit `config.yml` in a text editor
2. Use a plugin reload command (if your server supports it)
3. Or restart the server

**Note**: Some configuration changes (like `coin.item` or `coin.model`) may only take effect for newly created coins. Existing coins in player inventories or the world will retain their original appearance.

## Troubleshooting

### Banking Not Working

If banking features aren't working:

1. Check that `banking.enabled` is set to `true`
2. Verify that ServiceIO is installed and running
3. Check server logs for error messages
4. Ensure ServiceIO version is 2.3.1 or higher

### Custom Item Model Not Showing

If custom item models aren't displaying:

1. Verify the resource pack is installed and enabled
2. Check that the model path matches `coin.model` setting
3. Ensure the model JSON file exists in the resource pack
4. Verify the model references the correct texture file

### Configuration Errors

If you see configuration errors:

1. Check YAML syntax (indentation, colons, dashes)
2. Verify all string values are properly quoted if they contain special characters
3. Check server logs for specific error messages
4. Compare your config with the default configuration

## Related Documentation

- [Installation Guide](INSTALLATION.md) - Setting up the plugin and ServiceIO
- [API Documentation](API.md) - Programmatic access to configuration
- [Architecture Documentation](ARCHITECTURE.md) - How configuration is used internally
