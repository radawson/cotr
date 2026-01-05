package org.clockworx.cotr.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.entity.CoinEntityManager;
import org.clockworx.cotr.item.CoinItem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CotrCommand - Handles the /cotr command and its subcommands
 * 
 * This command provides two subcommands:
 * - /cotr drop [amount] - Spawns a coin stack at the player's location
 * - /cotr give [user] [amount] - Gives a coin stack to a player's inventory
 * 
 * Uses Paper's native command system which leverages Brigadier under the hood.
 */
public class CotrCommand implements CommandExecutor, TabCompleter {
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Check if any arguments were provided
        if (args.length == 0) {
            sender.sendMessage(Component.text("Usage: /cotr <drop|give> [arguments]", NamedTextColor.RED));
            sender.sendMessage(Component.text("  drop <amount> - Drop coins at your location", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("  give <player> <amount> - Give coins to a player", NamedTextColor.GRAY));
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        // Route to appropriate subcommand handler
        switch (subcommand) {
            case "drop":
                return handleDrop(sender, args);
            case "give":
                return handleGive(sender, args);
            default:
                sender.sendMessage(Component.text("Unknown subcommand: " + subcommand, NamedTextColor.RED));
                sender.sendMessage(Component.text("Usage: /cotr <drop|give> [arguments]", NamedTextColor.YELLOW));
                return true;
        }
    }
    
    /**
     * Handles the /cotr drop [amount] subcommand.
     * Spawns a coin stack at the sender's location.
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was handled successfully
     */
    private boolean handleDrop(@NotNull CommandSender sender, @NotNull String[] args) {
        // Check permission
        if (!sender.hasPermission("cotr.command.drop")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        // Check if sender is a player (required for drop command)
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players.", NamedTextColor.RED));
            return true;
        }
        
        Player player = (Player) sender;
        
        // Validate argument count
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /cotr drop <amount>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.GRAY));
            return true;
        }
        
        // Parse and validate amount
        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount: " + args[1], NamedTextColor.RED));
            sender.sendMessage(Component.text("Amount must be a number between 1 and 64", NamedTextColor.GRAY));
            return true;
        }
        
        // Validate amount range
        if (amount < 1 || amount > 64) {
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.RED));
            return true;
        }
        
        // Create the coin ItemStack
        ItemStack coin;
        try {
            coin = CoinItem.createCoin(amount);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Error creating coin: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }
        
        // Get drop location (slightly in front of player)
        org.bukkit.Location dropLocation = player.getLocation().add(
            player.getLocation().getDirection().multiply(0.5)
        );
        dropLocation.setY(dropLocation.getY() + player.getEyeHeight() - 0.3);
        
        // Create the coin display entity
        org.bukkit.entity.ItemDisplay coinDisplay = CoinEntityManager.createCoinDisplay(dropLocation, coin);
        
        if (coinDisplay != null) {
            // Add a small velocity to make it look natural
            coinDisplay.setVelocity(player.getLocation().getDirection().multiply(0.3));
            sender.sendMessage(Component.text("Dropped " + amount + " coin(s) of the realm!", NamedTextColor.GREEN));
        } else {
            // Fallback: if display creation fails, drop normally
            player.getWorld().dropItemNaturally(dropLocation, coin);
            sender.sendMessage(Component.text("Dropped " + amount + " coin(s) of the realm!", NamedTextColor.GREEN));
        }
        
        return true;
    }
    
    /**
     * Handles the /cotr give [user] [amount] subcommand.
     * Gives a coin stack to the specified player's inventory.
     * 
     * @param sender The command sender
     * @param args The command arguments
     * @return true if the command was handled successfully
     */
    private boolean handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        // Check permission
        if (!sender.hasPermission("cotr.command.give")) {
            sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
            return true;
        }
        
        // Validate argument count
        if (args.length < 3) {
            sender.sendMessage(Component.text("Usage: /cotr give <player> <amount>", NamedTextColor.RED));
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.GRAY));
            return true;
        }
        
        String targetName = args[1];
        
        // Find the target player
        Player target = sender.getServer().getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(Component.text("Player not found: " + targetName, NamedTextColor.RED));
            return true;
        }
        
        // Parse and validate amount
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(Component.text("Invalid amount: " + args[2], NamedTextColor.RED));
            sender.sendMessage(Component.text("Amount must be a number between 1 and 64", NamedTextColor.GRAY));
            return true;
        }
        
        // Validate amount range
        if (amount < 1 || amount > 64) {
            sender.sendMessage(Component.text("Amount must be between 1 and 64", NamedTextColor.RED));
            return true;
        }
        
        // Create the coin ItemStack
        ItemStack coin;
        try {
            coin = CoinItem.createCoin(amount);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text("Error creating coin: " + e.getMessage(), NamedTextColor.RED));
            return true;
        }
        
        // Give the coin to the player
        boolean success = CoinEntityManager.giveCoinToPlayer(target, coin);
        
        if (success) {
            // Notify the sender
            sender.sendMessage(Component.text("Gave " + amount + " coin(s) of the realm to " + target.getName() + "!", NamedTextColor.GREEN));
            
            // Notify the target player (if sender is not the target)
            if (!sender.getName().equals(target.getName())) {
                target.sendMessage(Component.text("You received " + amount + " coin(s) of the realm!", NamedTextColor.GREEN));
            }
        } else {
            sender.sendMessage(Component.text("Failed to give coins to " + target.getName(), NamedTextColor.RED));
        }
        
        return true;
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        // If no arguments, suggest subcommands
        if (args.length == 1) {
            completions.add("drop");
            completions.add("give");
            return filterCompletions(completions, args[0]);
        }
        
        String subcommand = args[0].toLowerCase();
        
        // Tab completion for drop subcommand
        if (subcommand.equals("drop")) {
            if (args.length == 2) {
                // Suggest common amounts
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("16");
                completions.add("32");
                completions.add("64");
                return filterCompletions(completions, args[1]);
            }
        }
        
        // Tab completion for give subcommand
        if (subcommand.equals("give")) {
            if (args.length == 2) {
                // Suggest online player names
                for (Player player : sender.getServer().getOnlinePlayers()) {
                    completions.add(player.getName());
                }
                return filterCompletions(completions, args[1]);
            } else if (args.length == 3) {
                // Suggest common amounts
                completions.add("1");
                completions.add("5");
                completions.add("10");
                completions.add("16");
                completions.add("32");
                completions.add("64");
                return filterCompletions(completions, args[2]);
            }
        }
        
        return completions;
    }
    
    /**
     * Filters completion suggestions based on the current input.
     * 
     * @param completions The list of possible completions
     * @param input The current input string
     * @return Filtered list of completions that start with the input
     */
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
