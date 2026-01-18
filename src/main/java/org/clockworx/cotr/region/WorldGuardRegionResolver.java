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
            Object wgLocation = bukkitAdapterAdapt.invoke(null, location);
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
            
            ClassLoader wgClassLoader = wgPlugin.getClass().getClassLoader();
            
            // Load classes using WorldGuard's classloader
            Class<?> worldGuardClass = Class.forName("com.sk89q.worldguard.WorldGuard", true, wgClassLoader);
            Class<?> bukkitAdapterClass = Class.forName("com.sk89q.worldguard.bukkit.BukkitAdapter", true, wgClassLoader);
            
            worldGuardGetInstance = worldGuardClass.getMethod("getInstance");
            worldGuardGetPlatform = worldGuardClass.getMethod("getPlatform");
            platformGetRegionContainer = worldGuardGetPlatform.getReturnType().getMethod("getRegionContainer");
            regionContainerCreateQuery = platformGetRegionContainer.getReturnType().getMethod("createQuery");
            
            // The BukkitAdapter.adapt(Location) returns a WorldEdit location type
            bukkitAdapterAdapt = bukkitAdapterClass.getMethod("adapt", Location.class);
            
            Class<?> regionQueryClass = regionContainerCreateQuery.getReturnType();
            Class<?> wgLocationClass = bukkitAdapterAdapt.getReturnType();
            regionQueryGetApplicableRegions = regionQueryClass.getMethod("getApplicableRegions", wgLocationClass);
            
            Class<?> applicableClass = regionQueryGetApplicableRegions.getReturnType();
            applicableGetRegions = applicableClass.getMethod("getRegions");
            
            Class<?> protectedRegionClass = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, wgClassLoader);
            regionGetPriority = protectedRegionClass.getMethod("getPriority");
            regionGetId = protectedRegionClass.getMethod("getId");
            reflectionReady = true;
            plugin.getLogger().info("WorldGuard reflection initialized successfully");
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
            reflectionReady = false;
        }
    }
}
