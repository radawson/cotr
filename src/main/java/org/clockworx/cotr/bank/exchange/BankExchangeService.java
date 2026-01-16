package org.clockworx.cotr.bank.exchange;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.clockworx.cotr.bank.BankManager;
import org.clockworx.cotr.bank.storage.DatabaseBankStorage;
import org.clockworx.cotr.config.BankConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * BankExchangeService - Handles emerald <-> coin exchanges via bank balances.
 *
 * This service is responsible for:
 * - Resolving region-based exchange rates
 * - Performing bank balance adjustments
 * - Updating emerald tracking statistics
 * - Returning structured results for command messaging
 */
public class BankExchangeService {
    private final BankManager bankManager;
    private final DatabaseBankStorage storage;
    private final EmeraldTracker emeraldTracker;
    private final BankConfig.EmeraldExchangeConfig exchangeConfig;

    public BankExchangeService(@NotNull BankManager bankManager,
                               @NotNull DatabaseBankStorage storage,
                               @NotNull EmeraldTracker emeraldTracker,
                               @NotNull BankConfig.EmeraldExchangeConfig exchangeConfig) {
        this.bankManager = bankManager;
        this.storage = storage;
        this.emeraldTracker = emeraldTracker;
        this.exchangeConfig = exchangeConfig;
    }

    /**
     * Gets the current emerald exchange rate for a player's region.
     */
    @NotNull
    public CompletableFuture<RateQuote> getRateQuote(@NotNull Player player) {
        String regionId = emeraldTracker.resolveRegionId(player.getLocation());
        return emeraldTracker.getRegionSupply(regionId)
            .thenCompose(supply -> emeraldTracker.getBankReserve(regionId)
                .thenCompose(reserve -> {
                    int rate = calculateRate(supply);
                    storage.upsertEmeraldExchangeRate(regionId, rate);
                    return CompletableFuture.completedFuture(new RateQuote(regionId, rate, supply, reserve));
                }));
    }

    /**
     * Deposits emeralds into the bank and credits coins at the current rate.
     */
    @NotNull
    public CompletableFuture<ExchangeResult> depositEmerald(@NotNull Player player,
                                                             @Nullable String accountName,
                                                             int emeraldAmount) {
        if (!exchangeConfig.isEnabled() || !bankManager.isBankingEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (emeraldAmount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        
        String regionId = emeraldTracker.resolveRegionId(player.getLocation());
        return emeraldTracker.getRegionSupply(regionId)
            .thenCompose(supply -> {
                int rate = calculateRate(supply);
                storage.upsertEmeraldExchangeRate(regionId, rate);
                
                long coinAmountLong = (long) emeraldAmount * (long) rate;
                if (coinAmountLong > Integer.MAX_VALUE) {
                    return CompletableFuture.completedFuture(null);
                }
                int coinAmount = (int) coinAmountLong;
                
                int emeraldsInInventory = countEmeraldsInInventory(player);
                if (emeraldsInInventory < emeraldAmount) {
                    return CompletableFuture.completedFuture(null);
                }
                
                removeEmeraldsFromInventory(player, emeraldAmount);
                
                return bankManager.depositBalance(player, accountName, coinAmount)
                    .thenCompose(success -> {
                        if (!success) {
                            giveEmeraldsToPlayer(player, emeraldAmount);
                            return CompletableFuture.completedFuture(null);
                        }
                        
                        emeraldTracker.trackBankIn(player.getLocation(), emeraldAmount);
                        CompletableFuture<String> accountFuture = accountName != null ?
                            CompletableFuture.completedFuture(accountName) :
                            bankManager.getDefaultAccount(player);
                        return accountFuture.thenCompose(accName -> bankManager.getBalance(player, accName)
                            .thenApply(balance -> new ExchangeResult(regionId, accName, rate, emeraldAmount, coinAmount, balance != null ? balance : 0)));
                    });
            });
    }

    /**
     * Withdraws emeralds from the bank by debiting coins at the current rate.
     */
    @NotNull
    public CompletableFuture<ExchangeResult> withdrawEmerald(@NotNull Player player,
                                                              @Nullable String accountName,
                                                              int emeraldAmount) {
        if (!exchangeConfig.isEnabled() || !bankManager.isBankingEnabled()) {
            return CompletableFuture.completedFuture(null);
        }
        if (emeraldAmount <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        
        String regionId = emeraldTracker.resolveRegionId(player.getLocation());
        return emeraldTracker.getRegionSupply(regionId)
            .thenCompose(supply -> emeraldTracker.getBankReserve(regionId)
                .thenCompose(reserve -> {
                    int rate = calculateRate(supply);
                    storage.upsertEmeraldExchangeRate(regionId, rate);
                    
                    if (reserve < emeraldAmount) {
                        return CompletableFuture.completedFuture(null);
                    }
                    
                    long coinCostLong = (long) emeraldAmount * (long) rate;
                    if (coinCostLong > Integer.MAX_VALUE) {
                        return CompletableFuture.completedFuture(null);
                    }
                    int coinCost = (int) coinCostLong;
                    
                    return bankManager.withdrawBalance(player, accountName, coinCost)
                        .thenCompose(success -> {
                            if (!success) {
                                return CompletableFuture.completedFuture(null);
                            }
                            
                            giveEmeraldsToPlayer(player, emeraldAmount);
                            emeraldTracker.trackBankOut(player.getLocation(), emeraldAmount);
                            
                            CompletableFuture<String> accountFuture = accountName != null ?
                                CompletableFuture.completedFuture(accountName) :
                                bankManager.getDefaultAccount(player);
                            return accountFuture.thenCompose(accName -> bankManager.getBalance(player, accName)
                                .thenApply(balance -> new ExchangeResult(regionId, accName, rate, emeraldAmount, coinCost, balance != null ? balance : 0)));
                        });
                }));
    }

    /**
     * Computes the exchange rate based on the configured mode and supply.
     */
    private int calculateRate(int supply) {
        int rate = exchangeConfig.getBaseRate();
        String mode = exchangeConfig.getMode().toLowerCase();
        
        boolean thresholdApplied = false;
        List<BankConfig.Threshold> thresholds = exchangeConfig.getThresholds();
        if ((mode.equals("thresholds") || mode.equals("hybrid")) && !thresholds.isEmpty()) {
            for (BankConfig.Threshold threshold : thresholds) {
                if (supply <= threshold.getMaxEmeralds()) {
                    rate = threshold.getRate();
                    thresholdApplied = true;
                    break;
                }
            }
        }
        
        BankConfig.Formula formula = exchangeConfig.getFormula();
        if (!thresholdApplied && (mode.equals("formula") || mode.equals("hybrid")) && formula.isEnabled()) {
            int steps = supply / Math.max(1, formula.getDivisor());
            rate = exchangeConfig.getBaseRate() + (steps * formula.getStep());
        }
        
        rate = Math.max(exchangeConfig.getMinRate(), Math.min(rate, exchangeConfig.getMaxRate()));
        return rate;
    }

    private int countEmeraldsInInventory(@NotNull Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.EMERALD) {
                total += item.getAmount();
            }
        }
        return total;
    }

    private void removeEmeraldsFromInventory(@NotNull Player player, int amount) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.EMERALD) {
                int itemAmount = item.getAmount();
                if (itemAmount <= amount) {
                    player.getInventory().removeItem(item);
                    amount -= itemAmount;
                } else {
                    item.setAmount(itemAmount - amount);
                    amount = 0;
                }
                if (amount <= 0) {
                    break;
                }
            }
        }
    }

    private void giveEmeraldsToPlayer(@NotNull Player player, int amount) {
        while (amount > 0) {
            int stackSize = Math.min(amount, 64);
            ItemStack emeralds = new ItemStack(Material.EMERALD, stackSize);
            
            HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(emeralds);
            if (!overflow.isEmpty()) {
                for (ItemStack item : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
            }
            
            amount -= stackSize;
        }
    }

    /**
     * Immutable rate quote for display purposes.
     */
    public static class RateQuote {
        private final String regionId;
        private final int rate;
        private final int supply;
        private final int bankReserve;

        public RateQuote(@NotNull String regionId, int rate, int supply, int bankReserve) {
            this.regionId = regionId;
            this.rate = rate;
            this.supply = supply;
            this.bankReserve = bankReserve;
        }

        @NotNull
        public String getRegionId() {
            return regionId;
        }

        public int getRate() {
            return rate;
        }

        public int getSupply() {
            return supply;
        }

        public int getBankReserve() {
            return bankReserve;
        }
    }

    /**
     * Immutable exchange result for command responses.
     */
    public static class ExchangeResult {
        private final String regionId;
        private final String accountName;
        private final int rate;
        private final int emeraldAmount;
        private final int coinAmount;
        private final int newBalance;

        public ExchangeResult(@NotNull String regionId,
                              @NotNull String accountName,
                              int rate,
                              int emeraldAmount,
                              int coinAmount,
                              int newBalance) {
            this.regionId = regionId;
            this.accountName = accountName;
            this.rate = rate;
            this.emeraldAmount = emeraldAmount;
            this.coinAmount = coinAmount;
            this.newBalance = newBalance;
        }

        @NotNull
        public String getRegionId() {
            return regionId;
        }

        @NotNull
        public String getAccountName() {
            return accountName;
        }

        public int getRate() {
            return rate;
        }

        public int getEmeraldAmount() {
            return emeraldAmount;
        }

        public int getCoinAmount() {
            return coinAmount;
        }

        public int getNewBalance() {
            return newBalance;
        }
    }
}
