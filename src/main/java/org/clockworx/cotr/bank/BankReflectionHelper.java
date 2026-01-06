package org.clockworx.cotr.bank;

import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BankReflectionHelper - Reflection-based wrapper for ServiceIO BankController and Bank
 * 
 * This class uses reflection to call ServiceIO methods without directly importing
 * the classes, allowing the plugin to work even when ServiceIO isn't installed.
 */
public class BankReflectionHelper {
    
    /**
     * Calls createBank on BankController.
     */
    @NotNull
    public static CompletableFuture<Object> createBank(@NotNull Object bankController, 
                                                       @NotNull UUID uuid, 
                                                       @NotNull String name) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("BankReflectionHelper.createBank() - uuid={}, name={}", uuid, name);
        
        try {
            Method method = bankController.getClass().getMethod("createBank", UUID.class, String.class);
            plugin.debug("BankReflectionHelper.createBank() - Method found: {}", method.getName());
            
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> result = (CompletableFuture<Object>) method.invoke(bankController, uuid, name);
            plugin.debug("BankReflectionHelper.createBank() - Method invoked, result: {}", result != null ? "non-null" : "null");
            
            return result != null ? result : CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            plugin.debug("BankReflectionHelper.createBank() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Calls loadBank on BankController.
     */
    @NotNull
    public static CompletableFuture<Object> loadBank(@NotNull Object bankController, @NotNull String name) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("BankReflectionHelper.loadBank() - name={}", name);
        
        try {
            Method method = bankController.getClass().getMethod("loadBank", String.class);
            plugin.debug("BankReflectionHelper.loadBank() - Method found: {}", method.getName());
            
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> result = (CompletableFuture<Object>) method.invoke(bankController, name);
            plugin.debug("BankReflectionHelper.loadBank() - Method invoked, result: {}", result != null ? "non-null" : "null");
            
            return result != null ? result : CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            plugin.debug("BankReflectionHelper.loadBank() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Calls deleteBank on BankController.
     */
    @NotNull
    public static CompletableFuture<Boolean> deleteBank(@NotNull Object bankController, @NotNull Object bank) {
        try {
            Method method = bankController.getClass().getMethod("deleteBank", bank.getClass());
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> result = (CompletableFuture<Boolean>) method.invoke(bankController, bank);
            return result != null ? result : CompletableFuture.completedFuture(false);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Calls getBalance on Bank.
     */
    @Nullable
    public static BigDecimal getBalance(@NotNull Object bank) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("BankReflectionHelper.getBalance() - bank={}", bank.getClass().getSimpleName());
        
        try {
            Method method = bank.getClass().getMethod("getBalance");
            plugin.debug("BankReflectionHelper.getBalance() - Method found: {}", method.getName());
            
            BigDecimal result = (BigDecimal) method.invoke(bank);
            plugin.debug("BankReflectionHelper.getBalance() - Balance: {}", result);
            
            return result;
        } catch (Exception e) {
            plugin.debug("BankReflectionHelper.getBalance() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Calls deposit on Bank.
     * Handles both deposit(BigDecimal) and deposit(Number) method signatures.
     */
    @Nullable
    public static BigDecimal deposit(@NotNull Object bank, @NotNull BigDecimal amount) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("BankReflectionHelper.deposit() - bank={}, amount={}", bank.getClass().getSimpleName(), amount);
        
        try {
            // Try BigDecimal parameter first (for external controllers)
            Method method = null;
            try {
                method = bank.getClass().getMethod("deposit", BigDecimal.class);
            } catch (NoSuchMethodException e) {
                // Try Number parameter (for our CotrBank)
                try {
                    method = bank.getClass().getMethod("deposit", Number.class);
                } catch (NoSuchMethodException e2) {
                    plugin.debug("BankReflectionHelper.deposit() - No deposit method found");
                    return null;
                }
            }
            
            plugin.debug("BankReflectionHelper.deposit() - Method found: {}", method.getName());
            
            BigDecimal result = (BigDecimal) method.invoke(bank, amount);
            plugin.debug("BankReflectionHelper.deposit() - New balance: {}", result);
            
            return result;
        } catch (Exception e) {
            plugin.debug("BankReflectionHelper.deposit() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Calls withdraw on Bank.
     * Handles both withdraw(BigDecimal) and withdraw(Number) method signatures.
     */
    @Nullable
    public static BigDecimal withdraw(@NotNull Object bank, @NotNull BigDecimal amount) {
        CoinOfTheRealmPlugin plugin = CoinOfTheRealmPlugin.getInstance();
        plugin.debug("BankReflectionHelper.withdraw() - bank={}, amount={}", bank.getClass().getSimpleName(), amount);
        
        try {
            // Try BigDecimal parameter first (for external controllers)
            Method method = null;
            try {
                method = bank.getClass().getMethod("withdraw", BigDecimal.class);
            } catch (NoSuchMethodException e) {
                // Try Number parameter (for our CotrBank)
                try {
                    method = bank.getClass().getMethod("withdraw", Number.class);
                } catch (NoSuchMethodException e2) {
                    plugin.debug("BankReflectionHelper.withdraw() - No withdraw method found");
                    return null;
                }
            }
            
            plugin.debug("BankReflectionHelper.withdraw() - Method found: {}", method.getName());
            
            BigDecimal result = (BigDecimal) method.invoke(bank, amount);
            plugin.debug("BankReflectionHelper.withdraw() - New balance: {}", result);
            
            return result;
        } catch (Exception e) {
            plugin.debug("BankReflectionHelper.withdraw() - Exception: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
