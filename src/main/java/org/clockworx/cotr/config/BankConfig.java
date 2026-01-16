package org.clockworx.cotr.config;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * BankConfig - Immutable configuration model for bank features.
 *
 * This class is intentionally separate from ConfigManager so that:
 * - Config parsing stays centralized in ConfigManager
 * - Other systems can safely consume typed, validated values
 * - The configuration intent is documented and stable over time
 */
public class BankConfig {
    private final boolean bankingEnabled;
    private final String defaultAccountPattern;
    private final boolean useOwnController;
    private final String servicePriority;
    private final String bankStorageType;
    private final String databaseType;
    private final String databasePrefix;
    private final String databaseFile;
    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUsername;
    private final String databasePassword;
    private final ExchangeConfig exchangeConfig;

    public BankConfig(boolean bankingEnabled,
                      @NotNull String defaultAccountPattern,
                      boolean useOwnController,
                      @NotNull String servicePriority,
                      @NotNull String bankStorageType,
                      @NotNull String databaseType,
                      @NotNull String databasePrefix,
                      @NotNull String databaseFile,
                      @NotNull String databaseHost,
                      int databasePort,
                      @NotNull String databaseName,
                      @NotNull String databaseUsername,
                      @NotNull String databasePassword,
                      @NotNull ExchangeConfig exchangeConfig) {
        this.bankingEnabled = bankingEnabled;
        this.defaultAccountPattern = defaultAccountPattern;
        this.useOwnController = useOwnController;
        this.servicePriority = servicePriority;
        this.bankStorageType = bankStorageType;
        this.databaseType = databaseType;
        this.databasePrefix = databasePrefix;
        this.databaseFile = databaseFile;
        this.databaseHost = databaseHost;
        this.databasePort = databasePort;
        this.databaseName = databaseName;
        this.databaseUsername = databaseUsername;
        this.databasePassword = databasePassword;
        this.exchangeConfig = exchangeConfig;
    }

    public boolean isBankingEnabled() {
        return bankingEnabled;
    }

    @NotNull
    public String getDefaultAccountPattern() {
        return defaultAccountPattern;
    }

    public boolean isUseOwnController() {
        return useOwnController;
    }

    @NotNull
    public String getServicePriority() {
        return servicePriority;
    }

    @NotNull
    public String getBankStorageType() {
        return bankStorageType;
    }

    @NotNull
    public String getDatabaseType() {
        return databaseType;
    }

    @NotNull
    public String getDatabasePrefix() {
        return databasePrefix;
    }

    @NotNull
    public String getDatabaseFile() {
        return databaseFile;
    }

    @NotNull
    public String getDatabaseHost() {
        return databaseHost;
    }

    public int getDatabasePort() {
        return databasePort;
    }

    @NotNull
    public String getDatabaseName() {
        return databaseName;
    }

    @NotNull
    public String getDatabaseUsername() {
        return databaseUsername;
    }

    @NotNull
    public String getDatabasePassword() {
        return databasePassword;
    }

    @NotNull
    public ExchangeConfig getExchangeConfig() {
        return exchangeConfig;
    }

    /**
     * ExchangeConfig - Settings related to item/currency exchanges.
     */
    public static class ExchangeConfig {
        private final String fallbackRegion;
        private final EmeraldExchangeConfig emeraldExchangeConfig;

        public ExchangeConfig(@NotNull String fallbackRegion,
                              @NotNull EmeraldExchangeConfig emeraldExchangeConfig) {
            this.fallbackRegion = fallbackRegion;
            this.emeraldExchangeConfig = emeraldExchangeConfig;
        }

        @NotNull
        public String getFallbackRegion() {
            return fallbackRegion;
        }

        @NotNull
        public EmeraldExchangeConfig getEmeraldExchangeConfig() {
            return emeraldExchangeConfig;
        }
    }

    /**
     * EmeraldExchangeConfig - Settings for emerald <-> coin exchange rates.
     */
    public static class EmeraldExchangeConfig {
        private final boolean enabled;
        private final int baseRate;
        private final int minRate;
        private final int maxRate;
        private final String mode;
        private final List<Threshold> thresholds;
        private final Formula formula;

        public EmeraldExchangeConfig(boolean enabled,
                                     int baseRate,
                                     int minRate,
                                     int maxRate,
                                     @NotNull String mode,
                                     @NotNull List<Threshold> thresholds,
                                     @NotNull Formula formula) {
            this.enabled = enabled;
            this.baseRate = baseRate;
            this.minRate = minRate;
            this.maxRate = maxRate;
            this.mode = mode;
            this.thresholds = thresholds;
            this.formula = formula;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getBaseRate() {
            return baseRate;
        }

        public int getMinRate() {
            return minRate;
        }

        public int getMaxRate() {
            return maxRate;
        }

        @NotNull
        public String getMode() {
            return mode;
        }

        @NotNull
        public List<Threshold> getThresholds() {
            return thresholds != null ? thresholds : Collections.emptyList();
        }

        @NotNull
        public Formula getFormula() {
            return formula;
        }
    }

    /**
     * Threshold - Configured supply breakpoint for setting an explicit rate.
     */
    public static class Threshold {
        private final int maxEmeralds;
        private final int rate;

        public Threshold(int maxEmeralds, int rate) {
            this.maxEmeralds = maxEmeralds;
            this.rate = rate;
        }

        public int getMaxEmeralds() {
            return maxEmeralds;
        }

        public int getRate() {
            return rate;
        }
    }

    /**
     * Formula - Parameters for a supply-based rate calculation.
     */
    public static class Formula {
        private final boolean enabled;
        private final int divisor;
        private final int step;

        public Formula(boolean enabled, int divisor, int step) {
            this.enabled = enabled;
            this.divisor = divisor;
            this.step = step;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public int getDivisor() {
            return divisor;
        }

        public int getStep() {
            return step;
        }
    }
}
