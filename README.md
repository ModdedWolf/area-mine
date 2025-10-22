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

### Core Features
- **Three Enchantment Levels** - Progressively larger mining areas
  - **Level I**: Mines a 1×2×1 area (horizontal × vertical × depth)
  - **Level II**: Mines a 2×2×2 area
  - **Level III**: Mines a 3×3×3 area

- **Compatible Tools** - Works with Iron, Diamond, and Netherite pickaxes

- **Villager Trading** - Master level Librarians sell Area Mine enchanted books
  - Cost: 5 Emeralds + 1 Book
  - Random level between I-III

- **Fortune & Silk Touch Support** ✅ - Respects Fortune and Silk Touch enchantments on area-mined blocks (enabled by default)

- **Server-Side Mod** - Only needs to be installed on the server, no client installation required

### Optional Features (Configurable)
All features below are **disabled by default** and can be enabled in the config:

- **Auto-Pickup** - Automatically collect mined blocks to inventory
- **Vein Mining Mode** - Only mine blocks of the same type (perfect for ores!)
- **Durability Scaling** - Tool durability scales with blocks mined
- **XP Cost System** - Consume XP when mining multiple blocks
- **Cooldown System** - Add cooldown between area mine activations
- **Block Blacklist/Whitelist** - Control which blocks can be area-mined
- **Mining Patterns** - Different mining shapes (currently: cube)
- **Particle Effects** - Visual feedback for blocks to be mined
- **Sound Effects** - Audio feedback for area mining
- **Tool Type Support** - Extend to axes, shovels, and hoes

### Admin Commands ✅
- `/areamine reload` - Reload configuration
- `/areamine toggle <player>` - Enable/disable for specific players
- `/areamine stats [player]` - View mining statistics

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

The mod generates a config file at `config/area_enchant.json` where you can customize everything:

```json
{
  "levels": {
    "1": {"horizontal": 1, "vertical": 2, "depth": 1},
    "2": {"horizontal": 2, "vertical": 2, "depth": 2},
    "3": {"horizontal": 3, "vertical": 3, "depth": 3}
  },
  "allowedTools": [
    "minecraft:iron_pickaxe",
    "minecraft:diamond_pickaxe",
    "minecraft:netherite_pickaxe"
  ],
  "blockBlacklist": [],
  "blockWhitelist": [],
  "autoPickup": false,
  "veinMiningMode": false,
  "durabilityScaling": false,
  "xpCostPerBlock": 0,
  "cooldownTicks": 0,
  "miningPattern": "cube",
  "particleEffects": false,
  "soundEffects": false,
  "permissionsEnabled": false,
  "respectFortuneAndSilkTouch": true,
  "enableAxeSupport": false,
  "enableShovelSupport": false,
  "enableHoeSupport": false
}
```

### Configuration Options

| Option | Default | Description |
|--------|---------|-------------|
| `levels` | See above | Mining area dimensions for each enchantment level |
| `allowedTools` | Pickaxes | Tools that can receive the enchantment |
| `blockBlacklist` | `[]` | Blocks that cannot be area-mined (e.g., `["minecraft:spawner"]`) |
| `blockWhitelist` | `[]` | If set, only these blocks can be area-mined |
| `autoPickup` | `false` | Automatically collect mined blocks to inventory |
| `veinMiningMode` | `false` | Only mine blocks of the same type |
| `durabilityScaling` | `false` | Each block damages the tool |
| `xpCostPerBlock` | `0` | XP cost per block mined (0 = free) |
| `cooldownTicks` | `0` | Cooldown between uses in ticks (20 ticks = 1 second) |
| `miningPattern` | `"cube"` | Mining pattern shape |
| `particleEffects` | `false` | Show particles on blocks to be mined |
| `soundEffects` | `false` | Play sound when area mining activates |
| `permissionsEnabled` | `false` | Enable permission system integration |
| `respectFortuneAndSilkTouch` | `true` | Apply Fortune/Silk Touch to all mined blocks |
| `enableAxeSupport` | `false` | Allow enchantment on axes |
| `enableShovelSupport` | `false` | Allow enchantment on shovels |
| `enableHoeSupport` | `false` | Allow enchantment on hoes |

### Reload Config
Use `/areamine reload` in-game (requires OP level 2) to reload the config without restarting the server.

## 🔧 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/areamine reload` | OP Level 2 | Reloads the configuration file |
| `/areamine toggle <player>` | OP Level 2 | Enable/disable Area Mine for a specific player |
| `/areamine stats` | OP Level 2 | View server-wide Area Mine statistics |
| `/areamine stats <player>` | OP Level 2 | View statistics for a specific player |

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

### v1.2.0 - Major Feature Update
**New Features:**
- ✅ **Fortune & Silk Touch Support** - Area-mined blocks now respect Fortune and Silk Touch (enabled by default)
- ✅ **Advanced Admin Commands** - Toggle, stats tracking for players
- 🎁 **Auto-Pickup System** - Configurable auto-collection of mined blocks
- ⛏️ **Vein Mining Mode** - Mine only matching block types
- 💎 **Durability Scaling** - Configurable tool durability cost per block
- ✨ **XP Cost System** - Optional XP consumption per block
- ⏱️ **Cooldown System** - Configurable cooldown between activations
- 🚫 **Block Filters** - Blacklist/whitelist for specific blocks
- 🎨 **Particle Effects** - Visual feedback for area mining
- 🔊 **Sound Effects** - Audio feedback for area mining
- 📊 **Statistics Tracking** - Track blocks mined and usage per player
- 🔧 **Tool Type Support** - Framework for axes, shovels, hoes (configurable)

**Admin Commands:**
- `/areamine toggle <player>` - Enable/disable for specific players
- `/areamine stats [player]` - View mining statistics

**Balance:**
- All new features are disabled by default (except Fortune/Silk Touch)
- Server admins can enable features via config
- Maintains original behavior for existing servers

### v1.1.0
- Added `/areamine reload` command for operators
- Made config system hot-reloadable
- Fixed enchantment table availability
- Added Minecraft enchantment tags (in_enchanting_table, tradeable)
- Clarified server-side only installation

### v1.0.0
- Initial release
- Area Mine enchantment with 3 levels
- Villager trading support
- Configurable mining areas and allowed tools

