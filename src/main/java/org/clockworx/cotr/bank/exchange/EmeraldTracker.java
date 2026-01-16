package org.clockworx.cotr.bank.exchange;

import org.bukkit.Location;
import org.clockworx.cotr.bank.storage.DatabaseBankStorage;
import org.clockworx.cotr.config.BankConfig;
import org.clockworx.cotr.region.WorldGuardRegionResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * EmeraldTracker - Tracks emerald supply signals per region.
 *
 * This class centralizes all emerald tracking so that:
 * - Event listeners can report deltas in a consistent way
 * - Exchange services can query supply and bank reserve
 * - Region selection is handled in one place
 */
public class EmeraldTracker {
    private final DatabaseBankStorage storage;
    private final WorldGuardRegionResolver regionResolver;
    private final BankConfig.ExchangeConfig exchangeConfig;

    public EmeraldTracker(@NotNull DatabaseBankStorage storage,
                          @NotNull WorldGuardRegionResolver regionResolver,
                          @NotNull BankConfig.ExchangeConfig exchangeConfig) {
        this.storage = storage;
        this.regionResolver = regionResolver;
        this.exchangeConfig = exchangeConfig;
    }

    /**
     * Resolves the region ID for a location using WorldGuard and fallback.
     */
    @NotNull
    public String resolveRegionId(@Nullable Location location) {
        return regionResolver.resolveRegionId(location, exchangeConfig.getFallbackRegion());
    }

    /**
     * Records emeralds mined from ore.
     */
    public void trackMined(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, amount, 0, 0, 0, 0, 0);
    }

    /**
     * Records emeralds found in loot.
     */
    public void trackLoot(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, 0, amount, 0, 0, 0, 0);
    }

    /**
     * Records emeralds dropped by mobs.
     */
    public void trackMobDrops(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, 0, 0, amount, 0, 0, 0);
    }

    /**
     * Records emeralds gained through villager trading.
     */
    public void trackTrading(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, 0, 0, 0, amount, 0, 0);
    }

    /**
     * Records emeralds deposited into the bank.
     */
    public void trackBankIn(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, 0, 0, 0, 0, amount, 0);
    }

    /**
     * Records emeralds withdrawn from the bank.
     */
    public void trackBankOut(@NotNull Location location, int amount) {
        if (amount <= 0) {
            return;
        }
        String regionId = resolveRegionId(location);
        storage.incrementEmeraldStats(regionId, 0, 0, 0, 0, 0, amount);
    }

    /**
     * Gets the current tracked supply for a region.
     * Supply is derived from sources and bank movements:
     * mined + loot + mob + trading + bank_out - bank_in.
     */
    @NotNull
    public CompletableFuture<Integer> getRegionSupply(@NotNull String regionId) {
        return storage.getEmeraldRegionStats(regionId)
            .thenApply(stats -> {
                int supply = stats.getMinedTotal()
                    + stats.getLootTotal()
                    + stats.getMobTotal()
                    + stats.getTradingTotal()
                    + stats.getBankOutTotal()
                    - stats.getBankInTotal();
                return Math.max(0, supply);
            });
    }

    /**
     * Gets the current bank reserve (emeralds deposited minus withdrawn).
     */
    @NotNull
    public CompletableFuture<Integer> getBankReserve(@NotNull String regionId) {
        return storage.getEmeraldRegionStats(regionId)
            .thenApply(stats -> {
                int reserve = stats.getBankInTotal() - stats.getBankOutTotal();
                return Math.max(0, reserve);
            });
    }
}
