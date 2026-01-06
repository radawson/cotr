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
 * - Description
 * - Lore
 * - Item model reference
 * - Max stack size
 * - Rarity
 * - Enchantment glint
 * - Use duration and animation
 * - Attribute modifiers
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
        
        // Description component (if set)
        if (coinConfig.getDescription() != null && !coinConfig.getDescription().isEmpty()) {
            json.append(",\n");
            json.append("    \"minecraft:description\": {\n");
            json.append("      \"text\": \"").append(escapeJson(coinConfig.getDescription())).append("\"\n");
            json.append("    }");
        }
        
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
        
        // Enchantment glint component
        if (coinConfig.isEnchantmentGlint()) {
            json.append(",\n");
            json.append("    \"minecraft:enchantment_glint_override\": true");
        }
        
        // Use duration component (if set)
        if (coinConfig.getUseDuration() > 0) {
            json.append(",\n");
            json.append("    \"minecraft:use_duration\": ").append(coinConfig.getUseDuration());
        }
        
        // Use animation component (if set and not "none")
        if (coinConfig.getUseAnimation() != null && !coinConfig.getUseAnimation().equals("none")) {
            json.append(",\n");
            json.append("    \"minecraft:use_animation\": \"").append(escapeJson(coinConfig.getUseAnimation())).append("\"");
        }
        
        // Attribute modifiers component (if enabled)
        if (coinConfig.isAttributeModifiersEnabled() && !coinConfig.getAttributeModifiers().isEmpty()) {
            json.append(",\n");
            json.append("    \"minecraft:attribute_modifiers\": {\n");
            json.append("      \"modifiers\": [\n");
            for (int i = 0; i < coinConfig.getAttributeModifiers().size(); i++) {
                CoinConfig.AttributeModifier mod = coinConfig.getAttributeModifiers().get(i);
                json.append("        {\n");
                json.append("          \"type\": \"").append(escapeJson(mod.getAttribute())).append("\",\n");
                json.append("          \"amount\": ").append(mod.getAmount()).append(",\n");
                json.append("          \"operation\": \"").append(escapeJson(mod.getOperation())).append("\",\n");
                json.append("          \"slot\": \"").append(escapeJson(mod.getSlot())).append("\"\n");
                json.append("        }");
                if (i < coinConfig.getAttributeModifiers().size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      ]\n");
            json.append("    }");
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
