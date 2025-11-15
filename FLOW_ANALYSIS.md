# Area Mine Flow Analysis & Issues

## Flow Trace

### 1. Initialization Flow
```
AreaEnchantMod.onInitialize()
  └─> registerServerEvents()
      └─> AreaMineHandler.register() [ONCE, protected by eventsRegistered flag]
          ├─> PlayerBlockBreakEvents.BEFORE.register() [Captures mining face]
          └─> PlayerBlockBreakEvents.AFTER.register() [Handles area mining]
```

### 2. Block Breaking Flow (Event-Based - PRIMARY)
```
Player breaks block
  └─> PlayerBlockBreakEvents.BEFORE fires
      ├─> Check: isBreakingArea? → return true (skip)
      ├─> Check: bedrock/unbreakable? → return false (prevent)
      └─> Capture mining face → store in playerMiningFaces map
  └─> Block breaks (vanilla)
  └─> PlayerBlockBreakEvents.AFTER fires
      ├─> Check: isBreakingArea? → return (prevent recursion) ⚠️ CRITICAL CHECK
      ├─> Check: bedrock? → return (skip)
      ├─> Get mining face from map
      └─> handleAreaMining()
          ├─> Check: isBreakingArea? → return (redundant but safe)
          ├─> Validate enchantment, cooldown, pattern
          ├─> Get blocks from pattern
          ├─> Filter blocks
          ├─> Set isBreakingArea = true ⚠️ CRITICAL
          ├─> Pre-add ALL blocks to undo data
          ├─> Save undo data
          └─> Branch:
              ├─> Batched: BatchedBlockBreaker.startBreaking()
              │   └─> Process blocks over multiple ticks
              │       └─> finish() clears flag and saves undo data
              └─> Instant: Break blocks immediately
                  ├─> Use setBlockState(Blocks.AIR, 3) ⚠️ NO EVENT TRIGGER
                  └─> Clear isBreakingArea flag
```

### 3. Block Breaking Flow (Mixin-Based - BACKUP)
```
Player breaks block
  └─> ServerPlayerInteractionManagerMixin.onBlockBreak()
      ├─> Check: AreaMineHandler.isBreakingArea()? → return (skip) ⚠️ PREVENTS DUPLICATE
      └─> onBlockBreakInternal()
          ├─> Check: isBreakingArea (local) OR AreaMineHandler.isBreakingArea()? → return
          ├─> Validate enchantment, cooldown, pattern
          ├─> Get blocks from pattern
          ├─> Filter blocks
          ├─> Set isBreakingArea (local) = true
          ├─> Set AreaMineHandler.isBreakingArea() = true
          └─> Branch:
              ├─> Batched: BatchedBlockBreaker.startBreaking()
              └─> Instant: Break blocks
                  └─> Use world.breakBlock() ⚠️ TRIGGERS EVENTS!
                      └─> This could cause recursion!
```

## CRITICAL ISSUES FOUND

### Issue #1: Mixin Uses `world.breakBlock()` Which Triggers Events
**Location**: `ServerPlayerInteractionManagerMixin.java:380`
**Severity**: HIGH
**Problem**: The mixin's instant breaking path uses `world.breakBlock()` which triggers `PlayerBlockBreakEvents.AFTER`, potentially causing recursion or duplicate processing.
**Fix**: Change to `world.setBlockState(Blocks.AIR, 3)` like the event handler does.

### Issue #2: Duplicate Block Breaking Systems
**Location**: Both `AreaMineHandler` and `ServerPlayerInteractionManagerMixin`
**Severity**: MEDIUM
**Problem**: Two systems handle area mining. While the mixin checks if the event handler is processing, having both adds complexity and potential for bugs.
**Recommendation**: Consider removing the mixin path entirely since the event-based system is the primary one.

### Issue #3: Center Block Not Added to Undo in Mixin Path
**Location**: `ServerPlayerInteractionManagerMixin.java:357`
**Severity**: MEDIUM
**Problem**: In the mixin's instant breaking, the center block (`pos`) is skipped (line 368), so it's never added to undo data. The event handler correctly adds it with `originalCenterBlockState`.
**Fix**: Add center block to undo data before the loop.

### Issue #4: Potential Race Condition in Undo Data Saving
**Location**: `AreaMineHandler.handleAreaMining():391-399` and `BatchedBlockBreaker.startBreaking():40-50`
**Severity**: MEDIUM
**Problem**: If a player starts a new area mine while batched breaking is still processing, the undo data check might not prevent overwriting if the timing is wrong.
**Current Protection**: Checks for existing undo data, but there's a window between check and save.
**Recommendation**: Add synchronization or queue system for undo data.

### Issue #5: Block State Timing in Multiplayer
**Location**: `AreaMineHandler.handleAreaMining():368-384`
**Severity**: LOW
**Problem**: Blocks are pre-added to undo data, but in multiplayer, another player might break a block between pre-adding and actual breaking. The undo data would have the wrong state.
**Current Mitigation**: Code comments acknowledge this, but no fix.
**Recommendation**: Re-check block state right before breaking in batched mode.

### Issue #6: Flag Management Inconsistency
**Location**: `ServerPlayerInteractionManagerMixin` uses both local and global flags
**Severity**: LOW
**Problem**: The mixin maintains a local `isBreakingArea` flag AND uses the global one from `AreaMineHandler`. This adds complexity.
**Current State**: Works but could be simplified.

### Issue #7: Undo Data Not Saved for Mixin Path
**Location**: `ServerPlayerInteractionManagerMixin.java:454`
**Severity**: HIGH
**Problem**: The mixin uses deprecated `playerData.setLastOperation(undoData)` which doesn't actually save to the separate undo storage. Should use `PlayerDataManager.setUndoData()`.
**Fix**: Change to use `PlayerDataManager.setUndoData()`.

## RECOMMENDED FIXES

### Priority 1 (Critical)
1. Fix mixin to use `setBlockState(Blocks.AIR, 3)` instead of `world.breakBlock()`
2. Fix mixin to use `PlayerDataManager.setUndoData()` instead of deprecated method
3. Add center block to undo data in mixin path

### Priority 2 (Important)
4. Consider removing mixin path entirely (event-based system is primary)
5. Add synchronization for undo data saving to prevent race conditions

### Priority 3 (Nice to Have)
6. Re-check block states right before breaking in batched mode
7. Simplify flag management (remove local flag from mixin)

## CODE QUALITY ISSUES

1. **Debug Print Statements**: Many `System.out.println` statements still present in undo command
2. **Inconsistent Error Handling**: Some places use `System.err.println`, others use exceptions
3. **Magic Numbers**: Hardcoded values like `300000` (5 minutes) should be constants
