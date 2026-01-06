package org.clockworx.cotr.bank;

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
        try {
            Method method = bankController.getClass().getMethod("createBank", UUID.class, String.class);
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> result = (CompletableFuture<Object>) method.invoke(bankController, uuid, name);
            return result != null ? result : CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Calls loadBank on BankController.
     */
    @NotNull
    public static CompletableFuture<Object> loadBank(@NotNull Object bankController, @NotNull String name) {
        try {
            Method method = bankController.getClass().getMethod("loadBank", String.class);
            @SuppressWarnings("unchecked")
            CompletableFuture<Object> result = (CompletableFuture<Object>) method.invoke(bankController, name);
            return result != null ? result : CompletableFuture.completedFuture(null);
        } catch (Exception e) {
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
        try {
            Method method = bank.getClass().getMethod("getBalance");
            return (BigDecimal) method.invoke(bank);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calls deposit on Bank.
     */
    @Nullable
    public static BigDecimal deposit(@NotNull Object bank, @NotNull BigDecimal amount) {
        try {
            Method method = bank.getClass().getMethod("deposit", BigDecimal.class);
            return (BigDecimal) method.invoke(bank, amount);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Calls withdraw on Bank.
     */
    @Nullable
    public static BigDecimal withdraw(@NotNull Object bank, @NotNull BigDecimal amount) {
        try {
            Method method = bank.getClass().getMethod("withdraw", BigDecimal.class);
            return (BigDecimal) method.invoke(bank, amount);
        } catch (Exception e) {
            return null;
        }
    }
}
