package org.clockworx.cotr.bank.impl;

import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BankControllerProxy - Creates a dynamic proxy that implements BankController at runtime.
 * 
 * This allows CotrBankController to work without requiring the BankController interface
 * at compile time, making ServiceIO a truly optional dependency.
 * 
 * The proxy delegates all method calls to the underlying CotrBankController instance
 * using reflection. Default interface methods (like OfflinePlayer overloads) are
 * handled by invoking the default implementation which delegates to our UUID-based methods.
 */
public class BankControllerProxy {
    
    /**
     * Creates a dynamic proxy that implements BankController and delegates to the given controller.
     * 
     * @param plugin The plugin instance
     * @param controller The CotrBankController instance to delegate to
     * @return A proxy object that implements BankController, or null if BankController class is not available
     */
    @Nullable
    public static Object createProxy(@NotNull CoinOfTheRealmPlugin plugin, @NotNull CotrBankController controller) {
        plugin.debug("BankControllerProxy.createProxy() - Creating dynamic proxy for BankController");
        
        try {
            // Load the BankController interface class
            Class<?> bankControllerClass = Class.forName("net.thenextlvl.service.api.economy.bank.BankController");
            plugin.debug("BankControllerProxy.createProxy() - BankController class loaded: {}", bankControllerClass.getName());
            
            // Also try to load Controller interface if it exists
            Class<?> controllerClass = null;
            try {
                controllerClass = Class.forName("net.thenextlvl.service.api.Controller");
                plugin.debug("BankControllerProxy.createProxy() - Controller class loaded: {}", controllerClass.getName());
            } catch (ClassNotFoundException e) {
                plugin.debug("BankControllerProxy.createProxy() - Controller interface not found, using only BankController");
            }
            
            // Create proxy with both interfaces if Controller is available
            Class<?>[] interfaces = controllerClass != null 
                ? new Class<?>[]{bankControllerClass, controllerClass}
                : new Class<?>[]{bankControllerClass};
            
            // Create the proxy
            Object proxy = Proxy.newProxyInstance(
                bankControllerClass.getClassLoader(),
                interfaces,
                new BankControllerInvocationHandler(plugin, controller, bankControllerClass)
            );
            
            plugin.debug("BankControllerProxy.createProxy() - Proxy created successfully");
            return proxy;
            
        } catch (ClassNotFoundException e) {
            plugin.debug("BankControllerProxy.createProxy() - BankController class not found: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.debug("BankControllerProxy.createProxy() - Exception creating proxy: {} - {}", 
                e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Invocation handler that delegates method calls to CotrBankController.
     * Handles both direct method delegation and default interface method invocation.
     */
    private static class BankControllerInvocationHandler implements InvocationHandler {
        private final CoinOfTheRealmPlugin plugin;
        private final CotrBankController controller;
        private final Class<?> bankControllerClass;
        
        public BankControllerInvocationHandler(@NotNull CoinOfTheRealmPlugin plugin, 
                                               @NotNull CotrBankController controller,
                                               @NotNull Class<?> bankControllerClass) {
            this.plugin = plugin;
            this.controller = controller;
            this.bankControllerClass = bankControllerClass;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            plugin.debug("BankControllerProxy.invoke() - Method: {}, Args: {}", methodName, args != null ? args.length : 0);
            
            // Handle Object methods
            if (methodName.equals("toString")) {
                return "BankControllerProxy[" + controller.getName() + "]";
            }
            if (methodName.equals("hashCode")) {
                return controller.hashCode();
            }
            if (methodName.equals("equals")) {
                return proxy == args[0];
            }
            
            // Try to find and invoke method on our controller first
            Method controllerMethod = findMethod(controller.getClass(), method, args);
            
            if (controllerMethod != null) {
                // Invoke the method on the controller
                Object result = controllerMethod.invoke(controller, args);
                plugin.debug("BankControllerProxy.invoke() - Method invoked on controller, result: {}", 
                    result != null ? result.getClass().getSimpleName() : "null");
                return result;
            }
            
            // Method not found in controller - check if it's a default method
            if (method.isDefault()) {
                plugin.debug("BankControllerProxy.invoke() - Invoking default method: {}", methodName);
                return invokeDefaultMethod(proxy, method, args);
            }
            
            // Handle OfflinePlayer-based methods by converting to UUID
            if (args != null && args.length > 0 && args[0] instanceof OfflinePlayer) {
                return handleOfflinePlayerMethod(proxy, method, args);
            }
            
            plugin.debug("BankControllerProxy.invoke() - Method not found in CotrBankController: {}", methodName);
            throw new NoSuchMethodException("Method " + methodName + " not found in CotrBankController");
        }
        
        /**
         * Invokes a default interface method using MethodHandles.
         */
        private Object invokeDefaultMethod(Object proxy, Method method, Object[] args) throws Throwable {
            try {
                // For Java 9+, use MethodHandles.lookup().findSpecial()
                return MethodHandles.lookup()
                    .findSpecial(
                        method.getDeclaringClass(),
                        method.getName(),
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                        method.getDeclaringClass()
                    )
                    .bindTo(proxy)
                    .invokeWithArguments(args);
            } catch (IllegalAccessException e) {
                // Fallback for older Java versions or access issues
                plugin.debug("BankControllerProxy.invokeDefaultMethod() - IllegalAccessException, trying alternative: {}", e.getMessage());
                
                // Try using privateLookupIn for better access
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(method.getDeclaringClass(), MethodHandles.lookup());
                return lookup.findSpecial(
                        method.getDeclaringClass(),
                        method.getName(),
                        MethodType.methodType(method.getReturnType(), method.getParameterTypes()),
                        method.getDeclaringClass()
                    )
                    .bindTo(proxy)
                    .invokeWithArguments(args);
            }
        }
        
        /**
         * Handles methods that take OfflinePlayer by converting to UUID and delegating.
         */
        private Object handleOfflinePlayerMethod(Object proxy, Method method, Object[] args) throws Throwable {
            String methodName = method.getName();
            OfflinePlayer player = (OfflinePlayer) args[0];
            UUID uuid = player.getUniqueId();
            
            plugin.debug("BankControllerProxy.handleOfflinePlayerMethod() - Converting OfflinePlayer to UUID for method: {}", methodName);
            
            // Convert args: replace OfflinePlayer with UUID
            Object[] newArgs = new Object[args.length];
            newArgs[0] = uuid;
            for (int i = 1; i < args.length; i++) {
                newArgs[i] = args[i];
            }
            
            // Find the UUID-based method
            Class<?>[] newParamTypes = new Class<?>[method.getParameterTypes().length];
            newParamTypes[0] = UUID.class;
            for (int i = 1; i < method.getParameterTypes().length; i++) {
                newParamTypes[i] = method.getParameterTypes()[i];
            }
            
            try {
                Method uuidMethod = controller.getClass().getMethod(methodName, newParamTypes);
                return uuidMethod.invoke(controller, newArgs);
            } catch (NoSuchMethodException e) {
                // Try invoking the default method instead
                if (method.isDefault()) {
                    return invokeDefaultMethod(proxy, method, args);
                }
                throw e;
            }
        }
        
        /**
         * Finds the corresponding method in CotrBankController that matches the interface method.
         */
        private Method findMethod(Class<?> clazz, Method interfaceMethod, Object[] args) {
            String methodName = interfaceMethod.getName();
            Class<?>[] paramTypes = interfaceMethod.getParameterTypes();
            
            try {
                // Try exact match first
                return clazz.getMethod(methodName, paramTypes);
            } catch (NoSuchMethodException e) {
                // Try to find by name and parameter count (for overloaded methods)
                for (Method m : clazz.getMethods()) {
                    if (m.getName().equals(methodName) && m.getParameterCount() == paramTypes.length) {
                        // Check if parameter types are compatible
                        Class<?>[] mParamTypes = m.getParameterTypes();
                        boolean compatible = true;
                        for (int i = 0; i < mParamTypes.length; i++) {
                            if (args != null && i < args.length && args[i] != null) {
                                // Check if the argument is assignable to the parameter type
                                if (!isAssignable(mParamTypes[i], args[i].getClass())) {
                                    compatible = false;
                                    break;
                                }
                            }
                        }
                        if (compatible) {
                            return m;
                        }
                    }
                }
                return null;
            }
        }
        
        /**
         * Checks if a value of sourceClass can be assigned to a parameter of targetClass.
         */
        private boolean isAssignable(Class<?> targetClass, Class<?> sourceClass) {
            if (targetClass.isAssignableFrom(sourceClass)) {
                return true;
            }
            // Handle primitive wrappers
            if (targetClass.isPrimitive()) {
                if (targetClass == int.class && sourceClass == Integer.class) return true;
                if (targetClass == long.class && sourceClass == Long.class) return true;
                if (targetClass == double.class && sourceClass == Double.class) return true;
                if (targetClass == float.class && sourceClass == Float.class) return true;
                if (targetClass == boolean.class && sourceClass == Boolean.class) return true;
            }
            return false;
        }
    }
}
