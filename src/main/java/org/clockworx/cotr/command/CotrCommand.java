package org.clockworx.cotr.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.bank.AccountMembership;
import org.clockworx.cotr.bank.AccountRole;
import org.clockworx.cotr.bank.BankManager;
import org.clockworx.cotr.bank.exchange.BankExchangeService;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.item.CoinItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CotrCommand - Handles the /cotr command and its subcommands
 * 
 * This command provides multiple subcommands:
 * - Physical coin operations: drop, give (physical)
 * - Banking operations: deposit, withdraw, balance, give (bank-to-bank), request
 * - Account management: account create, list, members, add, remove, delete
 * - Admin: info
 * 
 * Uses Paper's native command system which leverages Brigadier under the hood.
 */
public class CotrCommand implements CommandExecutor, TabCompleter {
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.onCommand() - sender={}, label={}, args={}", 
            sender.getName(), label, java.util.Arrays.toString(args));
        
        // Check base permission (replaces deprecated setPermissionMessage)
        if (!sender.hasPermission("cotr.command.use")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        // Check if any arguments were provided
        if (args.length == 0) {
            plugin.debug("CotrCommand.onCommand() - No arguments provided, showing usage");
            sendUsage(sender);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        plugin.debug("CotrCommand.onCommand() - Routing to subcommand: {}", subcommand);
        
        // Route to appropriate subcommand handler
        switch (subcommand) {
            case "drop":
                return handleDrop(sender, args);
            case "give":
                return handleGive(sender, args);
            case "deposit":
                return handleDeposit(sender, args);
            case "withdraw":
                return handleWithdraw(sender, args);
            case "balance":
                return handleBalance(sender, args);
            case "rate":
                return handleRate(sender, args);
            case "request":
                return handleRequest(sender, args);
            case "account":
                return handleAccount(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "reload":
                return handleReload(sender, args);
            default:
                plugin.debug("CotrCommand.onCommand() - Unknown subcommand: {}", subcommand);
                sender.sendMessage(Component.text("Unknown subcommand: " + subcommand, NamedTextColor.RED));
                sendUsage(sender);
                return true;
        }
    }
    
    /**
     * Sends the usage message to the sender.
     */
    private void sendUsage(@NotNull CommandSender sender) {
        sender.sendMessage(Component.text("Usage: /cotr <command> [arguments]", NamedTextColor.RED));
        sender.sendMessage(Component.text("  drop <amount> - Drop coins at your location", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  give <player> <amount> - Give coins to a player (physical)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  deposit [account] <amount> - Deposit coins to bank", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  deposit emerald [account] <amount> - Convert emeralds to bank coins", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  withdraw [account] <amount> - Withdraw coins from bank", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  withdraw emerald [account] <amount> - Convert bank coins to emeralds", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  balance [account] - View bank balance", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  rate emerald - View emerald exchange rate", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  give <player> [account] <amount> - Transfer coins (bank-to-bank)", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  request <player> [account] <amount> - Request coins from player", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  account <create|list|members|add|remove|delete> - Manage accounts", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  info - Admin: View plugin information", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("  reload - Admin: Reload config.yml / coin.yml from disk", NamedTextColor.GRAY));
    }
    
    /**
     * Handles the /cotr drop [amount] subcommand.
     */
    private boolean handleDrop(@NotNull CommandSender sender, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.handleDrop() - sender={}, args={}", sender.getName(), java.util.Arrays.toString(args));
        
        if (!sender.hasPermission("cotr.command.drop")) {
            plugin.debug("CotrCommand.handleDrop() - Permission check failed");
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.debug("CotrCommand.handleDrop() - Sender is not a player");
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length < 2) {
            plugin.debug("CotrCommand.handleDrop() - Insufficient arguments");
            sender.sendMessage(Component.text("Usage: /cotr drop <amount>", NamedTextColor.RED));
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
            plugin.debug("CotrCommand.handleDrop() - Parsed amount: {}", amount);
        } catch (NumberFormatException e) {
            plugin.debug("CotrCommand.handleDrop() - Invalid amount format: {}", args[1]);
            sender.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
            return true;
        }
        
        if (amount < 1 || amount > 64) {
            plugin.debug("CotrCommand.handleDrop() - Amount out of range: {}", amount);
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.RED));
            return true;
        }
        
        try {
            plugin.debug("CotrCommand.handleDrop() - Creating coin ItemStack with amount: {}", amount);
            ItemStack coin = CoinItem.createCoin(amount);
            org.bukkit.Location dropLocation = player.getLocation().add(
                player.getLocation().getDirection().multiply(0.5)
            );
            dropLocation.setY(dropLocation.getY() + player.getEyeHeight() - 0.3);
            plugin.debug("CotrCommand.handleDrop() - Drop location: {}", dropLocation);
            
            org.bukkit.entity.ItemDisplay coinDisplay = CoinEntityManager.createCoinDisplay(dropLocation, coin);
            if (coinDisplay != null) {
                plugin.debug("CotrCommand.handleDrop() - ItemDisplay created, adding velocity");
                coinDisplay.setVelocity(player.getLocation().getDirection().multiply(0.3));
                sender.sendMessage(Component.text("Dropped " + amount + " coin(s)!", NamedTextColor.GREEN));
            } else {
                plugin.debug("CotrCommand.handleDrop() - ItemDisplay creation failed, using normal drop");
                player.getWorld().dropItemNaturally(dropLocation, coin);
                sender.sendMessage(Component.text("Dropped " + amount + " coin(s)!", NamedTextColor.GREEN));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error creating coin: " + e.getMessage(), NamedTextColor.RED));
        }
        
        return true;
    }
    
    /**
     * Handles the /cotr give [user] [amount] subcommand (physical coins).
     */
    private boolean handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.handleGive() - sender={}, args={}", sender.getName(), java.util.Arrays.toString(args));
        
        if (!sender.hasPermission("cotr.command.give")) {
            plugin.debug("CotrCommand.handleGive() - Permission check failed");
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        // Check if this is bank-to-bank transfer (3+ args) or physical give (3 args)
        if (args.length >= 4) {
            plugin.debug("CotrCommand.handleGive() - Detected bank-to-bank transfer (4+ args)");
            // Bank-to-bank transfer
            return handleBankTransfer(sender, args);
        }
        
        // Physical coin give
        plugin.debug("CotrCommand.handleGive() - Processing physical coin give");
        if (args.length < 3) {
            plugin.debug("CotrCommand.handleGive() - Insufficient arguments");
            sender.sendMessage(Component.text("Usage: /cotr give <player> <amount>", NamedTextColor.RED));
            return true;
        }
        
        String targetName = args[1];
        plugin.debug("CotrCommand.handleGive() - Target player: {}", targetName);
        Player target = sender.getServer().getPlayer(targetName);
        if (target == null) {
            plugin.debug("CotrCommand.handleGive() - Player not found: {}", targetName);
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            plugin.debug("CotrCommand.handleGive() - Parsed amount: {}", amount);
        } catch (NumberFormatException e) {
            plugin.debug("CotrCommand.handleGive() - Invalid amount format: {}", args[2]);
            sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
            return true;
        }
        
        if (amount < 1 || amount > 64) {
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.RED));
            return true;
        }
        
        try {
            ItemStack coin = CoinItem.createCoin(amount);
            boolean success = CoinEntityManager.giveCoinToPlayer(target, coin);
            
            if (success) {
                sender.sendMessage(Component.text("Gave " + amount + " coin(s) to " + target.getName() + "!", NamedTextColor.GREEN));
                if (!sender.getName().equals(target.getName())) {
                    target.sendMessage(Component.text("You received " + amount + " coin(s)!", NamedTextColor.GREEN));
                }
            } else {
                sender.sendMessage(Component.text("Failed to give coins to " + target.getName(), NamedTextColor.RED));
            }
        } catch (Exception e) {
            sender.sendMessage(Component.text("Error creating coin: " + e.getMessage(), NamedTextColor.RED));
        }
        
        return true;
    }
    
    /**
     * Handles bank-to-bank transfer: /cotr give <player> [account] <amount>
     */
    private boolean handleBankTransfer(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player fromPlayer = (Player) sender;
        BankManager bankManager = getBankManager();
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            sender.sendMessage(Component.text("Banking is not available.", NamedTextColor.RED));
            return true;
        }
        
        String targetName = args[1];
        Player target = sender.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        // Parse arguments: could be "give player account amount" or "give player amount" (using defaults)
        // fromAccount will be determined by getDefaultAccount()
        final String toAccount;
        int amount;
        
        if (args.length == 4) {
            // /cotr give player amount (both use defaults)
            toAccount = null;
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else if (args.length == 5) {
            // /cotr give player account amount
            toAccount = args[2];
            try {
                amount = Integer.parseInt(args[4]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[4] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            sender.sendMessage(Component.text("Usage: /cotr give <player> [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        final int finalAmount = amount;
        
        // Get default account for sender
        bankManager.getDefaultAccount(fromPlayer).thenCompose(fromAcc -> {
            if (fromAcc == null) {
                sender.sendMessage(Component.text("Failed to get your default account.", NamedTextColor.RED));
                return CompletableFuture.completedFuture(false);
            }
            
            return bankManager.transfer(fromPlayer, fromAcc, target, toAccount, finalAmount)
                .thenApply(success -> {
                    if (success) {
                        sender.sendMessage(Component.text("Transferred " + amount + " coins to " + target.getName() + "!", NamedTextColor.GREEN));
                        target.sendMessage(Component.text("Received " + amount + " coins from " + fromPlayer.getName() + "!", NamedTextColor.GREEN));
                    } else {
                        sender.sendMessage(Component.text("Transfer failed. Check your balance and account access.", NamedTextColor.RED));
                    }
                    return success;
                });
        });
        
        return true;
    }
    
    /**
     * Handles /cotr deposit [account] <amount>
     */
    private boolean handleDeposit(@NotNull CommandSender sender, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.handleDeposit() - sender={}, args={}", sender.getName(), java.util.Arrays.toString(args));
        
        if (!sender.hasPermission("cotr.command.deposit")) {
            plugin.debug("CotrCommand.handleDeposit() - Permission check failed");
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.debug("CotrCommand.handleDeposit() - Sender is not a player");
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankManager bankManager = getBankManager();
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            plugin.debug("CotrCommand.handleDeposit() - Banking not available");
            sender.sendMessage(Component.text("Banking is not available.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2) {
            plugin.debug("CotrCommand.handleDeposit() - Insufficient arguments");
            sender.sendMessage(Component.text("Usage: /cotr deposit [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        if (args[1].equalsIgnoreCase("emerald")) {
            return handleDepositEmerald(sender, args);
        }
        
        String accountName = null;
        int amount;
        
        if (args.length == 2) {
            // /cotr deposit <amount> (use default account)
            plugin.debug("CotrCommand.handleDeposit() - Using default account");
            try {
                amount = Integer.parseInt(args[1]);
                plugin.debug("CotrCommand.handleDeposit() - Parsed amount: {}", amount);
            } catch (NumberFormatException e) {
                plugin.debug("CotrCommand.handleDeposit() - Invalid amount format: {}", args[1]);
                sender.sendMessage(Component.text("Invalid amount: " + args[1] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            // /cotr deposit <account> <amount>
            accountName = args[1];
            plugin.debug("CotrCommand.handleDeposit() - Using account: {}", accountName);
            try {
                amount = Integer.parseInt(args[2]);
                plugin.debug("CotrCommand.handleDeposit() - Parsed amount: {}", amount);
            } catch (NumberFormatException e) {
                plugin.debug("CotrCommand.handleDeposit() - Invalid amount format: {}", args[2]);
                sender.sendMessage(Component.text("Invalid amount: " + args[2] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        }
        
        if (amount <= 0) {
            plugin.debug("CotrCommand.handleDeposit() - Amount must be > 0, got: {}", amount);
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        plugin.debug("CotrCommand.handleDeposit() - Initiating deposit: player={}, account={}, amount={}", 
            player.getName(), accountName, amount);
        bankManager.deposit(player, accountName, amount).thenAccept(success -> {
            plugin.debug("CotrCommand.handleDeposit() - Deposit result: {}", success);
            if (success) {
                sender.sendMessage(Component.text("Deposited " + amount + " coins!", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Deposit failed. Check your inventory and account access.", NamedTextColor.RED));
            }
        });
        
        return true;
    }
    
    /**
     * Handles /cotr withdraw [account] <amount>
     */
    private boolean handleWithdraw(@NotNull CommandSender sender, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.handleWithdraw() - sender={}, args={}", sender.getName(), java.util.Arrays.toString(args));
        
        if (!sender.hasPermission("cotr.command.withdraw")) {
            plugin.debug("CotrCommand.handleWithdraw() - Permission check failed");
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.debug("CotrCommand.handleWithdraw() - Sender is not a player");
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankManager bankManager = getBankManager();
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            plugin.debug("CotrCommand.handleWithdraw() - Banking not available");
            sender.sendMessage(Component.text("Banking is not available.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /cotr withdraw [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        if (args[1].equalsIgnoreCase("emerald")) {
            return handleWithdrawEmerald(sender, args);
        }
        
        String accountName = null;
        int amount;
        
        if (args.length == 2) {
            try {
                amount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[1] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            accountName = args[1];
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        }
        
        if (amount <= 0) {
            plugin.debug("CotrCommand.handleWithdraw() - Amount must be > 0, got: {}", amount);
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        plugin.debug("CotrCommand.handleWithdraw() - Initiating withdrawal: player={}, account={}, amount={}", 
            player.getName(), accountName, amount);
        bankManager.withdraw(player, accountName, amount).thenAccept(success -> {
            plugin.debug("CotrCommand.handleWithdraw() - Withdrawal result: {}", success);
            if (success) {
                sender.sendMessage(Component.text("Withdrew " + amount + " coins!", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("Withdrawal failed. Check your balance and account access.", NamedTextColor.RED));
            }
        });
        
        return true;
    }
    
    /**
     * Handles /cotr deposit emerald [account] <amount>
     */
    private boolean handleDepositEmerald(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankExchangeService exchangeService = getExchangeService();
        if (exchangeService == null) {
            sender.sendMessage(Component.text("Emerald exchange is not available.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /cotr deposit emerald [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = null;
        int amount;
        
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else if (args.length == 4) {
            accountName = args[2];
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            sender.sendMessage(Component.text("Usage: /cotr deposit emerald [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        exchangeService.depositEmerald(player, accountName, amount).thenAccept(result -> {
            if (result == null) {
                sender.sendMessage(Component.text("Emerald deposit failed. Check your emeralds and account access.", NamedTextColor.RED));
                return;
            }
            
            sender.sendMessage(Component.text("Deposited " + result.getEmeraldAmount() + " emerald(s) for " +
                result.getCoinAmount() + " coins.", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Rate: 1 emerald = " + result.getRate() + " coins", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Balance (" + result.getAccountName() + "): " + result.getNewBalance() + " coins", NamedTextColor.GRAY));
        });
        
        return true;
    }
    
    /**
     * Handles /cotr withdraw emerald [account] <amount>
     */
    private boolean handleWithdrawEmerald(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankExchangeService exchangeService = getExchangeService();
        if (exchangeService == null) {
            sender.sendMessage(Component.text("Emerald exchange is not available.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /cotr withdraw emerald [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = null;
        int amount;
        
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else if (args.length == 4) {
            accountName = args[2];
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            sender.sendMessage(Component.text("Usage: /cotr withdraw emerald [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        exchangeService.withdrawEmerald(player, accountName, amount).thenAccept(result -> {
            if (result == null) {
                sender.sendMessage(Component.text("Emerald withdrawal failed. Check your balance and bank reserves.", NamedTextColor.RED));
                return;
            }
            
            sender.sendMessage(Component.text("Withdrew " + result.getEmeraldAmount() + " emerald(s) for " +
                result.getCoinAmount() + " coins.", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("Rate: 1 emerald = " + result.getRate() + " coins", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("Balance (" + result.getAccountName() + "): " + result.getNewBalance() + " coins", NamedTextColor.GRAY));
        });
        
        return true;
    }
    
    /**
     * Handles /cotr balance [account]
     */
    private boolean handleBalance(@NotNull CommandSender sender, @NotNull String[] args) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("CotrCommand.handleBalance() - sender={}, args={}", sender.getName(), java.util.Arrays.toString(args));
        
        if (!sender.hasPermission("cotr.command.balance")) {
            plugin.debug("CotrCommand.handleBalance() - Permission check failed");
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            plugin.debug("CotrCommand.handleBalance() - Sender is not a player");
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankManager bankManager = getBankManager();
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            plugin.debug("CotrCommand.handleBalance() - Banking not available");
            sender.sendMessage(Component.text("Banking is not available.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length == 1) {
            // List all accounts
            plugin.debug("CotrCommand.handleBalance() - Listing all accounts for player {}", player.getName());
            Set<String> accounts = bankManager.getPlayerAccounts(player);
            plugin.debug("CotrCommand.handleBalance() - Found {} accounts", accounts.size());
            if (accounts.isEmpty()) {
                sender.sendMessage(Component.text("You have no bank accounts.", NamedTextColor.YELLOW));
                return true;
            }
            
            sender.sendMessage(Component.text("Your accounts:", NamedTextColor.GOLD));
            for (String accountName : accounts) {
                bankManager.getBalance(player, accountName).thenAccept(balance -> {
                    if (balance != null) {
                        sender.sendMessage(Component.text("  " + accountName + ": " + balance + " coins", NamedTextColor.GRAY));
                    }
                });
            }
        } else {
            // Show specific account balance
            String accountName = args[1];
            bankManager.getBalance(player, accountName).thenAccept(balance -> {
                if (balance != null) {
                    sender.sendMessage(Component.text("Balance in " + accountName + ": " + balance + " coins", NamedTextColor.GREEN));
                } else {
                    sender.sendMessage(Component.text("Account not found or access denied.", NamedTextColor.RED));
                }
            });
        }
        
        return true;
    }
    
    /**
     * Handles /cotr rate emerald
     */
    private boolean handleRate(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cotr.command.rate")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2 || !args[1].equalsIgnoreCase("emerald")) {
            sender.sendMessage(Component.text("Usage: /cotr rate emerald", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankExchangeService exchangeService = getExchangeService();
        if (exchangeService == null) {
            sender.sendMessage(Component.text("Emerald exchange is not available.", NamedTextColor.RED));
            return true;
        }
        
        exchangeService.getRateQuote(player).thenAccept(quote -> {
            sender.sendMessage(Component.text("Emerald exchange rate for region '" + quote.getRegionId() + "':", NamedTextColor.GOLD));
            sender.sendMessage(Component.text("  1 emerald = " + quote.getRate() + " coins", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Tracked supply: " + quote.getSupply() + " emeralds", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  Bank reserve: " + quote.getBankReserve() + " emeralds", NamedTextColor.GRAY));
        });
        
        return true;
    }
    
    /**
     * Handles /cotr request <player> [account] <amount>
     */
    private boolean handleRequest(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cotr.command.request")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /cotr request <player> [account] <amount>", NamedTextColor.RED));
            return true;
        }
        
        String targetName = args[1];
        Player target = sender.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        int amount;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[2] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        } else {
            try {
                amount = Integer.parseInt(args[3]);
            } catch (NumberFormatException e) {
                sender.sendMessage(Component.text("Invalid amount: " + args[3] + " (must be a whole number)", NamedTextColor.RED));
                return true;
            }
        }
        
        if (amount <= 0) {
            sender.sendMessage(Component.text("Amount must be greater than 0", NamedTextColor.RED));
            return true;
        }
        
        // Send request message (approval system can be added later)
        target.sendMessage(Component.text(sender.getName() + " requests " + amount + " coins from you.", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("Request sent to " + target.getName() + ".", NamedTextColor.GREEN));
        
        return true;
    }
    
    /**
     * Handles /cotr account subcommands
     */
    private boolean handleAccount(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cotr.command.account")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /cotr account <create|list|members|add|remove|delete> [arguments]", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        BankManager bankManager = getBankManager();
        if (bankManager == null || !bankManager.isBankingEnabled()) {
            sender.sendMessage(Component.text("Banking is not available.", NamedTextColor.RED));
            return true;
        }
        
        String subcommand = args[1].toLowerCase();
        
        switch (subcommand) {
            case "create":
                return handleAccountCreate(player, args);
            case "list":
                return handleAccountList(player);
            case "members":
                return handleAccountMembers(player, args);
            case "add":
                return handleAccountAdd(player, args);
            case "remove":
                return handleAccountRemove(player, args);
            case "delete":
                return handleAccountDelete(player, args);
            default:
                sender.sendMessage(Component.text("Unknown account subcommand: " + subcommand, NamedTextColor.RED));
                return true;
        }
    }
    
    private boolean handleAccountCreate(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /cotr account create <name>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = args[2];
        BankManager bankManager = getBankManager();
        
        bankManager.createAccount(player, accountName).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Account '" + accountName + "' created!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Failed to create account. It may already exist.", NamedTextColor.RED));
            }
        });
        
        return true;
    }
    
    private boolean handleAccountList(@NotNull Player player) {
        BankManager bankManager = getBankManager();
        Set<String> accounts = bankManager.getPlayerAccounts(player);
        
        if (accounts.isEmpty()) {
            player.sendMessage(Component.text("You have no accounts.", NamedTextColor.YELLOW));
            return true;
        }
        
        player.sendMessage(Component.text("Your accounts:", NamedTextColor.GOLD));
        for (String accountName : accounts) {
            bankManager.getBalance(player, accountName).thenAccept(balance -> {
                if (balance != null) {
                    player.sendMessage(Component.text("  " + accountName + ": " + balance + " coins", NamedTextColor.GRAY));
                }
            });
        }
        
        return true;
    }
    
    private boolean handleAccountMembers(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /cotr account members <account>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = args[2];
        BankManager bankManager = getBankManager();
        Set<AccountMembership> members = bankManager.getAccountMembers(accountName);
        
        if (members.isEmpty()) {
            player.sendMessage(Component.text("Account not found or access denied.", NamedTextColor.RED));
            return true;
        }
        
        player.sendMessage(Component.text("Members of " + accountName + ":", NamedTextColor.GOLD));
        for (AccountMembership membership : members) {
            String playerName = player.getServer().getOfflinePlayer(membership.getPlayerUuid()).getName();
            player.sendMessage(Component.text("  " + playerName + " (" + membership.getRole() + ")", NamedTextColor.GRAY));
        }
        
        return true;
    }
    
    private boolean handleAccountAdd(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /cotr account add <account> <player> [role]", NamedTextColor.RED));
            return true;
        }
        
        String accountName = args[2];
        String targetName = args[3];
        Player target = player.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        AccountRole role = AccountRole.MEMBER;
        if (args.length >= 5) {
            try {
                role = AccountRole.valueOf(args[4].toUpperCase());
            } catch (IllegalArgumentException e) {
                player.sendMessage(Component.text("Invalid role. Use OWNER, MEMBER, USER, CONTRIBUTOR, or VIEWER.", NamedTextColor.RED));
                return true;
            }
        }
        
        BankManager bankManager = getBankManager();
        boolean success = bankManager.addMember(player, accountName, target, role);
        
        if (success) {
            player.sendMessage(Component.text("Added " + targetName + " to account '" + accountName + "' as " + role + "!", NamedTextColor.GREEN));
            target.sendMessage(Component.text("You were added to account '" + accountName + "' as " + role + "!", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Failed to add member. Check that you own the account.", NamedTextColor.RED));
        }
        
        return true;
    }
    
    private boolean handleAccountRemove(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /cotr account remove <account> <player>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = args[2];
        String targetName = args[3];
        Player target = player.getServer().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        BankManager bankManager = getBankManager();
        boolean success = bankManager.removeMember(player, accountName, target);
        
        if (success) {
            player.sendMessage(Component.text("Removed " + targetName + " from account '" + accountName + "'!", NamedTextColor.GREEN));
            target.sendMessage(Component.text("You were removed from account '" + accountName + "'.", NamedTextColor.YELLOW));
        } else {
            player.sendMessage(Component.text("Failed to remove member. Check that you own the account.", NamedTextColor.RED));
        }
        
        return true;
    }
    
    private boolean handleAccountDelete(@NotNull Player player, @NotNull String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /cotr account delete <account>", NamedTextColor.RED));
            return true;
        }
        
        String accountName = args[2];
        BankManager bankManager = getBankManager();
        
        bankManager.deleteAccount(player, accountName).thenAccept(success -> {
            if (success) {
                player.sendMessage(Component.text("Account '" + accountName + "' deleted!", NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("Failed to delete account. Check that you own it.", NamedTextColor.RED));
            }
        });
        
        return true;
    }
    
    /**
     * Handles /cotr info (admin command)
     */
    /**
     * Reloads config.yml and coin.yml from disk (including the resource-pack URL/hash),
     * so config changes take effect without a full server restart.
     */
    private boolean handleReload(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cotr.command.reload")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }

        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        boolean ok = plugin.getConfigManager().reload();
        if (ok) {
            sender.sendMessage(Component.text("Coin of the Realm configuration reloaded.", NamedTextColor.GREEN));
            sender.sendMessage(Component.text("(resource-pack URL/hash re-read; joining players will get the current pack)", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("Reload failed — see the server console for details.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean handleInfo(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission("cotr.command.info")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        BankManager bankManager = getBankManager();
        
        sender.sendMessage(Component.text("=== Coin of the Realm Info ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Banking enabled: " + (bankManager != null && bankManager.isBankingEnabled()), NamedTextColor.GRAY));
        
        if (bankManager != null && bankManager.isBankingEnabled()) {
            int accountCount = bankManager.getAccountCount();
            sender.sendMessage(Component.text("Total accounts: " + accountCount, NamedTextColor.GRAY));
        }
        
        return true;
    }
    
    /**
     * Gets the BankManager instance from the plugin.
     */
    @Nullable
    private BankManager getBankManager() {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        return plugin != null ? plugin.getBankManager() : null;
    }
    
    @Nullable
    private BankExchangeService getExchangeService() {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        return plugin != null ? plugin.getBankExchangeService() : null;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.add("drop");
            completions.add("give");
            completions.add("deposit");
            completions.add("withdraw");
            completions.add("balance");
            completions.add("rate");
            completions.add("request");
            completions.add("account");
            completions.add("info");
            completions.add("reload");
            return filterCompletions(completions, args[0]);
        }
        
        String subcommand = args[0].toLowerCase();
        
        if (subcommand.equals("account") && args.length == 2) {
            completions.add("create");
            completions.add("list");
            completions.add("members");
            completions.add("add");
            completions.add("remove");
            completions.add("delete");
            return filterCompletions(completions, args[1]);
        }
        
        if (subcommand.equals("give") && args.length == 2) {
            for (Player player : sender.getServer().getOnlinePlayers()) {
                completions.add(player.getName());
            }
            return filterCompletions(completions, args[1]);
        }
        
        if ((subcommand.equals("deposit") || subcommand.equals("withdraw") || subcommand.equals("rate")) && args.length == 2) {
            completions.add("emerald");
            return filterCompletions(completions, args[1]);
        }
        
        return completions;
    }
    
    private List<String> filterCompletions(List<String> completions, String input) {
        if (input == null || input.isEmpty()) {
            return completions;
        }
        
        String lowerInput = input.toLowerCase();
        return completions.stream()
                .filter(completion -> completion.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}
