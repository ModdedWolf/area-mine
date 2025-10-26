# Area Mine v3.0.0 - Major Feature Update

## 🎉 What's New

### Core Improvements
- **Durability System Overhaul**: Fixed durability scaling bug. Now only consumes durability when enabled (default: 50% per block)
- **XP Requirement Removed**: Area mining no longer consumes XP points - only tool durability matters!
- **10% Durability Warning**: Get alerted when your tool reaches critical durability levels (10% or below)
- **Persistent Player Data**: All statistics and progress now save to disk and persist across server restarts

### Mining Patterns (Feature #1, 2)
Choose from 6 different mining patterns:
- **Cube** (default): Traditional 3x3x3 area mining
- **Sphere**: Mine in a spherical radius
- **Tunnel**: Create long corridors forward
- **Cross**: Mine in a + shape (perfect for finding ores!)
- **Layer**: Mine flat horizontal layers
- **Vertical**: Mine straight up and down

Change via config: `"miningPattern": "sphere"`

### Keybinds (Feature #3, 4)
- **Toggle Area Mining**: Press `V` (rebindable) to enable/disable area mining
- **Preview Mode**: Hold `Left Alt` (rebindable) to see particle preview of blocks that will be mined
  - Green particles on target block
  - Cyan particles on area blocks

### Advanced Statistics (Feature #7, 8)
Track your mining performance:
- **Per-Block Stats**: See how many of each block type you've mined
- **Per-Dimension Stats**: Track mining across Overworld, Nether, End
- **Mining Efficiency**: Total blocks mined, time spent mining
- **Mining Tokens**: Earn tokens for every block mined

Commands:
- `/areamine stats` - View your detailed stats
- `/areamine stats all` - View server-wide stats (OP only)

### Undo System (Feature #9)
Made a mistake? Just `/areamine undo`!
- Restores blocks exactly as they were
- Automatically removes items from your inventory
- 5-minute time limit to prevent abuse
- Only stores your last mining operation

### Leaderboards (Feature #14)
Compete with other players:
- `/areamine leaderboard blocks` - Top miners by blocks
- `/areamine leaderboard diamonds` - Top diamond miners
- `/areamine leaderboard tokens` - Richest token holders

### Upgrade System (Feature #21)
Earn Mining Tokens and unlock permanent upgrades:
- **Radius Boost** (100 tokens): Mine +1 block in all directions
- **Efficiency Boost** (150 tokens): Earn 50% more tokens
- **Durability Boost** (200 tokens): Reduce durability cost by 15%
- **Auto Pickup** (250 tokens): Automatically collect mined blocks

Commands:
- `/areamine upgrades` - View available upgrades and your token balance
- `/areamine upgrades buy <upgrade>` - Purchase an upgrade

### Enchantment Synergies (Feature #10, 11)
Area Mine now works better with other enchantments:
- **Efficiency**: 20% speed bonus per level (configurable)
- **Unbreaking**: 15% durability reduction per level
- **Mending**: XP from mined blocks goes to tool repair
- **Fortune/Silk Touch**: Already respected in area mining!

### Block Filters (Feature #12)
Filter what you mine via config:
- `filterOresOnly`: Only mine ore blocks
- `filterStoneOnly`: Only mine stone-type blocks
- `filterIgnoreDirtGravel`: Skip dirt, gravel, and sand
- `filterValuableOnly`: Only mine valuable ores

### Client-Side Effects (Feature #9)
- Enchanting particles show blocks being mined
- Sound effects when area mining activates
- Visual particle preview mode
- Action bar feedback with mining stats

## 🔧 Technical Changes

### Configuration Updates
New config options in `config/area-mine/config.json`:
```json
{
  "configVersion": 3,
  "durabilityScaling": true,
  "durabilityMultiplier": 0.5,
  "miningPattern": "cube",
  "filterOresOnly": false,
  "filterStoneOnly": false,
  "filterIgnoreDirtGravel": false,
  "filterValuableOnly": false,
  "enableEnchantmentSynergies": true,
  "efficiencySpeedBonus": 0.2,
  "unbreakingDurabilityReduction": 0.15,
  "mendingXpToTool": true,
  "enableUpgradeSystem": true,
  "miningTokensPerBlock": 1,
  "upgradeCosts": {
    "radius_boost": 100,
    "efficiency_boost": 150,
    "durability_boost": 200,
    "auto_pickup": 250
  }
}
```

### New Commands
- `/areamine undo` - Undo last mining operation
- `/areamine leaderboard <type>` - View leaderboards
- `/areamine upgrades` - View available upgrades
- `/areamine upgrades buy <upgrade>` - Purchase upgrade

### Data Persistence
Player data now saves to `world/data/area_mine/<uuid>.json`:
- Auto-saves every 5 minutes
- Saves on server shutdown
- Includes all stats, tokens, and upgrades

### Breaking Changes
- Config version bumped to 3 (automatic migration)
- XP cost system completely removed
- Durability scaling now enabled by default

## 📊 Statistics Example
```
/areamine stats

§e[Area Mine] Detailed Stats:
  §7Blocks mined: §f5,243
  §7Times used: §f421
  §7Mining tokens: §a1,250
  §7Status: §aEnabled
  §e Top Mined Blocks:
    §7minecraft:stone: §f2,103
    §7minecraft:deepslate: §f1,542
    §7minecraft:diamond_ore: §f87
    §7minecraft:iron_ore: §f325
    §7minecraft:coal_ore: §f598
  §e Dimension Stats:
    §7minecraft:overworld: §f4,123 blocks
    §7minecraft:the_nether: §f1,120 blocks
```

## 🎮 Gameplay Tips

1. **Earn Tokens Fast**: Use Efficiency enchantment and unlock Efficiency Boost upgrade
2. **Save Durability**: Unlock Durability Boost and use Unbreaking enchantment
3. **Find Ores**: Use Cross pattern with `filterOresOnly` config
4. **Strip Mining**: Use Tunnel pattern for efficient branch mining
5. **Layer Mining**: Use Layer pattern for clearing large areas

## 🐛 Bug Fixes
- Fixed durability scaling applying incorrectly
- Fixed duplicate variable naming in mixin
- Removed XP cost calculation errors
- Fixed tool breaking prevention logic
- Improved tool durability warnings

## 🔄 Migration Guide
If upgrading from v1.3.0:
1. Backup your config file (optional)
2. Replace mod JAR
3. Restart server
4. Config will auto-migrate to v3
5. All existing player data will be preserved

## 🎯 Future Plans
- Mod Menu integration for in-game config GUI
- World protection mod integration (WorldGuard, GriefPrevention)
- More mining patterns
- Custom enchantment conflicts
- Multiplayer leaderboard web interface

## 📝 Notes
- Mod is now restricted to pickaxes only (as intended)
- Client-side features require Fabric API
- Keybinds can be rebound in Minecraft controls menu
- ModMenu and Cloth Config dependencies are optional (commented out in build.gradle)

---

**Full Changelog**: v1.3.0...v3.0.0
**Download**: [Releases](https://github.com/ModdedWolf/area-mine/releases)

