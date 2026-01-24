package org.clockworx.cotr.region;

import org.bukkit.Location;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * WorldGuardRegionResolver - Resolves the highest-priority WorldGuard region for a location.
 *
 * This helper keeps WorldGuard usage contained and optional:
 * - If WorldGuard is not installed or not enabled, it always returns the fallback region.
 * - If no regions apply to a location, it returns the fallback region.
 */
public class WorldGuardRegionResolver {
    private final CoinOfTheRealmPlugin plugin;
    private final boolean worldGuardAvailable;
    private boolean reflectionReady;
    private Method worldGuardGetInstance;
    private Method worldGuardGetPlatform;
    private Method platformGetRegionContainer;
    private Method regionContainerCreateQuery;
    private Method regionQueryGetApplicableRegions;
    private Method bukkitAdapterAdapt;
    private Method applicableGetRegions;
    private Method regionGetPriority;
    private Method regionGetId;

    public WorldGuardRegionResolver(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
        this.worldGuardAvailable = plugin.getServer().getPluginManager().getPlugin("WorldGuard") != null;
        if (!worldGuardAvailable) {
            plugin.getLogger().info("WorldGuard not detected. Using fallback regions for exchange tracking.");
        } else {
            initializeReflection();
        }
    }

    /**
     * Resolves the highest-priority WorldGuard region for the location.
     * If multiple regions have the same priority, the lowest alphabetical ID wins.
     *
     * @param location The location to resolve
     * @param fallbackRegion The fallback region ID when none are found
     * @return The resolved region ID
     */
    @NotNull
    public String resolveRegionId(@Nullable Location location, @NotNull String fallbackRegion) {
        if (!worldGuardAvailable || !reflectionReady || location == null) {
            return fallbackRegion;
        }
        
        try {
            Object worldGuard = worldGuardGetInstance.invoke(null);
            Object platform = worldGuardGetPlatform.invoke(worldGuard);
            Object regionContainer = platformGetRegionContainer.invoke(platform);
            Object regionQuery = regionContainerCreateQuery.invoke(regionContainer);
            
            // Invoke adapt method - handle different signatures
            Object wgLocation;
            try {
                if (bukkitAdapterAdapt.getParameterCount() == 1) {
                    wgLocation = bukkitAdapterAdapt.invoke(null, location);
                } else {
                    // Might need World parameter
                    wgLocation = bukkitAdapterAdapt.invoke(null, location, location.getWorld());
                }
            } catch (Exception e) {
                plugin.debug("Failed to adapt location: " + e.getMessage());
                return fallbackRegion;
            }
            
            // Ensure the location type matches what getApplicableRegions expects
            // If the types don't match, try to find a conversion method
            Class<?> expectedParamType = regionQueryGetApplicableRegions.getParameterTypes()[0];
            if (!expectedParamType.isInstance(wgLocation)) {
                plugin.debug("Location type mismatch: got " + wgLocation.getClass().getName() + ", expected " + expectedParamType.getName());
                // Try to find a conversion method
                try {
                    // Check if there's a method to convert between types
                    Method convertMethod = wgLocation.getClass().getMethod("to" + expectedParamType.getSimpleName());
                    wgLocation = convertMethod.invoke(wgLocation);
                } catch (Exception e) {
                    plugin.debug("Could not convert location type: " + e.getMessage());
                    return fallbackRegion;
                }
            }
            
            Object applicable = regionQueryGetApplicableRegions.invoke(regionQuery, wgLocation);
            @SuppressWarnings("unchecked")
            Set<Object> regions = (Set<Object>) applicableGetRegions.invoke(applicable);
            
            if (regions == null || regions.isEmpty()) {
                return fallbackRegion;
            }
            
            Object best = null;
            for (Object region : regions) {
                if (best == null) {
                    best = region;
                    continue;
                }
                int regionPriority = (int) regionGetPriority.invoke(region);
                int bestPriority = (int) regionGetPriority.invoke(best);
                if (regionPriority > bestPriority) {
                    best = region;
                } else if (regionPriority == bestPriority) {
                    String regionId = (String) regionGetId.invoke(region);
                    String bestId = (String) regionGetId.invoke(best);
                    if (regionId.compareToIgnoreCase(bestId) < 0) {
                        best = region;
                    }
                }
            }
            
            return best != null ? (String) regionGetId.invoke(best) : fallbackRegion;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to resolve WorldGuard region: " + e.getMessage());
            return fallbackRegion;
        }
    }
    
    /**
     * Initializes reflection handles for WorldGuard classes.
     * This allows optional use without hard dependencies at runtime.
     */
    private void initializeReflection() {
        try {
            // Get WorldGuard plugin to use its classloader
            org.bukkit.plugin.Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
            if (wgPlugin == null) {
                plugin.getLogger().warning("WorldGuard plugin not found despite earlier detection");
                reflectionReady = false;
                return;
            }
            
            // Log WorldGuard version for debugging
            @SuppressWarnings("deprecation")
            String wgVersion = wgPlugin.getDescription().getVersion();
            plugin.debug("WorldGuard version detected: " + wgVersion);
            
            ClassLoader wgClassLoader = wgPlugin.getClass().getClassLoader();
            
            // Load WorldGuard core class
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, wgClassLoader);
            
            // Try to find BukkitAdapter - it may be in different locations in different versions
            Class<?> bukkitAdapterClass = null;
            ClassLoader adapterClassLoader = wgClassLoader;
            String[] possibleAdapterPaths = {
                "com.sk89q.worldguard.bukkit.BukkitAdapter",
                "com.sk89q.worldguard.bukkit.BukkitWorldGuardPlatform",
                "com.sk89q.worldguard.platform.bukkit.BukkitAdapter"
            };
            
            for (String adapterPath : possibleAdapterPaths) {
                try {
                    bukkitAdapterClass = Class.forName(adapterPath, true, wgClassLoader);
                    plugin.debug("Found BukkitAdapter at: " + adapterPath);
                    break;
                } catch (ClassNotFoundException e) {
                    plugin.debug("BukkitAdapter not found at: " + adapterPath);
                }
            }
            
            if (bukkitAdapterClass == null) {
                // Try alternative approach: use WorldEdit's BukkitAdapter or direct location conversion
                // In WorldGuard 7.x, we might need to use WorldEdit's location classes directly
                try {
                    // Prefer WorldEdit plugin classloader if available
                    org.bukkit.plugin.Plugin wePlugin = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
                    if (wePlugin != null) {
                        adapterClassLoader = wePlugin.getClass().getClassLoader();
                        plugin.debug("WorldEdit detected; using its classloader for adapter");
                    }
                    Class<?> weBukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, adapterClassLoader);
                    bukkitAdapterClass = weBukkitAdapter;
                    plugin.debug("Using WorldEdit BukkitAdapter instead");
                } catch (ClassNotFoundException e) {
                    plugin.getLogger().warning("Could not find BukkitAdapter in any expected location. WorldGuard integration may not work.");
                    plugin.debug("Tried paths: " + String.join(", ", possibleAdapterPaths) + ", and WorldEdit adapter");
                    reflectionReady = false;
                    return;
                }
            }
            
            worldGuardGetInstance = worldGuardClass.getMethod("getInstance");
            worldGuardGetPlatform = worldGuardClass.getMethod("getPlatform");
            platformGetRegionContainer = worldGuardGetPlatform.getReturnType().getMethod("getRegionContainer");
            regionContainerCreateQuery = platformGetRegionContainer.getReturnType().getMethod("createQuery");
            
            // Try to find the method to convert Location to WorldEdit location
            // In WorldEdit/WorldGuard 7.x, this might be asBlockVector, toVector, or adapt
            String[] possibleMethodNames = {"adapt", "asBlockVector", "asVector", "toVector", "toBlockVector"};
            Class<?>[][] possibleSignatures = new Class<?>[][]{
                new Class<?>[]{Location.class},
                new Class<?>[]{Location.class, org.bukkit.World.class},
                new Class<?>[]{org.bukkit.World.class, Location.class}
            };
            
            bukkitAdapterAdapt = null;
            for (String methodName : possibleMethodNames) {
                for (Class<?>[] signature : possibleSignatures) {
                    try {
                        bukkitAdapterAdapt = bukkitAdapterClass.getMethod(methodName, signature);
                        plugin.debug("Found location conversion method: " + methodName + " with " + signature.length + " parameter(s)");
                        break;
                    } catch (NoSuchMethodException e) {
                        // Try next combination
                    }
                }
                if (bukkitAdapterAdapt != null) {
                    break;
                }
            }
            
            if (bukkitAdapterAdapt == null) {
                // Last resort: try to find any method that takes Location
                java.lang.reflect.Method[] methods = bukkitAdapterClass.getMethods();
                for (java.lang.reflect.Method method : methods) {
                    if (method.getParameterCount() == 1 || method.getParameterCount() == 2) {
                        Class<?>[] params = method.getParameterTypes();
                        boolean hasLocation = false;
                        for (Class<?> param : params) {
                            if (param == Location.class || param.isAssignableFrom(Location.class)) {
                                hasLocation = true;
                                break;
                            }
                        }
                        if (hasLocation && method.getReturnType() != void.class) {
                            bukkitAdapterAdapt = method;
                            plugin.debug("Found location conversion method via search: " + method.getName());
                            break;
                        }
                    }
                }
            }
            
            if (bukkitAdapterAdapt == null) {
                StringBuilder methodDump = new StringBuilder();
                for (java.lang.reflect.Method method : bukkitAdapterClass.getMethods()) {
                    methodDump.append(method.getName())
                        .append("(");
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        methodDump.append(params[i].getSimpleName());
                        if (i < params.length - 1) {
                            methodDump.append(", ");
                        }
                    }
                    methodDump.append(") -> ").append(method.getReturnType().getSimpleName()).append("; ");
                }
                plugin.debug("BukkitAdapter method list: " + methodDump);
                throw new NoSuchMethodException("Could not find any method in BukkitAdapter to convert Location. Tried methods: " + String.join(", ", possibleMethodNames));
            }
            
            Class<?> regionQueryClass = regionContainerCreateQuery.getReturnType();
            Class<?> wgLocationClass = bukkitAdapterAdapt.getReturnType();
            
            plugin.debug("RegionQuery class: " + regionQueryClass.getName());
            plugin.debug("Location class from adapt: " + wgLocationClass.getName());
            
            // First, get ALL getApplicableRegions methods to see what's available
            java.util.List<java.lang.reflect.Method> allApplicableMethods = new java.util.ArrayList<>();
            for (java.lang.reflect.Method method : regionQueryClass.getMethods()) {
                if (method.getName().equals("getApplicableRegions")) {
                    allApplicableMethods.add(method);
                }
            }
            
            if (allApplicableMethods.isEmpty()) {
                // Also check declared methods (might be in superclass)
                for (java.lang.reflect.Method method : regionQueryClass.getDeclaredMethods()) {
                    if (method.getName().equals("getApplicableRegions")) {
                        allApplicableMethods.add(method);
                    }
                }
            }
            
            // Log all available methods for debugging
            if (!allApplicableMethods.isEmpty()) {
                StringBuilder methodList = new StringBuilder();
                for (java.lang.reflect.Method method : allApplicableMethods) {
                    methodList.append(method.getName()).append("(");
                    Class<?>[] params = method.getParameterTypes();
                    for (int i = 0; i < params.length; i++) {
                        methodList.append(params[i].getSimpleName());
                        if (i < params.length - 1) methodList.append(", ");
                    }
                    methodList.append("); ");
                }
                plugin.debug("All getApplicableRegions methods found: " + methodList);
            }
            
            // Try to find getApplicableRegions method - it may have different signatures in different versions
            // WorldGuard 7.x uses: getApplicableRegions(Location)
            // Some versions might have: getApplicableRegions(BlockVector3) or other variants
            regionQueryGetApplicableRegions = null;
            
            // Strategy 1: Try the location class we got from adapt
            try {
                regionQueryGetApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", wgLocationClass);
                plugin.debug("Found getApplicableRegions with location class: " + wgLocationClass.getName());
            } catch (NoSuchMethodException e) {
                plugin.debug("getApplicableRegions not found with " + wgLocationClass.getName() + ", trying alternatives");
                
                // Strategy 2: Try common WorldEdit location types
                String[] possibleLocationClasses = {
                    "com.sk89q.worldedit.util.Location",
                    "com.sk89q.worldedit.math.BlockVector3",
                    "com.sk89q.worldedit.Vector",
                    "com.sk89q.worldedit.math.Vector3"
                };
                
                for (String locationClassName : possibleLocationClasses) {
                    try {
                        Class<?> altLocationClass = Class.forName(locationClassName, true, wgClassLoader);
                        regionQueryGetApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", altLocationClass);
                        plugin.debug("Found getApplicableRegions with alternative location class: " + locationClassName);
                        // Update wgLocationClass to match what we found
                        wgLocationClass = altLocationClass;
                        break;
                    } catch (ClassNotFoundException | NoSuchMethodException ex) {
                        // Try next class
                    }
                }
                
                // Strategy 3: If we found methods above, try each one
                if (regionQueryGetApplicableRegions == null && !allApplicableMethods.isEmpty()) {
                    for (java.lang.reflect.Method method : allApplicableMethods) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 1) {
                            Class<?> paramType = paramTypes[0];
                            // Check if our location class is compatible
                            if (paramType.isAssignableFrom(wgLocationClass) || 
                                wgLocationClass.isAssignableFrom(paramType) ||
                                paramType.getName().contains("Location") ||
                                paramType.getName().contains("Vector")) {
                                try {
                                    // Test if we can use this method
                                    regionQueryGetApplicableRegions = method;
                                    plugin.debug("Selected getApplicableRegions(" + paramType.getSimpleName() + ") from available methods");
                                    wgLocationClass = paramType; // Update to expected type
                                    break;
                                } catch (Exception ex2) {
                                    plugin.debug("Could not use method with " + paramType.getSimpleName() + ": " + ex2.getMessage());
                                }
                            }
                        }
                    }
                }
            }
            
            if (regionQueryGetApplicableRegions == null) {
                // Last resort: list all getApplicableRegions methods to help debug
                StringBuilder methodDump = new StringBuilder();
                java.util.List<java.lang.reflect.Method> applicableMethods = new java.util.ArrayList<>();
                for (java.lang.reflect.Method method : regionQueryClass.getMethods()) {
                    if (method.getName().equals("getApplicableRegions")) {
                        applicableMethods.add(method);
                        methodDump.append(method.getName())
                            .append("(");
                        Class<?>[] params = method.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            methodDump.append(params[i].getSimpleName());
                            if (i < params.length - 1) {
                                methodDump.append(", ");
                            }
                        }
                        methodDump.append("); ");
                    }
                }
                
                if (methodDump.length() > 0) {
                    plugin.debug("Available getApplicableRegions methods in RegionQuery: " + methodDump);
                    // Try each one to see which works
                    for (java.lang.reflect.Method method : applicableMethods) {
                        Class<?>[] paramTypes = method.getParameterTypes();
                        if (paramTypes.length == 1) {
                            // Try to see if we can convert our location to this type
                            Class<?> paramType = paramTypes[0];
                            plugin.debug("Found getApplicableRegions(" + paramType.getSimpleName() + ") - checking compatibility");
                            
                            // If it's a Location type from WorldEdit, try using it
                            if (paramType.getName().contains("Location") || 
                                paramType.getName().contains("Vector") ||
                                paramType.getName().contains("BlockVector")) {
                                try {
                                    // Try to convert our location to this type using the adapter
                                    Object testLocation = bukkitAdapterAdapt.invoke(null, 
                                        plugin.getServer().getWorlds().get(0).getSpawnLocation());
                                    if (paramType.isInstance(testLocation) || paramType.isAssignableFrom(testLocation.getClass())) {
                                        regionQueryGetApplicableRegions = method;
                                        wgLocationClass = paramType;
                                        plugin.debug("Successfully matched getApplicableRegions with parameter type: " + paramType.getName());
                                        break;
                                    }
                                } catch (Exception ex3) {
                                    plugin.debug("Could not test compatibility with " + paramType.getName() + ": " + ex3.getMessage());
                                }
                            }
                        }
                    }
                } else {
                    plugin.debug("No getApplicableRegions method found in RegionQuery class");
                    // List all methods for debugging
                    StringBuilder allMethods = new StringBuilder();
                    for (java.lang.reflect.Method method : regionQueryClass.getMethods()) {
                        allMethods.append(method.getName()).append("(");
                        Class<?>[] params = method.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            allMethods.append(params[i].getSimpleName());
                            if (i < params.length - 1) allMethods.append(", ");
                        }
                        allMethods.append("); ");
                    }
                    plugin.debug("All RegionQuery methods: " + allMethods);
                }
                
                if (regionQueryGetApplicableRegions == null) {
                    throw new NoSuchMethodException("Could not find compatible getApplicableRegions method in RegionQuery. Tried location class: " + wgLocationClass.getName());
                }
            }
            
            Class<?> applicableClass = regionQueryGetApplicableRegions.getReturnType();
            applicableGetRegions = applicableClass.getMethod("getRegions");
            
            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, wgClassLoader);
            regionGetPriority = protectedRegionClass.getMethod("getPriority");
            regionGetId = protectedRegionClass.getMethod("getId");
            reflectionReady = true;
            plugin.getLogger().info("WorldGuard reflection initialized successfully (version: " + wgVersion + ")");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("WorldGuard reflection initialization failed: Class not found - " + e.getMessage());
            plugin.debug("WorldGuard reflection ClassNotFoundException: " + e.getClass().getName() + ": " + e.getMessage());
            plugin.debug("Attempted to load class: " + e.getMessage());
            reflectionReady = false;
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("WorldGuard reflection initialization failed: Method not found - " + e.getMessage());
            plugin.debug("WorldGuard reflection NoSuchMethodException: " + e.getClass().getName() + ": " + e.getMessage());
            reflectionReady = false;
        } catch (Exception e) {
            plugin.getLogger().warning("WorldGuard reflection initialization failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            plugin.debug("WorldGuard reflection exception: " + e.getClass().getName() + " - " + e.getMessage());
            if (plugin.getConfigManager() != null && plugin.getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            reflectionReady = false;
        }
    }
}
