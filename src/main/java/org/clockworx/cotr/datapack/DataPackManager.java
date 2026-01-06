package org.clockworx.cotr.datapack;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.clockworx.cotr.CoinOfTheRealmPlugin;
import org.clockworx.cotr.config.CoinConfig;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * DataPackManager - Manages data pack generation and installation
 * 
 * This class handles:
 * - Generating item definition JSON from config
 * - Creating data pack structure
 * - Installing data pack to all world datapacks folders
 * - Checking installation status
 * 
 * The data pack is automatically installed to all worlds on plugin enable,
 * allowing custom items like cotr:coin to be used with /give commands.
 */
public class DataPackManager {
    
    private final CoinOfTheRealmPlugin plugin;
    private final String datapackName = "cotr-datapack";
    
    /**
     * Creates a new DataPackManager for the specified plugin.
     * 
     * @param plugin The plugin instance
     */
    public DataPackManager(@NotNull CoinOfTheRealmPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * Generates and installs the data pack to all worlds.
     * 
     * @param coinConfig The coin configuration to use for item definition
     * @param maxStackSize The maximum stack size
     * @param rarity The item rarity
     * @return true if installation was successful for at least one world
     */
    public boolean installDataPack(@NotNull CoinConfig coinConfig, int maxStackSize, @NotNull String rarity) {
        plugin.getLogger().info("Generating data pack for custom item registration...");
        
        // Generate item definition JSON
        String itemJson = ItemDefinitionGenerator.generateItemDefinition(coinConfig, maxStackSize, rarity);
        
        // Get the item key to determine namespace and item name
        String itemKey = coinConfig.getItemKey();
        String namespace;
        String itemName;
        
        if (itemKey.contains(":")) {
            String[] parts = itemKey.split(":", 2);
            namespace = parts[0];
            itemName = parts[1];
        } else {
            // Default namespace
            namespace = "cotr";
            itemName = "coin";
        }
        
        // Get all world datapacks folders
        List<File> datapackFolders = getWorldDatapacksFolders();
        
        if (datapackFolders.isEmpty()) {
            plugin.getLogger().warning("No world datapacks folders found. Data pack will not be installed.");
            plugin.getLogger().info("You may need to manually install the data pack to enable /give commands.");
            return false;
        }
        
        int successCount = 0;
        for (File datapacksFolder : datapackFolders) {
            if (installDataPackToFolder(datapacksFolder, namespace, itemName, itemJson)) {
                successCount++;
            }
        }
        
        if (successCount > 0) {
            plugin.getLogger().info("Data pack installed to " + successCount + " world(s).");
            plugin.getLogger().info("Note: You may need to restart the server or reload datapacks for changes to take effect.");
            plugin.getLogger().info("Use /reload or restart the server to activate the data pack.");
            return true;
        } else {
            plugin.getLogger().warning("Failed to install data pack to any world.");
            return false;
        }
    }
    
    /**
     * Gets all world datapacks folders.
     * 
     * @return List of datapacks folder paths
     */
    @NotNull
    private List<File> getWorldDatapacksFolders() {
        List<File> folders = new ArrayList<>();
        
        // Get server root directory
        File serverRoot = Bukkit.getWorldContainer();
        
        // Check each loaded world
        for (World world : Bukkit.getWorlds()) {
            File worldFolder = world.getWorldFolder();
            File datapacksFolder = new File(worldFolder, "datapacks");
            
            if (datapacksFolder.exists() || datapacksFolder.mkdirs()) {
                folders.add(datapacksFolder);
                plugin.debug("Found datapacks folder: {}", datapacksFolder.getAbsolutePath());
            }
        }
        
        // Also check for unloaded worlds in the server directory
        if (serverRoot != null && serverRoot.exists()) {
            File[] worldFolders = serverRoot.listFiles(File::isDirectory);
            if (worldFolders != null) {
                for (File worldFolder : worldFolders) {
                    // Skip if already processed
                    boolean alreadyAdded = folders.stream()
                        .anyMatch(f -> f.getParentFile().getName().equals(worldFolder.getName()));
                    
                    if (!alreadyAdded) {
                        File datapacksFolder = new File(worldFolder, "datapacks");
                        if (datapacksFolder.exists() || datapacksFolder.mkdirs()) {
                            folders.add(datapacksFolder);
                            plugin.debug("Found datapacks folder (unloaded world): {}", datapacksFolder.getAbsolutePath());
                        }
                    }
                }
            }
        }
        
        return folders;
    }
    
    /**
     * Installs the data pack to a specific datapacks folder.
     * 
     * @param datapacksFolder The datapacks folder to install to
     * @param namespace The item namespace (e.g., "cotr")
     * @param itemName The item name (e.g., "coin")
     * @param itemJson The item definition JSON
     * @return true if installation was successful
     */
    private boolean installDataPackToFolder(@NotNull File datapacksFolder, 
                                           @NotNull String namespace, 
                                           @NotNull String itemName, 
                                           @NotNull String itemJson) {
        try {
            // Create data pack folder
            File datapackFolder = new File(datapacksFolder, datapackName);
            if (!datapackFolder.exists()) {
                datapackFolder.mkdirs();
            }
            
            // Copy pack.mcmeta from resources
            copyPackMeta(datapackFolder);
            
            // Create data/namespace/item/ structure
            File itemFolder = new File(datapackFolder, "data/" + namespace + "/item");
            if (!itemFolder.exists()) {
                itemFolder.mkdirs();
            }
            
            // Write item definition JSON
            File itemFile = new File(itemFolder, itemName + ".json");
            try (FileWriter writer = new FileWriter(itemFile)) {
                writer.write(itemJson);
            }
            
            plugin.debug("Installed data pack to: {}", datapackFolder.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to install data pack to " + datapacksFolder.getAbsolutePath() + ": " + e.getMessage());
            plugin.debug("Exception details: ", e);
            return false;
        }
    }
    
    /**
     * Copies pack.mcmeta from plugin resources to the data pack folder.
     */
    private void copyPackMeta(@NotNull File datapackFolder) throws IOException {
        InputStream packMetaStream = plugin.getResource("cotr-datapack/pack.mcmeta");
        if (packMetaStream == null) {
            // Create default pack.mcmeta if resource not found
            createDefaultPackMeta(datapackFolder);
            return;
        }
        
        File packMetaFile = new File(datapackFolder, "pack.mcmeta");
        Files.copy(packMetaStream, packMetaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        packMetaStream.close();
    }
    
    /**
     * Creates a default pack.mcmeta file.
     */
    private void createDefaultPackMeta(@NotNull File datapackFolder) throws IOException {
        File packMetaFile = new File(datapackFolder, "pack.mcmeta");
        try (FileWriter writer = new FileWriter(packMetaFile)) {
            writer.write("{\n");
            writer.write("  \"pack\": {\n");
            writer.write("    \"pack_format\": 61,\n");
            writer.write("    \"description\": \"Coin of the Realm Data Pack\"\n");
            writer.write("  }\n");
            writer.write("}\n");
        }
    }
    
    /**
     * Checks if the data pack is installed in a specific world.
     * 
     * @param world The world to check
     * @return true if the data pack is installed
     */
    public boolean isDataPackInstalled(@NotNull World world) {
        File worldFolder = world.getWorldFolder();
        File datapacksFolder = new File(worldFolder, "datapacks");
        File datapackFolder = new File(datapacksFolder, datapackName);
        
        File packMeta = new File(datapackFolder, "pack.mcmeta");
        return packMeta.exists();
    }
}
