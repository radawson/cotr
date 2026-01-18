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
            if (bukkitAdapterAdapt.getParameterCount() == 1) {
                wgLocation = bukkitAdapterAdapt.invoke(null, location);
            } else {
                // Might need World parameter
                wgLocation = bukkitAdapterAdapt.invoke(null, location, location.getWorld());
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
            regionQueryGetApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", wgLocationClass);
            
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
