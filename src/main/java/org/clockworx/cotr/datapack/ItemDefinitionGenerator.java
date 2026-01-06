package org.clockworx.cotr.datapack;

import org.clockworx.cotr.config.CoinConfig;
import org.jetbrains.annotations.NotNull;

/**
 * ItemDefinitionGenerator - Generates item definition JSON for data packs
 * 
 * This class generates the JSON structure for custom item definitions
 * that can be used in Minecraft data packs. The generated JSON follows
 * the format required for custom items in Minecraft 1.20.5+.
 * 
 * The generated item definition includes:
 * - Display name
 * - Lore
 * - Item model reference
 * - Max stack size
 * - Rarity
 */
public class ItemDefinitionGenerator {
    
    /**
     * Escapes a string for JSON.
     */
    private static String escapeJson(@NotNull String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Generates a complete item definition JSON string from a CoinConfig.
     * 
     * @param coinConfig The coin configuration
     * @param maxStackSize The maximum stack size (default: 64)
     * @param rarity The item rarity (common, uncommon, rare, epic)
     * @return A JSON string representing the item definition
     */
    @NotNull
    public static String generateItemDefinition(@NotNull CoinConfig coinConfig, 
                                                int maxStackSize, 
                                                @NotNull String rarity) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"components\": {\n");
        
        // Display name component
        json.append("    \"minecraft:display_name\": {\n");
        json.append("      \"text\": \"").append(escapeJson(coinConfig.getDisplayName())).append("\"\n");
        json.append("    }");
        
        // Lore component
        if (!coinConfig.getLore().isEmpty()) {
            json.append(",\n");
            json.append("    \"minecraft:lore\": [\n");
            for (int i = 0; i < coinConfig.getLore().size(); i++) {
                json.append("      {\n");
                json.append("        \"text\": \"").append(escapeJson(coinConfig.getLore().get(i))).append("\"\n");
                json.append("      }");
                if (i < coinConfig.getLore().size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("    ]");
        }
        
        // Item model component (if model is configured)
        if (coinConfig.getModelNamespace() != null && coinConfig.getModelKey() != null) {
            String modelPath = coinConfig.getModelNamespace() + ":item/" + coinConfig.getModelKey();
            json.append(",\n");
            json.append("    \"minecraft:item_model\": \"").append(escapeJson(modelPath)).append("\"");
        }
        
        // Max stack size component
        if (maxStackSize > 0 && maxStackSize <= 64) {
            json.append(",\n");
            json.append("    \"minecraft:max_stack_size\": ").append(maxStackSize);
        }
        
        // Rarity component
        if (rarity != null && !rarity.isEmpty()) {
            json.append(",\n");
            json.append("    \"minecraft:rarity\": \"").append(escapeJson(rarity)).append("\"");
        }
        
        json.append("\n");
        json.append("  }\n");
        json.append("}");
        
        return json.toString();
    }
    
    /**
     * Generates item definition with default values (max stack 64, rarity common).
     * 
     * @param coinConfig The coin configuration
     * @return A JSON string representing the item definition
     */
    @NotNull
    public static String generateItemDefinition(@NotNull CoinConfig coinConfig) {
        return generateItemDefinition(coinConfig, 64, "common");
    }
}
