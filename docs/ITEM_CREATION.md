# Custom Item Creation Guide

This guide explains how to create custom items for use with the Coin of the Realm plugin. Custom items allow you to use `/give` commands with your own item IDs (e.g., `/give player mynamespace:myitem 5`), providing a unique currency that won't interfere with other plugins or vanilla items.

## Table of Contents

1. [Overview](#overview)
2. [Resource Packs vs Data Packs](#resource-packs-vs-data-packs)
3. [Creating a Custom Item](#creating-a-custom-item)
4. [Item Definition Components](#item-definition-components)
5. [Resource Pack Setup](#resource-pack-setup)
6. [Integration with Coin of the Realm](#integration-with-coin-of-the-realm)
7. [Examples](#examples)
8. [Troubleshooting](#troubleshooting)

## Overview

In Minecraft 1.20.5+, you can register custom items using data packs. These items:
- Have unique IDs (e.g., `mynamespace:myitem`)
- Can be used with `/give` commands
- Support custom textures and models via resource packs
- Can have custom properties (display name, lore, rarity, etc.)

The Coin of the Realm plugin automatically generates and installs a data pack for the configured custom item, but you can also create your own custom items following this guide.

## Resource Packs vs Data Packs

It's important to understand the difference:

**Resource Packs** (appearance only):
- Define how items **look** (textures, models)
- Located in `assets/` folder
- Cannot register new item IDs
- Required for custom item appearance

**Data Packs** (game mechanics):
- Register new item IDs
- Define item properties (display name, lore, etc.)
- Located in `data/` folder
- Required for `/give` commands to work

**Both are needed** for a fully functional custom item:
- Data pack registers the item ID
- Resource pack makes it look custom

## Creating a Custom Item

### Step 1: Create Data Pack Structure

Create a folder structure in your world's `datapacks` directory:

```
datapacks/
└── my-custom-item-pack/
    ├── pack.mcmeta
    └── data/
        └── mynamespace/
            └── item/
                └── myitem.json
```

### Step 2: Create pack.mcmeta

Create `pack.mcmeta` in the data pack root:

```json
{
  "pack": {
    "pack_format": 61,
    "description": "My Custom Item Data Pack"
  }
}
```

**Note**: `pack_format` depends on your Minecraft version:
- 1.20.5 - 1.20.6: `pack_format: 18`
- 1.21 - 1.21.1: `pack_format: 61`
- Check [Minecraft Wiki](https://minecraft.wiki/w/Data_pack#Pack_format) for latest format

### Step 3: Create Item Definition

Create `data/mynamespace/item/myitem.json`:

```json
{
  "components": {
    "minecraft:display_name": {
      "text": "My Custom Item"
    },
    "minecraft:lore": [
      {"text": "This is a custom item"},
      {"text": "created for my server"}
    ],
    "minecraft:item_model": "mynamespace:item/myitem",
    "minecraft:max_stack_size": 64,
    "minecraft:rarity": "common"
  }
}
```

### Step 4: Load the Data Pack

1. Place the data pack folder in your world's `datapacks` directory
2. Restart the server or run `/reload`
3. Verify with `/datapack list`
4. Test with `/give @s mynamespace:myitem 1`

## Item Definition Components

The item definition JSON supports various components:

### Required Components

None! But you'll want at least a display name.

### Common Components

#### Display Name

```json
"minecraft:display_name": {
  "text": "My Custom Item"
}
```

#### Lore

```json
"minecraft:lore": [
  {"text": "Line 1"},
  {"text": "Line 2"}
]
```

#### Item Model

References the model in your resource pack:

```json
"minecraft:item_model": "mynamespace:item/myitem"
```

This corresponds to `assets/mynamespace/models/item/myitem.json` in your resource pack.

#### Max Stack Size

```json
"minecraft:max_stack_size": 64
```

Valid range: 1-64

#### Rarity

```json
"minecraft:rarity": "common"
```

Valid values: `common`, `uncommon`, `rare`, `epic`

### Advanced Components

#### Food Properties

```json
"minecraft:food": {
  "nutrition": 4,
  "saturation": 0.3,
  "can_always_eat": false
}
```

#### Tool Properties

```json
"minecraft:tool": {
  "rules": [
    {
      "blocks": ["minecraft:stone"],
      "speed": 1.0,
      "correct_for_drops": true
    }
  ]
}
```

#### Durability

```json
"minecraft:durability": {
  "max_durability": 100
}
```

## Resource Pack Setup

To make your custom item look unique, create a resource pack:

### Structure

```
resourcepack/
├── pack.mcmeta
└── assets/
    └── mynamespace/
        ├── models/
        │   └── item/
        │       └── myitem.json
        └── textures/
            └── item/
                └── myitem.png
```

### Item Model (myitem.json)

```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "mynamespace:item/myitem"
  }
}
```

Or for a more complex model:

```json
{
  "texture_size": [32, 32],
  "textures": {
    "0": "mynamespace:item/myitem",
    "particle": "mynamespace:item/myitem"
  },
  "elements": [
    {
      "from": [5, 4, 7.5],
      "to": [11, 12, 8.5],
      "faces": {
        "north": {"uv": [0.5, 0, 3.5, 4], "texture": "#0"},
        "south": {"uv": [0.5, 4, 3.5, 8], "texture": "#0"}
      }
    }
  ]
}
```

### Texture File

Create `assets/mynamespace/textures/item/myitem.png`:
- Recommended size: 16x16 or 32x32 pixels
- PNG format
- Transparent background supported

### pack.mcmeta

```json
{
  "pack": {
    "pack_format": 61,
    "description": "My Custom Item Resource Pack"
  }
}
```

## Integration with Coin of the Realm

The Coin of the Realm plugin can automatically generate and install a data pack for your custom coin item.

### Configuration

In `config.yml`:

```yaml
coin:
  # Custom item ID
  item: "cotr:coin"
  
  # Fallback material (used if custom item fails)
  fallback-item: "minecraft:gold_nugget"
  
  # Item model reference
  model: "cotr:coin"
  
  # Display name
  display-name: "Coin of the Realm"
  
  # Lore
  lore:
    - "A valuable currency"
    - "used throughout the realm."
  
  # Max stack size
  max-stack-size: 64
  
  # Rarity
  rarity: "common"
```

### Automatic Installation

When the plugin loads:
1. Checks if `coin.item` is a custom item (contains `:` and not `minecraft:`)
2. Generates item definition JSON from config
3. Creates data pack structure
4. Installs to all world `datapacks` folders
5. Logs installation status

### Manual Installation

If automatic installation fails:
1. Check server logs for errors
2. Verify world `datapacks` folders exist
3. Manually copy the generated data pack from plugin logs
4. Restart server or run `/reload`

## Examples

### Example 1: Simple Custom Coin

**Data Pack** (`data/myserver/item/coin.json`):
```json
{
  "components": {
    "minecraft:display_name": {
      "text": "Server Coin"
    },
    "minecraft:lore": [
      {"text": "Official server currency"}
    ],
    "minecraft:item_model": "myserver:item/coin",
    "minecraft:max_stack_size": 64,
    "minecraft:rarity": "common"
  }
}
```

**Resource Pack** (`assets/myserver/models/item/coin.json`):
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "myserver:item/coin"
  }
}
```

### Example 2: Rare Token

**Data Pack** (`data/myserver/item/rare_token.json`):
```json
{
  "components": {
    "minecraft:display_name": {
      "text": "Rare Token"
    },
    "minecraft:lore": [
      {"text": "A rare token"},
      {"text": "worth 100 coins"}
    ],
    "minecraft:item_model": "myserver:item/rare_token",
    "minecraft:max_stack_size": 16,
    "minecraft:rarity": "rare"
  }
}
```

### Example 3: Using Coin of the Realm Default

The plugin's default configuration creates `cotr:coin`:

```yaml
coin:
  item: "cotr:coin"
  model: "cotr:coin"
  display-name: "Coin of the Realm"
```

This automatically generates:
- Data pack: `datapacks/cotr-datapack/`
- Item definition: `data/cotr/item/coin.json`
- Works with: `/give player cotr:coin 5`

## Troubleshooting

### Item Not Found Error

**Problem**: `/give player mynamespace:myitem` says "Item not found"

**Solutions**:
1. Verify data pack is loaded: `/datapack list`
2. Check item definition path: `data/mynamespace/item/myitem.json`
3. Ensure `pack.mcmeta` exists and has correct format
4. Restart server (some changes require restart, not just `/reload`)

### Item Looks Like Missing Texture

**Problem**: Item appears as purple/black missing texture

**Solutions**:
1. Verify resource pack is applied
2. Check model path matches: `assets/mynamespace/models/item/myitem.json`
3. Verify texture exists: `assets/mynamespace/textures/item/myitem.png`
4. Check `item_model` in item definition matches model path

### Data Pack Not Loading

**Problem**: Data pack doesn't appear in `/datapack list`

**Solutions**:
1. Check folder structure is correct
2. Verify `pack.mcmeta` exists in data pack root
3. Check `pack_format` matches your Minecraft version
4. Look for errors in server console
5. Ensure data pack is in world's `datapacks` folder, not server root

### Plugin Installation Fails

**Problem**: Coin of the Realm can't install data pack

**Solutions**:
1. Check server logs for specific error
2. Verify world folders exist and are writable
3. Check disk space
4. Manually create `datapacks` folders if missing
5. Review plugin configuration for invalid values

### Item Created But Not Identified as Coin

**Problem**: Custom item created but plugin doesn't recognize it as coin

**Solutions**:
1. Verify NBT data is set (plugin adds `coinoftherealm:coin` NBT)
2. Check `isCoin()` method handles custom items
3. Ensure item has correct NBT identifier
4. Use `/cotr give` command instead of `/give` for proper coin creation

## Best Practices

1. **Use Unique Namespaces**: Use your server/plugin name as namespace to avoid conflicts
2. **Version Control**: Keep data pack and resource pack in version control
3. **Test First**: Test custom items in a test world before production
4. **Backup**: Backup worlds before installing data packs
5. **Documentation**: Document your custom items for other admins
6. **Resource Pack**: Always provide a resource pack for custom appearance
7. **Fallback**: Configure fallback materials for players without resource pack

## Additional Resources

- [Minecraft Data Pack Wiki](https://minecraft.wiki/w/Data_pack)
- [Minecraft Resource Pack Wiki](https://minecraft.wiki/w/Resource_pack)
- [Item Components Reference](https://minecraft.wiki/w/Item_components)
- [Model Format Documentation](https://minecraft.wiki/w/Model)

## Support

For issues specific to Coin of the Realm plugin:
- Check plugin logs
- Review configuration in `config.yml`
- Verify data pack installation status
- Check plugin documentation

For general Minecraft data pack questions:
- Minecraft Wiki
- Minecraft community forums
- Data pack tutorials
