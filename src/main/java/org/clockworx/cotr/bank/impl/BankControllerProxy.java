package org.clockworx.cotr.bank.impl;

import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * BankControllerProxy - Creates a dynamic proxy that implements BankController at runtime.
 * 
 * This allows CotrBankController to work without requiring the BankController interface
 * at compile time, making ServiceIO a truly optional dependency.
 * 
 * The proxy delegates all method calls to the underlying CotrBankController instance
 * using reflection.
 */
public class BankControllerProxy {
    
    /**
     * Creates a dynamic proxy that implements BankController and delegates to the given controller.
     * 
     * @param plugin The plugin instance
     * @param controller The CotrBankController instance to delegate to
     * @return A proxy object that implements BankController, or null if BankController class is not available
     */
    @NotNull
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
                new BankControllerInvocationHandler(plugin, controller)
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
     */
    private static class BankControllerInvocationHandler implements InvocationHandler {
        private final CoinOfTheRealmPlugin plugin;
        private final CotrBankController controller;
        
        public BankControllerInvocationHandler(@NotNull CoinOfTheRealmPlugin plugin, @NotNull CotrBankController controller) {
            this.plugin = plugin;
            this.controller = controller;
        }
        
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            plugin.debug("BankControllerProxy.invoke() - Method: {}, Args: {}", method.getName(), args != null ? args.length : 0);
            
            // Get the corresponding method from CotrBankController
            Method controllerMethod = findMethod(controller.getClass(), method, args);
            
            if (controllerMethod != null) {
                // Invoke the method on the controller
                Object result = controllerMethod.invoke(controller, args);
                plugin.debug("BankControllerProxy.invoke() - Method invoked, result: {}", result != null ? result.getClass().getSimpleName() : "null");
                return result;
            } else {
                plugin.debug("BankControllerProxy.invoke() - Method not found in CotrBankController: {}", method.getName());
                throw new NoSuchMethodException("Method " + method.getName() + " not found in CotrBankController");
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
                            if (args != null && i < args.length) {
                                // Check if the argument is assignable to the parameter type
                                if (!mParamTypes[i].isAssignableFrom(args[i].getClass())) {
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
    }
}
