# Area Mine v4.5.0

A powerful server-sided Fabric mod for Minecraft 1.21.10 that adds a multi-tier **Area Mine enchantment** system with unlockable mining patterns, token economy, and extensive progression!

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.10-brightgreen)
![Mod Loader](https://img.shields.io/badge/Mod%20Loader-Fabric-blue)
![Side](https://img.shields.io/badge/Side-Server-orange)
![License](https://img.shields.io/badge/License-MIT-yellow)
![Version](https://img.shields.io/badge/Version-4.5.0-red)

## 📖 Overview

**Area Mine** transforms pickaxe mining into a powerful, strategic system with:
- 🎯 **6 Unlockable Mining Patterns** - Cube (free), then unlock Sphere, Tunnel, Cross, Layer, and Vertical
- 🏆 **Tiered Enchantment System** - Levels I, II, and III with unique sizes per pattern
- 💎 **Token Economy** - Earn tokens from mining, spend them on patterns and upgrades
- 📊 **Achievement & Progression** - Track stats, earn achievements, compete on leaderboards
- ⚡ **Smart & Balanced** - Only works on pickaxe-effective blocks, scales durability by tier
- 🎮 **Server-Side** - Players can join without installing the mod!

## ✨ Core Features

### 🎯 Enchantment Tiers & Progression

Each tier increases mining area size, with **unique dimensions per pattern**:

| Pattern | Tier I | Tier II | Tier III |
|---------|--------|---------|----------|
| **Cube** (Free) | 1×2×1 | 2×2×2 | 3×3×3 |
| **Sphere** | 1 radius | 2 radius | 3 radius |
| **Tunnel** | 1×2×5 | 2×2×8 | 3×3×12 |
| **Cross** | 3×1×3 | 5×2×5 | 7×3×7 |
| **Layer** | 3×1×3 | 5×1×5 | 7×1×7 |
| **Vertical** | 1×5×1 | 1×8×1 | 1×12×1 |

**Format**: Width × Height × Depth (relative to player facing direction)

### 🗺️ Mining Patterns

#### **🟦 Cube** (Default - FREE)
Traditional area mining. Perfect for general-purpose mining and beginners.
- **Cost**: FREE (default pattern)
- **Best For**: General mining, resource gathering

#### **🔵 Sphere** (1,000,000 tokens)
Mine in a spherical radius around the target block.
- **Cost**: 1,000,000 tokens
- **Best For**: Creating chambers, caverns, large-scale excavation
- **Visual**: Orange sparkle particles

#### **🟡 Tunnel** (2,500,000 tokens)
Create long corridors in your facing direction.
- **Cost**: 2,500,000 tokens
- **Best For**: Branch mining, highway creation, exploration
- **Visual**: Yellow/orange flame particles

#### **🟢 Cross** (5,000,000 tokens)
Mine in a + shape extending in all cardinal directions.
- **Cost**: 5,000,000 tokens
- **Best For**: Ore finding, exploration, revealing hidden caves
- **Visual**: Green happy villager particles

#### **🟣 Layer** (10,000,000 tokens)
Mine flat horizontal layers at your target height.
- **Cost**: 10,000,000 tokens
- **Best For**: Clearing levels, flat mining, building prep
- **Visual**: Cyan/teal glow particles

#### **🟪 Vertical** (25,000,000 tokens)
Mine straight up or down from your position.
- **Cost**: 25,000,000 tokens
- **Best For**: Shaft creation, vertical exploration, depth mining
- **Visual**: Purple portal particles

### 💰 Token System & Block Values

Earn tokens from mining to unlock patterns and upgrades!

**Base Token Values** (configurable):
- **Common Blocks**: 1 token (stone, deepslate, etc.)
- **Iron Ore**: 10 tokens
- **Gold Ore**: 25 tokens
- **Diamond Ore**: 100 tokens
- **Ancient Debris**: 1,000 tokens

**Token Multipliers**:
- ⚡ **Efficiency I-V**: +20% tokens per level (Efficiency V = **+100%**)
- 📈 **Efficiency Boost Upgrade**: +50% tokens
- 🎯 **Combined**: Efficiency V + Upgrade = **+150% tokens** (2.5× multiplier)

### 🎮 Gameplay Mechanics

#### **✋ Crouch to Activate** (Default)
Area Mine only activates while **sneaking/crouching** - preventing accidental activations!

#### **⚒️ Pickaxe-Effective Blocks Only**
Only mines blocks that pickaxes are effective against:
- ✅ **Mines**: Stone, ores, deepslate, netherrack, terracotta, concrete, etc.
- ❌ **Ignores**: Dirt, sand, gravel, wood, leaves, etc.

#### **⚖️ Durability Scaling by Tier**
- **Tier I** (Area Mine I): 10% durability per block
- **Tier II** (Area Mine II): 20% durability per block  
- **Tier III** (Area Mine III): 30% durability per block

**Example**: Mining 10 blocks with Tier III costs **3 durability** (10 × 0.3 = 3)

#### **🔊 Sound Effects**
- **Start Mining**: Anvil sound (activation feedback)
- **Complete Mining**: XP orb pickup sound (completion feedback)

#### **🎨 Visual Preview** (Client-Side)
If installed client-side, see color-coded particle outlines showing exactly which blocks will be mined!
- Particles update based on server-selected pattern
- Configurable density (low/medium/high)
- Color-coded by pattern type

### 🏆 Upgrade System

Unlock permanent upgrades with tokens:

| Upgrade | Cost | Effect |
|---------|------|--------|
| **Radius Boost** | 100,000 | Mine +1 block in all directions |
| **Efficiency Boost** | 150,000 | Earn +50% tokens from all mining |
| **Durability Boost** | 200,000 | Reduce durability cost by 15% |
| **Auto Pickup** | 250,000 | Mined blocks go straight to inventory |

**Command**: `/areamine upgrades buy <upgrade_name>`

### 🏅 Achievement System

Unlock custom achievements as you progress:

| Achievement | Requirement | Reward |
|-------------|-------------|--------|
| **First Use** | Use Area Mine once | Unlocked tracking |
| **Efficiency Expert** | Mine 10,000 blocks | Milestone |
| **Diamond Digger** | Mine 1,000 diamond ore | Milestone |
| **Unstoppable** | Mine 100,000 total blocks | Elite status |
| **Excavator** | Mine 1,000 blocks in one session | Session master |
| **Token Millionaire** | Earn 1,000,000 tokens | Wealthy miner |
| **Master Miner** | Unlock all 4 upgrades | Full progression |

Achievements save automatically and persist across server restarts!

### ✨ Enchantment Compatibility

#### **✅ Compatible Enchantments**
- **Fortune**: Multiplies drops (ore → gems) ✅
- **Efficiency**: Faster breaking + token bonus ✅
- **Unbreaking**: Reduces durability cost ✅
- **Mending**: Repairs from XP orbs (ores, mobs) ✅

#### **❌ Incompatible Enchantments**
- **Silk Touch**: Cannot be applied with Area Mine (conflicting gameplay)

### 🎯 Advanced Features

#### **↩️ Undo System**
Made a mistake? Undo your last mining operation!
- **Command**: `/areamine undo`
- **Time Limit**: 5 minutes
- **Smart Inventory**: Removes mined items from your inventory when undoing

#### **📊 Leaderboards**
Compete with other players across multiple categories:
- `/areamine leaderboard blocks` - Top miners by total blocks
- `/areamine leaderboard diamonds` - Top diamond ore miners
- `/areamine leaderboard tokens` - Richest token holders

#### **📈 Detailed Statistics**
Track comprehensive mining stats:
- Total blocks mined (all-time & per session)
- Blocks by type (stone, deepslate, ores, etc.)
- Blocks by dimension (Overworld, Nether, End)
- Mining tokens earned and spent
- Times used and efficiency ratings

#### **⚡ Performance Optimized**
- **Batched Breaking**: Breaks blocks across multiple ticks (10 blocks/tick default)
- **Async Processing**: Won't lag the server even with large mining areas
- **Smart Caching**: Player data cached in memory, saves periodically

#### **🛡️ World Protection Integration**
Respects world protection plugins - won't mine in protected areas.

## 📥 Installation

### Server Installation (Required)

1. **Install Fabric Loader** (v0.17.3+)
   - Download from [fabricmc.net](https://fabricmc.net/use/)
   
2. **Install Fabric API** (Required Dependency)
   - Download from [Modrinth](https://modrinth.com/mod/fabric-api)
   - Place in `mods/` folder

3. **Install Area Mine**
   - Download from [Modrinth](https://modrinth.com/mod/area-mine) or [Releases](https://github.com/ModdedWolf/area-mine/releases)
   - Place in `mods/` folder
   - Restart server

**✅ Players can join without installing the mod!**

### Client Installation (Optional)

For visual preview and keybinds:
1. Install Fabric Loader on your client
2. Install Fabric API
3. Install Area Mine
4. Set keybinds in Controls → Area Mine category

## 🔧 Commands Reference

### Player Commands

| Command | Description |
|---------|-------------|
| `/areamine stats` | View your mining statistics and progress |
| `/areamine upgrades` | View available upgrades and token balance |
| `/areamine upgrades buy <name>` | Purchase an upgrade |
| `/areamine patterns` | View all patterns and unlock status |
| `/areamine patterns buy <name>` | Purchase a mining pattern |
| `/areamine undo` | Undo last mining operation (5min limit) |
| `/areamine leaderboard <type>` | View leaderboards (blocks/diamonds/tokens) |

### Admin Commands (OP Level 2+)

| Command | Description |
|---------|-------------|
| `/areamine reload` | Reload configuration from disk |
| `/areamine toggle <player>` | Enable/disable Area Mine for a player |
| `/areamine stats <player>` | View another player's statistics |
| `/areamine stats all` | View server-wide statistics |
| `/areamine pattern <name>` | Change server-wide pattern (syncs to all clients) |

## ⚙️ Configuration

Configuration file: `config/area-mine/config.json`

### Essential Settings

```json
{
  "configVersion": 4,
  "requireCrouch": true,
  "pickaxeEffectiveOnly": true,
  "miningPattern": "cube",
  
  "patternLevels": {
    "cube": {
      "1": {"horizontal": 1, "vertical": 2, "depth": 1},
      "2": {"horizontal": 2, "vertical": 2, "depth": 2},
      "3": {"horizontal": 3, "vertical": 3, "depth": 3}
    },
    "sphere": {
      "1": {"horizontal": 1, "vertical": 1, "depth": 1},
      "2": {"horizontal": 2, "vertical": 2, "depth": 2},
      "3": {"horizontal": 3, "vertical": 3, "depth": 3}
    }
  },
  
  "patternCosts": {
    "sphere": 1000000,
    "tunnel": 2500000,
    "cross": 5000000,
    "layer": 10000000,
    "vertical": 25000000
  },
  
  "enableBlockValues": true,
  "blockValues": {
    "minecraft:diamond_ore": 100,
    "minecraft:deepslate_diamond_ore": 100,
    "minecraft:ancient_debris": 1000,
    "minecraft:gold_ore": 25,
    "minecraft:iron_ore": 10
  },
  
  "durabilityScaling": true,
  "enableUpgradeSystem": true,
  "upgradeCosts": {
    "radius_boost": 100000,
    "efficiency_boost": 150000,
    "durability_boost": 200000,
    "auto_pickup": 250000
  },
  
  "enchantmentConflicts": ["minecraft:silk_touch"],
  
  "particleEffects": true,
  "previewDensity": "medium",
  "soundEffects": true,
  
  "batchedBreaking": true,
  "blocksPerTick": 10,
  
  "actionBarFeedback": false,
  "sendTokensInChat": true
}
```

### Configuration Options Explained

#### **Pattern Settings**
- `miningPattern`: Server-wide default pattern (`"cube"` recommended)
- `patternLevels`: Define size for each tier of each pattern
- `patternCosts`: Token cost to unlock each pattern (cube is always free)

#### **Block Values**
- `enableBlockValues`: Use different token values per block type
- `blockValues`: Map of `"block_id": token_value`
- Unlisted blocks default to `miningTokensPerBlock` (1 token)

#### **Durability System**
- `durabilityScaling`: Enable tier-based durability (10%/20%/30%)
- Tier I: 10% durability per block
- Tier II: 20% durability per block
- Tier III: 30% durability per block

#### **Performance**
- `batchedBreaking`: Break blocks over multiple ticks (prevents lag)
- `blocksPerTick`: How many blocks to break per tick (default: 10)

#### **Feedback**
- `actionBarFeedback`: Show messages above hotbar (default: `false`)
- `sendTokensInChat`: Send feedback in chat (default: `true`)

#### **Visual & Audio**
- `particleEffects`: Enable particle outlines
- `previewDensity`: Particle density (`"low"`, `"medium"`, `"high"`)
- `soundEffects`: Enable sound feedback

#### **Restrictions**
- `requireCrouch`: Only activate while sneaking (default: `true`)
- `pickaxeEffectiveOnly`: Only mine pickaxe-effective blocks (default: `true`)
- `enchantmentConflicts`: Enchantments incompatible with Area Mine

## 🎮 Gameplay Guide

### Getting Started

1. **Obtain the Enchantment**
   - Trade with Master-level Librarian villagers (5 emeralds + 1 book)
   - Find at Enchanting Table (Rare - Weight 2)

2. **Apply to Pickaxe**
   - Use anvil to combine enchanted book with pickaxe
   - Compatible with Fortune, Efficiency, Unbreaking, Mending

3. **Start Mining**
   - **Crouch** and break a block to activate
   - Watch particles show mining area (if client-side installed)
   - Hear activation sound, then completion sound

4. **Earn Tokens**
   - Automatically earn tokens from blocks mined
   - Check balance: `/areamine stats`

### Progression Path

1. **Early Game** (0-100K tokens)
   - Use **Cube** pattern (free)
   - Mine common blocks to earn tokens
   - Save up for **Radius Boost** (100K)

2. **Mid Game** (100K-1M tokens)
   - Unlock **Radius Boost** and **Efficiency Boost**
   - Save up for **Sphere** pattern (1M) or **Tunnel** (2.5M)
   - Start tracking achievements

3. **Late Game** (1M-25M tokens)
   - Unlock all patterns: Sphere → Tunnel → Cross → Layer → Vertical
   - Get **Durability Boost** and **Auto Pickup** upgrades
   - Compete on leaderboards

4. **End Game** (25M+ tokens)
   - All patterns and upgrades unlocked
   - Achieve "Token Millionaire" and "Master Miner"
   - Top leaderboard positions

### Strategy Tips

**🎯 Fastest Token Farming**
- Use **Efficiency V** pickaxe (+100% tokens)
- Unlock **Efficiency Boost** upgrade (+50% tokens)
- Total: **+150% tokens** (2.5× multiplier)
- Mine high-value blocks (diamond ore = 100 tokens each!)

**⚡ Best Durability Efficiency**
- Use **Tier I** enchantment (10% durability)
- Unlock **Durability Boost** upgrade (-15% cost)
- Add **Unbreaking III** enchantment (-45% cost)
- **Result**: ~5% durability cost per block!

**💎 Ore Finding**
- Use **Cross** pattern (reveals in 4 directions)
- Mine at Y-levels: -59 (diamond), 15 (iron), 48 (emerald)
- Enable `filterOresOnly: true` in config (admin only)

**🚇 Strip Mining**
- Use **Tunnel** pattern
- Tier III creates 3×3×12 corridors
- Space tunnels 6 blocks apart for full coverage

**🏗️ Large Excavation**
- Use **Layer** pattern for flat levels
- Use **Sphere** pattern for round chambers
- Tier III Layer: 7×1×7 = 49 blocks per use!

## 📊 Statistics Example

```
/areamine stats

§a[Area Mine] Your Stats:
  §fBlocks mined: §e125,483
  §fSession blocks: §e1,024
  §fMining tokens: §e3,482,190
  §fTimes used: §e8,432
  §fStatus: §aEnabled

§6Top Mined Blocks:
  §7minecraft:deepslate: §f42,103
  §7minecraft:stone: §f38,492
  §7minecraft:diamond_ore: §f1,284
  §7minecraft:iron_ore: §f8,921
  §7minecraft:coal_ore: §f15,639

§bDimension Stats:
  §7minecraft:overworld: §f98,423 blocks
  §7minecraft:the_nether: §f22,158 blocks
  §7minecraft:the_end: §f4,902 blocks

§dUnlocked Upgrades:
  §a✔ §fradius_boost
  §a✔ §fefficiency_boost
  §c✘ §7durability_boost
  §c✘ §7auto_pickup

§5Unlocked Patterns:
  §a✔ §fcube (default)
  §a✔ §fsphere
  §a✔ §ftunnel
  §c✘ §7cross
  §c✘ §7layer
  §c✘ §7vertical
```

## 🔄 Changelog

### v4.0.0 - Complete Overhaul 🚀

**🎯 Major Changes:**
- ✅ **Per-Pattern Tier Configurations**: Each pattern has unique sizes for Tiers I/II/III
- ✅ **Pickaxe-Effective Only**: Only mines blocks that pickaxes work on (no dirt/sand/wood)
- ✅ **Crouch Activation**: Must sneak to activate (prevents accidents)
- ✅ **Silk Touch Incompatibility**: Silk Touch cannot be applied with Area Mine
- ✅ **Pattern Unlock System**: Cube is free, other patterns cost 1M-25M tokens
- ✅ **Block Value System**: Different blocks award different token amounts
- ✅ **Durability Scaling by Tier**: Tier I=10%, Tier II=20%, Tier III=30%

**🎮 New Features:**
- 🎨 **Pattern-Specific Particles**: Each pattern has unique colors/types
- 🔊 **Sound Effects**: Anvil on start, XP orb on complete
- ⚡ **Async Block Breaking**: Batched breaking over ticks (10 blocks/tick)
- 🛡️ **World Protection**: Respects player permissions and protection plugins
- 🎯 **Preview Density Config**: Adjust particle density (low/medium/high)

**🏆 New Achievements:**
- 💰 **Token Millionaire**: Earn 1,000,000 tokens
- 🏔️ **Unstoppable**: Mine 100,000 total blocks
- 🎓 **Master Miner**: Unlock all 4 upgrades
- ⛏️ **Excavator**: Mine 1,000 blocks in one session

**🔧 Pattern Refinements:**
- Fixed **Tunnel** pattern width (was 7×7, now correct 3×3 at Tier III)
- Fixed **Sphere** pattern radius calculation (proper spheres now)
- Fixed **Layer** pattern offset (mines exact target Y-level)
- Fixed **Cross** pattern depth (1→3/5/7 for tiers)
- Fixed **Vertical** pattern centering

**📊 Commands:**
- `/areamine patterns` - List all patterns and unlock status
- `/areamine patterns buy <pattern>` - Purchase patterns with tokens
- `/areamine pattern <pattern>` - Admin: change server pattern

**🌐 Networking:**
- Server-client pattern synchronization
- Pattern changes instantly update client previews
- Pattern selection persists across sessions

**💎 Block Values (Default):**
- Diamond Ore: 100 tokens
- Ancient Debris: 1,000 tokens
- Gold Ore: 25 tokens
- Iron Ore: 10 tokens
- Common blocks: 1 token

**⚖️ Balance Changes:**
- Upgrade costs increased 1000× (100K→100K actual gameplay)
- Durability reduced to 10%/20%/30% (was 50% flat)
- Mending now works purely vanilla (XP orbs only)
- Action bar disabled by default (chat messages instead)

**🐛 Bug Fixes:**
- Fixed durability applying multiple times per mining operation
- Fixed tokens not being awarded in batched breaking
- Fixed `finish()` not being called, preventing token rewards
- Fixed chat messages not appearing for players
- Fixed particle outlines not showing correct patterns
- Fixed sphere particles flickering
- Fixed pickaxe breaking instantly from excessive durability cost

### v3.0.0 - Major Feature Update
- 6 Mining Patterns (Cube, Sphere, Tunnel, Cross, Layer, Vertical)
- Upgrade System with 4 unlockable upgrades
- Leaderboards and detailed statistics
- Undo system with inventory management
- Enchantment synergies (Efficiency, Unbreaking, Mending)
- Persistent data saving
- Achievement system

### v1.3.0 - Polish Update
- Multi-language support
- Config migration system
- Tool durability protection

### v1.0.0 - Initial Release
- Area Mine enchantment (3 tiers)
- Basic configuration

## 📋 Requirements

- **Minecraft**: 1.21.10
- **Fabric Loader**: 0.17.3+
- **Java**: 21+
- **Fabric API**: Latest for 1.21.10 (Required)

## 🎯 Roadmap

Potential future features:
- Custom enchantment GUI (in-game config editor)
- More mining patterns (spiral, diagonal, star)
- Pattern combinations (mix patterns)
- Prestige system (reset for bonuses)
- Team/guild statistics
- Custom particle effects per player

## 📝 License

This mod is licensed under the [MIT License](LICENSE).

## 👤 Author

Created by **ModdedWolf**

## 🐛 Support

Found a bug or have a suggestion?
- Open an issue on the issue tracker
- Join the Discord community
- Check existing documentation

## 🙏 Contributing

Contributions welcome! Feel free to:
- Submit pull requests
- Report bugs
- Suggest features
- Improve documentation

---

**Happy Mining! ⛏️✨**

*Area Mine v4.0.0 - The Ultimate Mining Enchantment*
