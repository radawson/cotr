# API Documentation

This document provides a comprehensive reference for the Coin of the Realm plugin API, intended for developers who want to integrate with or extend the plugin.

## Getting Started

### Obtaining the Plugin Instance

```java
import org.clockworx.cotr.CoinOfTheRealmPlugin;

CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();

if (plugin == null) {
    // Plugin is not enabled
    return;
}
```

### Checking Plugin Availability

Always check if the plugin instance is available before using it:

```java
CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
if (plugin == null) {
    getLogger().warning("Coin of the Realm is not enabled!");
    return;
}
```

## Coin Item API

The `CoinItem` class provides static methods for creating and identifying coin items.

### Creating Coins

#### `createCoin(int amount)`

Creates a coin ItemStack with the specified amount.

**Parameters**:
- `amount` (int): Number of coins in the stack (1-64)

**Returns**: `ItemStack` - A new coin ItemStack

**Throws**:
- `IllegalArgumentException` - If amount is less than 1 or greater than 64
- `IllegalStateException` - If the plugin or config is not available

**Example**:
```java
import org.clockworx.cotr.item.CoinItem;
import org.bukkit.inventory.ItemStack;

// Create a stack of 10 coins
ItemStack coins = CoinItem.createCoin(10);

// Give to player
player.getInventory().addItem(coins);
```

#### `createCoin()`

Creates a single coin ItemStack (convenience method).

**Returns**: `ItemStack` - A new coin ItemStack containing 1 coin

**Example**:
```java
ItemStack singleCoin = CoinItem.createCoin();
```

### Identifying Coins

#### `isCoin(ItemStack item)`

Checks if an ItemStack is a coin.

**Parameters**:
- `item` (ItemStack): The item to check

**Returns**: `boolean` - `true` if the item is a coin, `false` otherwise

**Example**:
```java
ItemStack item = player.getInventory().getItemInMainHand();

if (CoinItem.isCoin(item)) {
    player.sendMessage("You're holding coins!");
}
```

**Notes**:
- Returns `false` if the item is `null`
- Validates both material and NBT data
- Works with custom items if they have the correct NBT identifier

#### `getCoinAmount(ItemStack item)`

Gets the number of coins in an ItemStack.

**Parameters**:
- `item` (ItemStack): The coin ItemStack

**Returns**: `int` - The number of coins, or `0` if not a coin

**Example**:
```java
ItemStack coins = player.getInventory().getItemInMainHand();
int amount = CoinItem.getCoinAmount(coins);

if (amount > 0) {
    player.sendMessage("You have " + amount + " coins!");
}
```

### Counting Coins in Inventory

Example utility method for counting all coins in a player's inventory:

```java
public int countPlayerCoins(Player player) {
    int total = 0;
    for (ItemStack item : player.getInventory().getContents()) {
        if (item != null && CoinItem.isCoin(item)) {
            total += CoinItem.getCoinAmount(item);
        }
    }
    return total;
}
```

## Banking API

The banking API provides access to bank account management and transactions. All banking operations are asynchronous and return `CompletableFuture`.

### Getting the BankManager

```java
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.bank.BankManager;

CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
BankManager bankManager = plugin.getBankManager();

if (bankManager == null || !bankManager.isBankingEnabled()) {
    // Banking is not available
    return;
}
```

### Checking Banking Availability

```java
if (bankManager.isBankingEnabled()) {
    // Banking features are available
} else {
    // Banking is disabled or ServiceIO is not installed
}
```

### Account Operations

#### `getDefaultAccount(Player player)`

Gets or creates the player's default account.

**Parameters**:
- `player` (Player): The player

**Returns**: `CompletableFuture<String>` - The default account name, or `null` if creation failed

**Example**:
```java
bankManager.getDefaultAccount(player).thenAccept(accountName -> {
    if (accountName != null) {
        player.sendMessage("Your default account: " + accountName);
    } else {
        player.sendMessage("Failed to get default account");
    }
});
```

#### `createAccount(Player owner, String accountName)`

Creates a new bank account.

**Parameters**:
- `owner` (Player): The account owner
- `accountName` (String): Unique account name

**Returns**: `CompletableFuture<Boolean>` - `true` if created, `false` if already exists

**Example**:
```java
bankManager.createAccount(player, "guild-treasury").thenAccept(success -> {
    if (success) {
        player.sendMessage("Account created!");
    } else {
        player.sendMessage("Account already exists");
    }
});
```

#### `getPlayerAccounts(Player player)`

Gets all accounts a player has access to.

**Parameters**:
- `player` (Player): The player

**Returns**: `Set<String>` - Set of account names

**Example**:
```java
Set<String> accounts = bankManager.getPlayerAccounts(player);
player.sendMessage("You have access to " + accounts.size() + " accounts");
```

#### `getAccount(Player player, String accountName)`

Gets an account if the player has access.

**Parameters**:
- `player` (Player): The player requesting access
- `accountName` (String): The account name

**Returns**: `CompletableFuture<Object>` - The Bank object (from ServiceIO), or `null` if no access

**Note**: The returned object is ServiceIO's Bank type, accessed via reflection.

## ServiceIO Integration

Coin of the Realm implements the ServiceIO BankController interface, allowing other plugins to discover and use our banking system.

### Getting the BankController

```java
import net.thenextlvl.service.api.economy.bank.BankController;
import org.bukkit.plugin.ServicesManager;

ServicesManager servicesManager = getServer().getServicesManager();
BankController bankController = servicesManager.load(BankController.class);

if (bankController != null) {
    // Use the BankController
    // Check if it's Coin of the Realm's implementation
    if (bankController instanceof org.clockworx.cotr.bank.impl.CotrBankController) {
        // It's our implementation
    }
}
```

### BankController API

The BankController provides methods for managing banks:

#### `createBank(UUID owner, String name)`
Creates a new global bank account.

**Parameters**:
- `owner` (UUID): The bank owner's UUID
- `name` (String): Unique bank name

**Returns**: `CompletableFuture<Bank>` - The created bank, or fails if already exists

#### `createBank(UUID owner, String name, World world)`
Creates a new world-specific bank account.

**Parameters**:
- `owner` (UUID): The bank owner's UUID
- `name` (String): Unique bank name
- `world` (World): The world for this bank

**Returns**: `CompletableFuture<Bank>` - The created bank, or fails if already exists

#### `loadBank(String name)`
Loads a bank by name.

**Parameters**:
- `name` (String): The bank name

**Returns**: `CompletableFuture<Bank>` - The bank, or `null` if not found

#### `loadBank(UUID owner)`
Loads a global bank by owner UUID.

**Parameters**:
- `owner` (UUID): The owner UUID

**Returns**: `CompletableFuture<Bank>` - The bank, or `null` if not found

#### `loadBank(UUID owner, World world)`
Loads a world-specific bank by owner and world.

**Parameters**:
- `owner` (UUID): The owner UUID
- `world` (World): The world

**Returns**: `CompletableFuture<Bank>` - The bank, or `null` if not found

#### `deleteBank(String name)`
Deletes a bank by name.

**Parameters**:
- `name` (String): The bank name

**Returns**: `CompletableFuture<Boolean>` - `true` if deleted, `false` if not found

#### `getBank(String name)`
Gets a cached bank by name (synchronous).

**Parameters**:
- `name` (String): The bank name

**Returns**: `Optional<Bank>` - The bank if cached

#### `format(Number amount)`
Formats a currency amount as a string.

**Parameters**:
- `amount` (Number): The amount to format

**Returns**: `String` - Formatted string (e.g., "50 Coins of the Realm")

#### `fractionalDigits()`
Returns the number of fractional digits supported.

**Returns**: `int` - Always returns `0` (coins are whole numbers)

### Bank API

The Bank interface provides account operations:

#### `getOwner()`
Gets the bank owner's UUID.

**Returns**: `UUID` - The owner UUID

#### `getName()`
Gets the bank name.

**Returns**: `String` - The bank name

#### `getWorld()`
Gets the world for this bank (if world-specific).

**Returns**: `Optional<World>` - The world, or empty for global banks

#### `getBalance()`
Gets the current balance.

**Returns**: `BigDecimal` - The balance

#### `deposit(Number amount)`
Deposits funds into the bank.

**Parameters**:
- `amount` (Number): The amount to deposit

**Returns**: `BigDecimal` - The new balance

**Throws**: `IllegalArgumentException` if amount is negative

#### `withdraw(Number amount)`
Withdraws funds from the bank.

**Parameters**:
- `amount` (Number): The amount to withdraw

**Returns**: `BigDecimal` - The new balance

**Throws**: 
- `IllegalArgumentException` if amount is negative
- `IllegalStateException` if insufficient funds

#### `setBalance(Number amount)`
Sets the balance directly.

**Parameters**:
- `amount` (Number): The new balance

#### `addMember(UUID member)`
Adds a member to the bank.

**Parameters**:
- `member` (UUID): The member UUID

**Returns**: `boolean` - `true` if added

#### `removeMember(UUID member)`
Removes a member from the bank.

**Parameters**:
- `member` (UUID): The member UUID

**Returns**: `boolean` - `true` if removed

#### `isMember(UUID member)`
Checks if a UUID is a member.

**Parameters**:
- `member` (UUID): The member UUID

**Returns**: `boolean` - `true` if member

#### `getMembers()`
Gets all member UUIDs.

**Returns**: `Set<UUID>` - Set of member UUIDs

### Example: Using BankController

```java
// Get BankController from ServiceIO
BankController bankController = getServer().getServicesManager().load(BankController.class);

if (bankController == null) {
    getLogger().warning("No BankController available");
    return;
}

// Create a bank account
UUID playerUuid = player.getUniqueId();
CompletableFuture<Bank> bankFuture = bankController.createBank(playerUuid, "my-account");

bankFuture.thenAccept(bank -> {
    if (bank != null) {
        // Deposit funds
        BigDecimal newBalance = bank.deposit(BigDecimal.valueOf(100));
        player.sendMessage("Deposited! New balance: " + bankController.format(newBalance));
        
        // Withdraw funds
        try {
            BigDecimal afterWithdraw = bank.withdraw(BigDecimal.valueOf(25));
            player.sendMessage("Withdrew! New balance: " + bankController.format(afterWithdraw));
        } catch (IllegalStateException e) {
            player.sendMessage("Insufficient funds!");
        }
    }
}).exceptionally(ex -> {
    if (ex.getCause() instanceof IllegalStateException) {
        player.sendMessage("Account already exists!");
    } else {
        getLogger().severe("Error creating bank", ex);
    }
    return null;
});
```

### Balance Operations

#### `getBalance(Player player, String accountName)`

Gets the balance of an account.

**Parameters**:
- `player` (Player): The player requesting the balance
- `accountName` (String): The account name

**Returns**: `CompletableFuture<Integer>` - The balance in coins, or `null` if no access

**Example**:
```java
bankManager.getBalance(player, "my-account").thenAccept(balance -> {
    if (balance != null) {
        player.sendMessage("Balance: " + balance + " coins");
    } else {
        player.sendMessage("Account not found or access denied");
    }
});
```

### Transaction Operations

#### `deposit(Player player, String accountName, int amount)`

Deposits coins from a player's inventory to an account.

**Parameters**:
- `player` (Player): The player depositing
- `accountName` (String): The account name (or `null` for default)
- `amount` (int): Amount to deposit (in coins)

**Returns**: `CompletableFuture<Boolean>` - `true` if successful

**Example**:
```java
bankManager.deposit(player, null, 100).thenAccept(success -> {
    if (success) {
        player.sendMessage("Deposited 100 coins!");
    } else {
        player.sendMessage("Deposit failed - check inventory and account access");
    }
});
```

**Notes**:
- Removes coins from player's inventory
- Creates default account if `accountName` is `null` and account doesn't exist
- Refunds coins if deposit fails

#### `withdraw(Player player, String accountName, int amount)`

Withdraws coins from an account to a player's inventory.

**Parameters**:
- `player` (Player): The player withdrawing
- `accountName` (String): The account name (or `null` for default)
- `amount` (int): Amount to withdraw (in coins)

**Returns**: `CompletableFuture<Boolean>` - `true` if successful

**Example**:
```java
bankManager.withdraw(player, null, 50).thenAccept(success -> {
    if (success) {
        player.sendMessage("Withdrew 50 coins!");
    } else {
        player.sendMessage("Withdrawal failed - check balance and account access");
    }
});
```

**Notes**:
- Adds coins to player's inventory
- Drops overflow coins if inventory is full
- Creates default account if `accountName` is `null` and account doesn't exist

#### `transfer(Player from, String fromAccount, Player to, String toAccount, int amount)`

Transfers funds between two accounts.

**Parameters**:
- `from` (Player): The player initiating the transfer
- `fromAccount` (String): Source account name
- `to` (Player): Target player
- `toAccount` (String): Target account name (or `null` for default)
- `amount` (int): Amount to transfer (in coins)

**Returns**: `CompletableFuture<Boolean>` - `true` if successful

**Example**:
```java
Player target = Bukkit.getPlayer("OtherPlayer");
bankManager.transfer(player, "my-account", target, null, 200)
    .thenAccept(success -> {
        if (success) {
            player.sendMessage("Transferred 200 coins!");
        } else {
            player.sendMessage("Transfer failed");
        }
    });
```

### Member Management

#### `addMember(Player owner, String accountName, Player newMember, AccountRole role)`

Adds a member to an account (owner only).

**Parameters**:
- `owner` (Player): The account owner
- `accountName` (String): The account name
- `newMember` (Player): The player to add
- `role` (AccountRole): The role to assign (OWNER, MEMBER, VIEWER)

**Returns**: `boolean` - `true` if added

**Example**:
```java
import org.clockworx.cotr.bank.AccountRole;

boolean success = bankManager.addMember(
    owner, 
    "guild-treasury", 
    newMember, 
    AccountRole.MEMBER
);

if (success) {
    owner.sendMessage("Member added!");
}
```

#### `removeMember(Player owner, String accountName, Player member)`

Removes a member from an account (owner only).

**Parameters**:
- `owner` (Player): The account owner
- `accountName` (String): The account name
- `member` (Player): The player to remove

**Returns**: `boolean` - `true` if removed

#### `getAccountMembers(String accountName)`

Gets all members of an account.

**Parameters**:
- `accountName` (String): The account name

**Returns**: `Set<AccountMembership>` - Set of account memberships

**Example**:
```java
import org.clockworx.cotr.bank.AccountMembership;

Set<AccountMembership> members = bankManager.getAccountMembers("guild-treasury");
for (AccountMembership membership : members) {
    String playerName = Bukkit.getOfflinePlayer(membership.getPlayerUuid()).getName();
    player.sendMessage(playerName + " - " + membership.getRole());
}
```

#### `deleteAccount(Player owner, String accountName)`

Deletes an account (owner only).

**Parameters**:
- `owner` (Player): The account owner
- `accountName` (String): The account name

**Returns**: `CompletableFuture<Boolean>` - `true` if deleted

## Configuration API

### Getting Configuration

```java
import org.clockworx.cotr.config.ConfigManager;
import org.clockworx.cotr.config.CoinConfig;

CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
ConfigManager configManager = plugin.getConfigManager();
CoinConfig coinConfig = configManager.getCoinConfig();

// Access configuration values
String displayName = coinConfig.getDisplayName();
List<String> lore = coinConfig.getLore();
Material material = coinConfig.getMaterial();
```

### Configuration Values

#### CoinConfig Methods

- `getDisplayName()` - Returns the coin display name
- `getLore()` - Returns the lore lines
- `getMaterial()` - Returns the Material (or null for custom items)
- `getItemKey()` - Returns the raw item key string
- `getItemModelKey()` - Returns the NamespacedKey for the item model
- `isVanillaItem()` - Checks if using a vanilla Material

## NBT Data Structure

Coins are identified using NBT (Named Binary Tags) data stored in the item's PersistentDataContainer.

### NBT Key

- **Key**: `cotr:coin` (as NamespacedKey)
- **Type**: `PersistentDataType.BOOLEAN`
- **Value**: `true`

### Accessing NBT Data

```java
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
NamespacedKey coinKey = plugin.getKey("cotr:coin");

ItemMeta meta = itemStack.getItemMeta();
if (meta != null) {
    boolean isCoin = meta.getPersistentDataContainer()
        .has(coinKey, PersistentDataType.BOOLEAN);
}
```

## Integration Examples

### Example 1: Shop Plugin Integration

```java
public class ShopIntegration {
    
    public boolean purchaseItem(Player player, int price) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin == null) return false;
        
        // Count coins in inventory
        int coins = countPlayerCoins(player);
        
        if (coins < price) {
            player.sendMessage("Not enough coins! You need " + price);
            return false;
        }
        
        // Remove coins
        removeCoinsFromInventory(player, price);
        
        // Give item
        // ... give item to player ...
        
        return true;
    }
    
    private int countPlayerCoins(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CoinItem.isCoin(item)) {
                total += CoinItem.getCoinAmount(item);
            }
        }
        return total;
    }
    
    private void removeCoinsFromInventory(Player player, int amount) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && CoinItem.isCoin(item)) {
                int itemAmount = item.getAmount();
                if (itemAmount <= amount) {
                    player.getInventory().removeItem(item);
                    amount -= itemAmount;
                } else {
                    item.setAmount(itemAmount - amount);
                    amount = 0;
                }
                if (amount <= 0) break;
            }
        }
    }
}
```

### Example 2: Banking Integration

```java
public class BankingIntegration {
    
    public void payPlayer(Player from, Player to, int amount) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        BankManager bankManager = plugin.getBankManager();
        
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            from.sendMessage("Banking is not available");
            return;
        }
        
        // Transfer from default account to default account
        bankManager.transfer(from, null, to, null, amount)
            .thenAccept(success -> {
                if (success) {
                    from.sendMessage("Paid " + amount + " coins to " + to.getName());
                    to.sendMessage("Received " + amount + " coins from " + from.getName());
                } else {
                    from.sendMessage("Payment failed");
                }
            });
    }
    
    public void checkBalance(Player player) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        BankManager bankManager = plugin.getBankManager();
        
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            return;
        }
        
        bankManager.getDefaultAccount(player).thenCompose(accountName -> {
            if (accountName == null) {
                player.sendMessage("No account found");
                return CompletableFuture.completedFuture(null);
            }
            
            return bankManager.getBalance(player, accountName);
        }).thenAccept(balance -> {
            if (balance != null) {
                player.sendMessage("Your balance: " + balance + " coins");
            }
        });
    }
}
```

### Example 3: Event Listener

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class CoinListener implements Listener {
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        if (plugin == null) return;
        
        BankManager bankManager = plugin.getBankManager();
        if (bankManager != null && bankManager.isBankingEnabled()) {
            // Create default account on first join
            bankManager.getDefaultAccount(player).thenAccept(accountName -> {
                if (accountName != null) {
                    player.sendMessage("Welcome! Your account: " + accountName);
                }
            });
        }
    }
}
```

## Constants

### Plugin Constants

```java
// NBT key for coin identification
String COIN_NBT_KEY = "cotr:coin";

// CustomModelData value (deprecated, use model instead)
int COIN_CUSTOM_MODEL_DATA = 1000;
```

## Error Handling

### Common Exceptions

1. **IllegalArgumentException**: Invalid parameters (e.g., amount out of range)
2. **IllegalStateException**: Plugin not available
3. **NullPointerException**: Null parameters (always check for null)

### Best Practices

1. **Always check plugin availability**:
   ```java
   CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
   if (plugin == null) return;
   ```

2. **Handle CompletableFuture errors**:
   ```java
   bankManager.deposit(player, null, amount)
       .exceptionally(ex -> {
           getLogger().warning("Deposit failed: " + ex.getMessage());
           return false;
       });
   ```

3. **Validate inputs**:
   ```java
   if (amount <= 0) {
       throw new IllegalArgumentException("Amount must be positive");
   }
   ```

## Thread Safety

- **CoinItem**: Thread-safe (static methods, no shared state)
- **BankManager**: Thread-safe (uses thread-safe collections)
- **ConfigManager**: Thread-safe (read-only after initialization)

## Version Compatibility

- **Minecraft**: 1.21.11+
- **Java**: 21+
- **ServiceIO**: 2.3.1+ (for banking features)

## Related Documentation

- [Architecture Documentation](ARCHITECTURE.md) - System design and architecture
- [Configuration Reference](CONFIGURATION.md) - Configuration options
- [Installation Guide](INSTALLATION.md) - Setup instructions
- [Development Guide](DEVELOPMENT.md) - Building the project
