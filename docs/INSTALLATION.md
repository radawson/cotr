# Installation Guide

This guide will walk you through installing Coin of the Realm on your Minecraft server, including optional ServiceIO integration for banking features.

## Prerequisites

Before installing Coin of the Realm, ensure you have:

- **Minecraft Server**: Version 1.21.11 or higher
- **Server Software**: Paper or a compatible fork (Spigot, Purpur, etc.)
- **Java**: Version 21 or higher
- **ServiceIO** (optional): Version 2.3.1 or higher, required only for banking features

## Basic Installation (Physical Coins Only)

If you only need physical coin functionality (dropping, giving coins), you can install Coin of the Realm without ServiceIO.

### Step 1: Download the Plugin

1. Download the latest release from the [GitHub releases page](https://github.com/radawson/cotr/releases)
2. Save the JAR file to a convenient location

### Step 2: Install the Plugin

1. Stop your Minecraft server
2. Place the `CoinOfTheRealm-<version>.jar` file in your server's `plugins` folder
3. Start your server

### Step 3: Verify Installation

1. Check the server console for the message: `[CoinOfTheRealm] Coin of the Realm plugin has been enabled!`
2. Verify that `plugins/CoinOfTheRealm/config.yml` was created
3. Test the plugin by running `/cotr drop 1` in-game (requires appropriate permissions)

The plugin is now installed and ready to use for physical coin operations!

## Installation with Banking Features

To enable banking features (deposits, withdrawals, transfers, account management), you need to install ServiceIO alongside Coin of the Realm.

### Step 1: Install Coin of the Realm

Follow the basic installation steps above.

### Step 2: Download ServiceIO

1. Visit the [ServiceIO GitHub repository](https://github.com/thenextlvl/service-io)
2. Download the latest release (version 2.3.1 or higher)
3. Save the JAR file

### Step 3: Install ServiceIO

1. Stop your Minecraft server
2. Place the ServiceIO JAR file in your server's `plugins` folder
3. Start your server
4. Wait for ServiceIO to fully initialize (check console for completion messages)

### Step 4: Configure Coin of the Realm

1. Open `plugins/CoinOfTheRealm/config.yml` in a text editor
2. Ensure `banking.enabled` is set to `true`:
   ```yaml
   banking:
     enabled: true
   ```
3. Save the file

### Step 5: Restart the Server

1. Restart your server to apply the configuration
2. Check the console for one of these messages:
   - `[CoinOfTheRealm] BankController loaded successfully. Banking features enabled.` (Success)
   - `[CoinOfTheRealm] ServiceIO not found. Banking features disabled.` (ServiceIO not detected)

### Step 6: Verify Banking Features

1. In-game, run `/cotr balance` to check if banking is working
2. If you see "Banking is not available", check the troubleshooting section below
3. If you see your balance (or a message about creating an account), banking is working!

## Post-Installation Configuration

### Editing config.yml

The plugin creates a default `config.yml` file on first run. You can customize:

- **Coin appearance**: Material, display name, lore, model
- **Banking settings**: Account naming patterns, storage backend

For detailed configuration options, see [CONFIGURATION.md](CONFIGURATION.md).

### Setting Up Resource Pack (Optional)

To use custom coin textures:

1. Create or obtain a resource pack with the coin model
2. The model should be located at `assets/cotr/models/item/coin.json` (or match your `coin.model` setting)
3. Configure the model in `config.yml`:
   ```yaml
   coin:
     model: "cotr:coin"
   ```
4. Distribute the resource pack to your players
5. Players without the resource pack will see the fallback material (default: gold nugget)

For more information on resource pack setup, see the [Configuration Reference](CONFIGURATION.md#coinmodel).

## ServiceIO Setup Details

### What is ServiceIO?

ServiceIO is a service-oriented API framework for Minecraft plugins. Coin of the Realm uses ServiceIO's `BankController` API to manage bank accounts and transactions.

### ServiceIO Requirements

- **Version**: 2.3.1 or higher (as specified in build.gradle.kts)
- **Repository**: ServiceIO is available from `https://repo.thenextlvl.net/releases`
- **Dependencies**: ServiceIO may have its own dependencies - check the ServiceIO documentation

### ServiceIO Configuration

ServiceIO typically requires its own configuration. Refer to the [ServiceIO documentation](https://github.com/thenextlvl/service-io) for ServiceIO-specific setup instructions.

### How Coin of the Realm Uses ServiceIO

Coin of the Realm integrates with ServiceIO using reflection, which means:

- The plugin can run without ServiceIO (banking features will be disabled)
- No hard dependency on ServiceIO classes (prevents ClassNotFoundException)
- Banking features are automatically enabled when ServiceIO is detected
- The plugin uses ServiceIO's `BankController` for account persistence

### ServiceIO Integration Architecture

```
Coin of the Realm
    │
    ├── BankManager (our code)
    │       │
    │       ├── AccountMembershipManager (many-to-many relationships)
    │       │
    │       └── BankReflectionHelper (reflection wrapper)
    │               │
    │               └── ServiceIO BankController (external API)
```

The `BankManager` acts as a bridge between:
- **AccountMembershipManager**: Our custom many-to-many player-account system
- **ServiceIO BankController**: External banking API (one owner per bank)

## Troubleshooting

### Banking Not Working

**Symptoms**: Banking commands return "Banking is not available" or similar errors.

**Solutions**:

1. **Check ServiceIO Installation**:
   - Verify ServiceIO JAR is in the `plugins` folder
   - Check server console for ServiceIO startup messages
   - Ensure ServiceIO version is 2.3.1 or higher

2. **Check Configuration**:
   - Open `plugins/CoinOfTheRealm/config.yml`
   - Verify `banking.enabled` is set to `true`
   - Restart the server after changing configuration

3. **Check Server Logs**:
   - Look for messages like: `ServiceIO not found. Banking features disabled.`
   - Look for messages like: `BankController loaded successfully. Banking features enabled.`
   - Check for any error messages related to ServiceIO

4. **Verify ServiceIO is Running**:
   - ServiceIO must be fully loaded before Coin of the Realm
   - Check plugin load order if using a plugin manager
   - Ensure ServiceIO doesn't have any startup errors

### ServiceIO Not Detected

**Symptoms**: Console shows "ServiceIO not found" even though ServiceIO is installed.

**Solutions**:

1. **Check Plugin Load Order**:
   - ServiceIO must load before Coin of the Realm
   - Some servers allow you to specify load order
   - Try renaming ServiceIO JAR to start with `00-` to load it first

2. **Verify ServiceIO Version**:
   - Ensure you're using ServiceIO 2.3.1 or higher
   - Older versions may not be compatible

3. **Check ServiceIO API**:
   - Verify ServiceIO's `BankController` is available
   - Check ServiceIO documentation for API changes
   - Ensure ServiceIO is properly configured

4. **Server Compatibility**:
   - Ensure your server version is compatible with ServiceIO
   - Check ServiceIO's compatibility requirements

### Configuration Errors

**Symptoms**: Plugin fails to start or configuration doesn't work.

**Solutions**:

1. **Check YAML Syntax**:
   - Verify proper indentation (use spaces, not tabs)
   - Ensure colons and dashes are correct
   - Check for missing quotes around strings with special characters

2. **Validate Configuration Values**:
   - Check that `coin.item` is a valid Minecraft item
   - Verify `banking.enabled` is `true` or `false` (not a string)
   - Ensure `banking.default-account-pattern` contains `{player-uuid}`

3. **Reset Configuration**:
   - Delete `config.yml` and let the plugin regenerate it
   - Compare your config with the default configuration
   - See [CONFIGURATION.md](CONFIGURATION.md) for valid values

### Plugin Not Loading

**Symptoms**: Plugin doesn't appear in `/plugins` list or server console.

**Solutions**:

1. **Check Server Version**:
   - Ensure server is Minecraft 1.21.11 or higher
   - Verify you downloaded the correct plugin version

2. **Check Java Version**:
   - Plugin requires Java 21 or higher
   - Run `java -version` to check your Java version

3. **Check Server Software**:
   - Plugin requires Paper or compatible fork
   - Spigot/CraftBukkit may not work correctly

4. **Check Server Logs**:
   - Look for error messages in `logs/latest.log`
   - Check for ClassNotFoundException or other errors
   - Verify all dependencies are available

### Commands Not Working

**Symptoms**: Commands return permission errors or don't execute.

**Solutions**:

1. **Check Permissions**:
   - Verify you have the required permissions (see README.md)
   - Use a permissions plugin to check your permissions
   - Default permissions may require OP status

2. **Check Command Registration**:
   - Verify plugin loaded successfully
   - Check console for command registration messages
   - Try using `/plugins` to verify plugin is enabled

3. **Check Command Syntax**:
   - Verify you're using the correct command syntax
   - See README.md for complete command list
   - Check for typos in command names

## Upgrade Instructions

### Upgrading from Previous Versions

1. **Backup Your Data**:
   - Copy `plugins/CoinOfTheRealm/` folder
   - Backup `account-memberships.yml` if it exists

2. **Update the Plugin**:
   - Stop your server
   - Remove the old JAR file
   - Place the new JAR file in `plugins` folder
   - Start your server

3. **Check Configuration**:
   - Compare new `config.yml` with your old configuration
   - Update any deprecated settings (like `custom-model-data` → `model`)
   - See [CONFIGURATION.md](CONFIGURATION.md) for changes

4. **Verify Functionality**:
   - Test basic coin operations
   - Test banking features (if enabled)
   - Check server logs for warnings or errors

### Migrating from Physical Coins Only to Banking

If you previously used Coin of the Realm without banking and want to add it:

1. Follow the "Installation with Banking Features" section above
2. Existing physical coins will continue to work
3. Players will need to deposit physical coins to use banking features
4. Default accounts will be created automatically when players first use banking

## Additional Resources

- [Configuration Reference](CONFIGURATION.md) - Complete config.yml documentation
- [API Documentation](API.md) - Developer integration guide
- [Architecture Documentation](ARCHITECTURE.md) - System design details
- [ServiceIO Documentation](https://github.com/thenextlvl/service-io) - ServiceIO setup and usage

## Getting Help

If you encounter issues not covered in this guide:

1. Check the [GitHub Issues](https://github.com/radawson/cotr/issues) page
2. Review server logs for error messages
3. Verify all prerequisites are met
4. Consult the other documentation files in the `docs/` folder
