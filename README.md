# Area Mine Enchantment

A server-sided Fabric mod for Minecraft 1.21.10 that adds a powerful Area Mine enchantment, allowing you to mine multiple blocks at once!

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-brightgreen)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-blue)
![Side](https://img.shields.io/badge/Side-Server-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)

## 📖 Description

The **Area Mine** enchantment can be applied to pickaxes and automatically mines blocks in an area around your target block. Perfect for large-scale mining operations and resource gathering!

**⚠️ Server-Side Only**: This mod only needs to be installed on the server. Players can join without having the mod installed on their client!

## ✨ Features

- **Three Enchantment Levels** - Progressively larger mining areas
  - **Level I**: Mines a 1×2×1 area (horizontal × vertical × depth)
  - **Level II**: Mines a 2×2×2 area
  - **Level III**: Mines a 3×3×3 area

- **Compatible Tools** - Works with Iron, Diamond, and Netherite pickaxes

- **Villager Trading** - Master level Librarians sell Area Mine enchanted books
  - Cost: 5 Emeralds + 1 Book
  - Random level between I-III

- **Configurable** - Customize mining area sizes and allowed tools via config file

- **OP Commands** - `/areamine reload` command to reload config without restarting

- **Server-Side Mod** - Only needs to be installed on the server, no client installation required

## 🎮 How to Obtain

### 1. Trade with Librarian Villagers
Master-level (Level 5) Librarians have a chance to sell Area Mine enchanted books for 5 emeralds + 1 book.

### 2. Enchanting Table
Find it naturally through the enchanting table (Rare - Weight: 2).

### 3. Anvil
Combine enchanted books with your pickaxe using an anvil.

## 📥 Installation

### Server Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) (version 0.17.3 or higher) on your server
2. Install [Fabric API](https://modrinth.com/mod/fabric-api) in your server's mods folder
3. Download the latest mod JAR from the [Releases](https://github.com/ModdedWolf/area-mine/releases) page
4. Place the JAR file in your server's `mods` folder
5. Start your Fabric server

**Note**: Players do NOT need to install this mod on their client to join your server!

## ⚙️ Configuration

The mod generates a config file at `config/area_enchant.json` where you can customize:

```json
{
  "levels": {
    "1": {
      "horizontal": 1,
      "vertical": 2,
      "depth": 1
    },
    "2": {
      "horizontal": 2,
      "vertical": 2,
      "depth": 2
    },
    "3": {
      "horizontal": 3,
      "vertical": 3,
      "depth": 3
    }
  },
  "allowedTools": [
    "minecraft:iron_pickaxe",
    "minecraft:diamond_pickaxe",
    "minecraft:netherite_pickaxe"
  ]
}
```

### Reload Config
Use `/areamine reload` in-game (requires OP level 2) to reload the config without restarting the server.

## 🔧 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/areamine reload` | OP Level 2 | Reloads the configuration file |

## 📋 Requirements

- **Minecraft**: 1.21.10
- **Fabric Loader**: 0.17.3+
- **Java**: 21+
- **Fabric API**: Latest version

## 📝 License

This mod is licensed under the [MIT License](LICENSE).

## 👤 Author

Created by [ModdedWolf](https://github.com/ModdedWolf)

## 🐛 Issues & Suggestions

Found a bug or have a suggestion? Please open an issue on the [Issues](https://github.com/ModdedWolf/area-mine/issues) page!

## 🔄 Changelog

### v1.1.0
- Added `/areamine reload` command for operators
- Made config system hot-reloadable
- Config changes now take effect immediately with the reload command

### v1.0.0
- Initial release
- Area Mine enchantment with 3 levels
- Villager trading support
- Configurable mining areas and allowed tools

