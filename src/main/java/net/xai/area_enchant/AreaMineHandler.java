package net.xai.area_enchant;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Event-based handler for Area Mine enchantment.
 * This replaces the mixin approach and should work better in singleplayer.
 */
public class AreaMineHandler {
    private static final Map<UUID, Direction> playerMiningFaces = new HashMap<>();
    private static final Map<UUID, Boolean> isBreakingArea = new HashMap<>();
    private static boolean eventsRegistered = false; // Prevent duplicate registration
    
    // Pending ore breaks that need to be processed on next tick (oreharvester bypasses AFTER event)
    private static class PendingOreBreak {
        final ServerPlayerEntity player;
        final ServerWorld world;
        final BlockPos pos;
        final BlockState originalState;
        final Direction miningFace;
        final long scheduledTick; // Tick when this was scheduled
        final PlayerDataManager.UndoData preSavedUndoData; // Block states saved BEFORE oreharvester breaks them
        
        PendingOreBreak(ServerPlayerEntity player, ServerWorld world, BlockPos pos, BlockState originalState, Direction miningFace, long scheduledTick, PlayerDataManager.UndoData preSavedUndoData) {
            this.player = player;
            this.world = world;
            this.pos = pos;
            this.originalState = originalState;
            this.miningFace = miningFace;
            this.scheduledTick = scheduledTick;
            this.preSavedUndoData = preSavedUndoData;
        }
    }
    private static final Map<UUID, PendingOreBreak> pendingOreBreaks = new HashMap<>();
    
    // Public method to clear breaking flag (called by BatchedBlockBreaker when done)
    public static void clearBreakingFlag(UUID playerId) {
        isBreakingArea.put(playerId, false);
    }
    
    // Public method to check if breaking area (called by BatchedBlockBreaker)
    public static boolean isBreakingArea(UUID playerId) {
        return isBreakingArea.getOrDefault(playerId, false);
    }
    
    // Public method to set breaking flag (called by BatchedBlockBreaker)
    public static void setBreakingArea(UUID playerId, boolean value) {
        isBreakingArea.put(playerId, value);
    }
    
    public static void register() {
        // CRITICAL: Prevent duplicate registration - this can cause events to fire multiple times
        if (eventsRegistered) {
            return;
        }
        
        eventsRegistered = true;
        
        // Capture mining face when player starts/stops breaking
        // CRITICAL: This event must be registered FIRST to capture the mining face
        // CRITICAL: We also capture the center block state here for undo data
        // This ensures we have the state BEFORE oreharvester or other mods can modify it
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            // CRITICAL: Don't capture mining face if we're already breaking an area
            // This prevents batched breaking from triggering new area mine events
            if (isBreakingArea.getOrDefault(player.getUuid(), false)) {
                return true; // Allow the break (it's from batched breaking)
            }
            
            // Prevent breaking bedrock/unbreakable blocks with area mine
            if (player instanceof ServerPlayerEntity serverPlayer && world instanceof ServerWorld serverWorld) {
                String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
                float hardness = state.getHardness(serverWorld, pos);
                
                // Check for bedrock or unbreakable blocks
                boolean isUnbreakable = hardness < 0 || 
                    blockId.equals("minecraft:bedrock") ||
                    AreaEnchantMod.config.blockBlacklist.contains(blockId);
                
                if (isUnbreakable) {
                    // Always prevent breaking bedrock/unbreakable blocks, regardless of enchantment
                    // This prevents both direct breaking and area mining from breaking bedrock
                    // Check if player has area mine enchantment to show message
                    var stack = serverPlayer.getMainHandStack();
                    Optional<net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment>> enchantmentRegistry = 
                        serverWorld.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                    if (enchantmentRegistry.isPresent()) {
                        RegistryEntry<net.minecraft.enchantment.Enchantment> entry = enchantmentRegistry.get()
                            .getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
                        if (entry != null && EnchantmentHelper.getLevel(entry, stack) > 0) {
                            serverPlayer.sendMessage(Text.literal("§c[Area Mine] Cannot break unbreakable blocks (bedrock)!"), false);
                        }
                    }
                    return false; // Prevent the break
                }
                
                // CRITICAL: Check if this is an ore and player has area mine
                // If so, we need to prepare to save ore states BEFORE oreharvester breaks them
                var stack = serverPlayer.getMainHandStack();
                Optional<net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment>> enchantmentRegistry = 
                    serverWorld.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                if (enchantmentRegistry.isPresent()) {
                    RegistryEntry<net.minecraft.enchantment.Enchantment> entry = enchantmentRegistry.get()
                        .getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
                    if (entry != null && EnchantmentHelper.getLevel(entry, stack) > 0) {
                        // Player has area mine - check if this is an ore
                        boolean isOre = blockId.contains("_ore") || 
                                       (blockId.contains("deepslate") && (blockId.contains("ore") || blockId.contains("_ore")));
                        if (isOre) {
                            // CRITICAL: oreharvester bypasses PlayerBlockBreakEvents.AFTER, so we need to handle ores differently
                            // CRITICAL: We MUST save block states NOW before oreharvester breaks them!
                            Direction face = getMiningFace(serverPlayer, pos);
                            if (face != null) {
                                
                                // CRITICAL: Pre-save block states BEFORE oreharvester breaks them
                                // We need to get the pattern and save all block states immediately
                                PlayerDataManager.UndoData preSavedUndoData = null;
                                if (AreaEnchantMod.config.enableUndo) {
                                    try {
                                        // Get player data to determine pattern and level
                                        PlayerDataManager.PlayerData playerData = PlayerDataManager.get(serverPlayer.getUuid());
                                        String currentPattern = AreaEnchantMod.config.miningPattern;
                                        int level = EnchantmentHelper.getLevel(entry, stack);
                                        
                                        if (level > 0 && playerData.hasPattern(currentPattern)) {
                                            // Get tier sizes
                                            var sizeConfig = AreaEnchantMod.config.patternLevels.containsKey(currentPattern) ?
                                                AreaEnchantMod.config.patternLevels.get(currentPattern).getOrDefault(level, new AreaEnchantMod.Size(level, level, level)) :
                                                AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
                                            
                                            int horizontalSize = sizeConfig.horizontal;
                                            int verticalSize = sizeConfig.vertical;
                                            int depthSize = sizeConfig.depth;
                                            
                                            // Apply radius boost upgrade
                                            if (playerData.hasUpgrade("radius_boost")) {
                                                horizontalSize++;
                                                verticalSize++;
                                                depthSize++;
                                            }
                                            
                                            // Get blocks to mine
                                            List<BlockPos> blocksToMine = MiningPattern.getBlocksToMine(
                                                currentPattern, pos, face, horizontalSize, verticalSize, depthSize);
                                            
                                            // Save ALL block states immediately (before oreharvester breaks them)
                                            preSavedUndoData = new PlayerDataManager.UndoData();
                                            
                                            // CRITICAL: Track player's inventory state BEFORE breaking blocks
                                            for (int i = 0; i < serverPlayer.getInventory().size(); i++) {
                                                ItemStack invStack = serverPlayer.getInventory().getStack(i);
                                                if (!invStack.isEmpty()) {
                                                    String itemId = Registries.ITEM.getId(invStack.getItem()).toString();
                                                    preSavedUndoData.inventoryBefore.put(itemId, preSavedUndoData.inventoryBefore.getOrDefault(itemId, 0) + invStack.getCount());
                                                }
                                            }
                                            
                                            preSavedUndoData.addBlock(pos, state); // Center block
                                            
                                            // CRITICAL: Find ALL connected ores of the same type (even outside pattern)
                                            // This ensures we can undo ores broken by oreharvester outside the pattern
                                            java.util.Set<BlockPos> patternBlocksSet = new java.util.HashSet<>(blocksToMine);
                                            java.util.Set<BlockPos> allConnectedOres = findConnectedOres(
                                                serverWorld, pos, state.getBlock(), patternBlocksSet
                                            );
                                            
                                            // Save ALL connected ore states (including those outside pattern)
                                            for (BlockPos orePos : allConnectedOres) {
                                                if (!orePos.equals(pos)) { // Skip center (already added)
                                                    BlockState oreState = serverWorld.getBlockState(orePos);
                                                    if (!oreState.isAir() && oreState.getBlock() == state.getBlock()) {
                                                        preSavedUndoData.addBlock(orePos, oreState);
                                                    }
                                                }
                                            }
                                            
                                            // Also save pattern blocks that aren't ores (for regular blocks in pattern)
                                            for (BlockPos blockPos : blocksToMine) {
                                                if (!blockPos.equals(pos) && !allConnectedOres.contains(blockPos)) {
                                                    BlockState blockState = serverWorld.getBlockState(blockPos);
                                                    if (!blockState.isAir()) {
                                                        preSavedUndoData.addBlock(blockPos, blockState);
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                
                                pendingOreBreaks.put(serverPlayer.getUuid(), new PendingOreBreak(serverPlayer, serverWorld, pos, state, face, serverWorld.getTime(), preSavedUndoData));
                                // Also save mining face for AFTER event (in case it fires)
                                playerMiningFaces.put(player.getUuid(), face);
                            }
                        }
                    }
                }
                
                // Try to get the face from the player's look direction (only if not already set for ores)
                if (!pendingOreBreaks.containsKey(player.getUuid())) {
                    Direction face = getMiningFace(serverPlayer, pos);
                    if (face != null) {
                        playerMiningFaces.put(player.getUuid(), face);
                    }
                }
            }
            return true; // Allow the break to continue
        });
        
        // Handle area mining after block is broken
        // CRITICAL: This event must be registered SECOND to handle area mining after the block is broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            // CRITICAL: Log ALL block breaks to see if AFTER event fires at all
            String brokenBlockId = Registries.BLOCK.getId(state.getBlock()).toString();
            boolean isOre = brokenBlockId.contains("_ore") || 
                           (brokenBlockId.contains("deepslate") && (brokenBlockId.contains("ore") || brokenBlockId.contains("_ore")));
            
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
                return;
            }
            
            // CRITICAL: Check if we're already breaking an area FIRST - before any other checks
            // This prevents batched breaking from triggering new area mine events
            // This is the MOST IMPORTANT check to prevent recursion
            // CRITICAL: This also prevents oreharvester from breaking connected ores and triggering new area mine events
            // that would overwrite undo data
            UUID playerId = player.getUuid();
            boolean alreadyBreaking = isBreakingArea.getOrDefault(playerId, false);
            if (alreadyBreaking) {
                // Clean up mining face if it exists (it shouldn't, but just in case)
                playerMiningFaces.remove(playerId);
                // Also clear pending ore break if it exists (shouldn't happen, but safety)
                pendingOreBreaks.remove(playerId);
                return;
            }
            
            // CRITICAL: If this is an ore and we have a pending ore break, clear it
            // The AFTER event fired, so we don't need the pending break anymore
            if (isOre && pendingOreBreaks.containsKey(playerId)) {
                pendingOreBreaks.remove(playerId);
            }
            
            // CRITICAL: Check if the broken block was bedrock - if so, don't do area mining
            if (brokenBlockId.equals("minecraft:bedrock") || state.getHardness(serverWorld, pos) < 0) {
                playerMiningFaces.remove(player.getUuid()); // Clean up
                return;
            }
            
            Direction miningFace = playerMiningFaces.remove(player.getUuid());
            if (miningFace == null) {
                return;
            }
            
            // CRITICAL: Pass the original block state so we can add it to undo data
            // The block is already broken, so world.getBlockState(pos) would return air
            handleAreaMining(serverPlayer, serverWorld, pos, miningFace, state);
        });
    }
    
    private static Direction getMiningFace(ServerPlayerEntity player, BlockPos pos) {
        // Convert yaw/pitch to direction
        // This is a simplified approach - we'll use the block position relative to player
        BlockPos playerPos = player.getBlockPos();
        int dx = pos.getX() - playerPos.getX();
        int dy = pos.getY() - playerPos.getY();
        int dz = pos.getZ() - playerPos.getZ();
        
        // Find the dominant axis
        int absX = Math.abs(dx);
        int absY = Math.abs(dy);
        int absZ = Math.abs(dz);
        
        if (absX >= absY && absX >= absZ) {
            return dx > 0 ? Direction.EAST : Direction.WEST;
        } else if (absY >= absX && absY >= absZ) {
            return dy > 0 ? Direction.UP : Direction.DOWN;
        } else {
            return dz > 0 ? Direction.SOUTH : Direction.NORTH;
        }
    }
    
    private static void handleAreaMining(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction miningFace, BlockState originalCenterBlockState) {
        UUID playerId = player.getUuid();
        String centerBlockId = Registries.BLOCK.getId(originalCenterBlockState.getBlock()).toString();
        boolean isOre = centerBlockId.contains("_ore") || 
                       (centerBlockId.contains("deepslate") && (centerBlockId.contains("ore") || centerBlockId.contains("_ore")));
        
        // CRITICAL: Check if we're already breaking an area FIRST, before any other checks
        // This is a redundant check (we already check in AFTER event), but extra safety
        // This must be the very first check to prevent recursive calls
        boolean alreadyBreaking = isBreakingArea.getOrDefault(playerId, false);
        if (alreadyBreaking) {
            return;
        }
        
        // Safety check: If the original block is bedrock or unbreakable, don't do area mining
        BlockState originalState = world.getBlockState(pos);
        String originalBlockId = Registries.BLOCK.getId(originalState.getBlock()).toString();
        if (originalState.getHardness(world, pos) < 0 || 
            originalBlockId.equals("minecraft:bedrock") ||
            AreaEnchantMod.config.blockBlacklist.contains(originalBlockId)) {
            return;
        }
        
        var stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            return;
        }
        
        Optional<net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment>> enchantmentRegistry = 
            world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
        if (enchantmentRegistry.isEmpty()) {
            return;
        }
        
        RegistryEntry<net.minecraft.enchantment.Enchantment> entry = enchantmentRegistry.get()
            .getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
        if (entry == null) {
            return;
        }
        
        int level = EnchantmentHelper.getLevel(entry, stack);
        if (level <= 0) {
            return;
        }
        
        // Check if crouch is required
        if (AreaEnchantMod.config.requireCrouch && !player.isSneaking()) {
            return;
        }
        
        // Get player data
        PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
        playerData.ensureDefaultPattern();
        
        if (playerData.isDisabled()) {
            return;
        }
        
        // Check pattern unlock
        String currentPattern = AreaEnchantMod.config.miningPattern;
        if (!playerData.hasPattern(currentPattern)) {
            player.sendMessage(Text.literal("§c[Area Mine] You haven't unlocked the " + currentPattern + " pattern! Use /areamine patterns"), false);
            return;
        }
        
        // Check cooldown
        if (AreaEnchantMod.config.cooldownTicks > 0) {
            long currentTick = world.getTime();
            if (playerData.isOnCooldown(currentTick, AreaEnchantMod.config.cooldownTicks)) {
                if (AreaEnchantMod.config.actionBarFeedback) {
                    long ticksRemaining = AreaEnchantMod.config.cooldownTicks - (currentTick - playerData.getLastUseTick());
                    double secondsRemaining = ticksRemaining / 20.0;
                    player.sendMessage(Text.literal(String.format("§e[Area Mine] On cooldown: %.1fs remaining", secondsRemaining)), false);
                }
                return;
            }
            playerData.updateLastUse(currentTick);
        } else {
            playerData.updateLastUse(world.getTime());
        }
        
        // Get tier sizes
        var sizeConfig = AreaEnchantMod.config.patternLevels.containsKey(currentPattern) ?
            AreaEnchantMod.config.patternLevels.get(currentPattern).getOrDefault(level, new AreaEnchantMod.Size(level, level, level)) :
            AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
        
        int horizontalSize = sizeConfig.horizontal;
        int verticalSize = sizeConfig.vertical;
        int depthSize = sizeConfig.depth;
        
        // Apply radius boost upgrade
        if (playerData.hasUpgrade("radius_boost")) {
            horizontalSize++;
            verticalSize++;
            depthSize++;
        }
        
        // Get blocks to mine - ONLY blocks within the pattern, nothing else
        List<BlockPos> blocksToMine = MiningPattern.getBlocksToMine(
            currentPattern,
            pos,
            miningFace,
            horizontalSize,
            verticalSize,
            depthSize
        );
        
        // CRITICAL: Verify blocks are within the pattern bounds - no ore connection logic should add blocks
        // The pattern should ONLY contain blocks within the specified dimensions
        java.util.Set<BlockPos> blocksToMineSet = new java.util.HashSet<>(blocksToMine);
        if (blocksToMine.size() != blocksToMineSet.size()) {
            // Duplicate blocks filtered out
        }
        
        // Filter blocks
        BlockState originalBlock = world.getBlockState(pos);
        List<BlockPos> filteredBlocks = new ArrayList<>();
        
        
        int skippedCenter = 0;
        int skippedAir = 0;
        int skippedBlacklist = 0;
        int skippedWhitelist = 0;
        int skippedFilter = 0;
        int skippedVeinMining = 0;
        int added = 0;
        
        for (BlockPos blockPos : blocksToMine) {
            BlockState blockState = world.getBlockState(blockPos);
            
            // CRITICAL: Skip the original block (already broken)
            // This is the center block that triggered the area mine
            // We don't want to break it again, and we don't want it in filteredBlocks
            if (blockPos.equals(pos)) {
                skippedCenter++;
                continue;
            }
            
            // Skip air (but center block might be air, which is fine)
            if (blockState.isAir()) {
                skippedAir++;
                continue;
            }
            
            // Check blacklist/whitelist
            String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
            
            // CRITICAL: Check for unbreakable blocks (bedrock has hardness -1.0f) FIRST
            // This must be checked before any other filtering to prevent bedrock from being broken
            float hardness = blockState.getHardness(world, blockPos);
            if (hardness < 0 || blockId.equals("minecraft:bedrock")) {
                continue;
            }
            
            // Check blacklist (bedrock should be in here too, but double-check)
            if (AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                skippedBlacklist++;
                continue;
            }
            
            if (!AreaEnchantMod.config.blockWhitelist.isEmpty()) {
                if (!AreaEnchantMod.config.blockWhitelist.contains(blockId)) {
                    skippedWhitelist++;
                    continue;
                }
            }
            
            // Apply block filters (pickaxe-effective, ores-only, stone-only, etc.)
            // BUT: Always allow ores (including deepslate ores) regardless of other filters
            // Check for ores: contains "_ore" OR is a deepslate ore variant
            boolean isBlockOre = blockId.contains("_ore") || 
                           (blockId.contains("deepslate") && (blockId.contains("ore") || blockId.contains("_ore")));
            
            // Check if it's a common mining block that should always be allowed
            boolean isCommonMiningBlock = blockId.contains("stone") || 
                                          blockId.contains("deepslate") ||
                                          blockId.contains("cobblestone") ||
                                          blockId.contains("netherrack") ||
                                          blockId.contains("basalt") ||
                                          blockId.contains("blackstone") ||
                                          blockId.contains("end_stone") ||
                                          blockId.contains("obsidian");
            
            if (!isBlockOre && !isCommonMiningBlock && !BlockFilter.shouldMineBlock(blockState, AreaEnchantMod.config)) {
                // For other blocks, apply normal filters
                skippedFilter++;
                continue;
            }
            
            // Vein mining check - ONLY apply if veinMiningMode is enabled
            // IMPORTANT: This should NOT affect ores or blocks outside the pattern
            // The pattern already defines the area, so vein mining just filters by type within that area
            if (AreaEnchantMod.config.veinMiningMode && !originalBlock.isAir()) {
                String originalId = Registries.BLOCK.getId(originalBlock.getBlock()).toString();
                if (!blockId.equals(originalId)) {
                    skippedVeinMining++;
                    continue;
                }
            }
            
            filteredBlocks.add(blockPos);
            added++;
        }
        
        if (filteredBlocks.isEmpty()) {
            return;
        }
        
        // CRITICAL: Set breaking flag IMMEDIATELY before any block breaking operations
        // This must be set BEFORE calling BatchedBlockBreaker.startBreaking() to prevent recursion
        // Note: playerId is already defined at the start of this method
        isBreakingArea.put(playerId, true);
        
        // Initialize undo data BEFORE breaking blocks
        PlayerDataManager.UndoData undoData = new PlayerDataManager.UndoData();
        
        // CRITICAL: Track player's inventory state BEFORE breaking blocks
        // This allows us to restore inventory to the exact state it was before
        if (AreaEnchantMod.config.enableUndo) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack invStack = player.getInventory().getStack(i);
                if (!invStack.isEmpty()) {
                    String itemId = Registries.ITEM.getId(invStack.getItem()).toString();
                    undoData.inventoryBefore.put(itemId, undoData.inventoryBefore.getOrDefault(itemId, 0) + invStack.getCount());
                }
            }
        }
        
        // CRITICAL: Pre-add all blocks to undo data BEFORE storing it
        // This ensures all blocks are tracked even if breaking fails partway through
        // CRITICAL: Add the center block FIRST (it was already broken, so we use the original state)
        if (AreaEnchantMod.config.enableUndo) {
            // Add the center block (the one that was just broken) - use original state from BEFORE event
            undoData.addBlock(pos, originalCenterBlockState);
            
            // CRITICAL: For ores, we need to save states RIGHT NOW before any other mods can modify them
            // Ore vein mods (like oreharvester) might process PlayerBlockBreakEvents and modify/break connected ores
            // By saving states immediately here, we capture them before other mods can interfere
            int oreCount = 0;
            int regularBlockCount = 0;
            int alreadyBrokenOres = 0;
            for (BlockPos blockPos : filteredBlocks) {
                // CRITICAL: Get the block state synchronously on the server thread RIGHT NOW
                // This ensures we get the actual state at this moment, before any other mods process events
                // We MUST do this before any block breaking starts to prevent ore vein mods from modifying blocks
                BlockState blockState = world.getBlockState(blockPos);
                String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                
                // Track ore vs regular blocks for debugging
                boolean isBlockOre = blockId.contains("_ore") || 
                               (blockId.contains("deepslate") && (blockId.contains("ore") || blockId.contains("_ore")));
                
                // CRITICAL: Check if ore is already broken (oreharvester might have broken it)
                if (isBlockOre && blockState.isAir()) {
                    alreadyBrokenOres++;
                    // Still save it (as air) so undo doesn't fail
                } else if (isBlockOre) {
                    oreCount++;
                } else {
                    regularBlockCount++;
                }
                
                // CRITICAL: For ores, we need to capture the EXACT state including all properties
                // Some ore vein mods might change block states or break connected ores
                // By saving immediately, we preserve the state before any modifications
                
                // Add ALL blocks to undo data with their current state
                // This is critical for ores - we need to preserve the exact block state
                undoData.addBlock(blockPos, blockState);
            }
        }
        
        // CRITICAL: Save undo data to separate storage IMMEDIATELY so it persists
        // This ensures undo data is available even if player data is reloaded
        // IMPORTANT: Only save if we have blocks to mine, and don't overwrite existing undo data
        // CRITICAL: For ores, check if we already have pre-saved undo data (from BEFORE event)
        if (AreaEnchantMod.config.enableUndo) {
            PlayerDataManager.UndoData existingUndo = PlayerDataManager.getUndoData(player.getUuid());
            
            // If we have pre-saved undo data (from BEFORE event for ores), use it instead
            if (isOre && existingUndo != null && !existingUndo.blocks.isEmpty()) {
                undoData = existingUndo;
            } else if (!undoData.blocks.isEmpty()) {
                // Check if undo data already exists - if so, don't overwrite it (prevents recursive calls from clearing it)
                // CRITICAL: This check prevents ore vein mods from triggering new area mine events that overwrite undo data
                if (existingUndo != null && !existingUndo.blocks.isEmpty()) {
                    // CRITICAL: Don't overwrite existing undo data - use it instead
                    // This prevents ore vein mods from clearing undo data when they break connected ores
                    undoData = existingUndo;
                } else {
                    // Store in separate storage (NOT in PlayerData, so it persists across cache clears)
                    PlayerDataManager.setUndoData(player.getUuid(), undoData);
                }
            }
        }
        
        // Use batched breaking if enabled
        if (AreaEnchantMod.config.batchedBreaking) {
            // CRITICAL: Keep isBreakingArea=true until batched breaking completes
            // This prevents block break events from batched breaking from triggering new area mine events
            BatchedBlockBreaker.startBreaking(player, world, filteredBlocks, stack, level, undoData);
            // NOTE: isBreakingArea will be cleared in BatchedBlockBreaker.finish() to prevent re-entry
            // Note: Undo data will be saved in BatchedBlockBreaker.finish()
            return;
        }
        
        // Instant breaking
        int blocksMined = 0;
        boolean autoPickup = AreaEnchantMod.config.autoPickup || playerData.hasUpgrade("auto_pickup");
        
        // CRITICAL: Only break blocks that are in filteredBlocks - nothing else
        // This ensures we NEVER break blocks outside the pattern, even if they're connected ores
        for (BlockPos otherPos : filteredBlocks) {
            // Verify this block is actually in our filtered list (safety check)
            if (!filteredBlocks.contains(otherPos)) {
                continue;
            }
            
            BlockState blockState = world.getBlockState(otherPos);
            // Skip air blocks (but center block might be air, which is already handled above)
            if (blockState.isAir()) {
                continue;
            }
            
            // CRITICAL: For instant breaking, re-check block state RIGHT BEFORE breaking
            // This is especially important for ores - ore vein mods might have modified the block
            // between when we saved it and now
            if (AreaEnchantMod.config.enableUndo && undoData != null) {
                // Check if block is in undo data
                boolean alreadyAdded = undoData.blocks.stream().anyMatch(b -> b.pos.equals(otherPos));
                if (!alreadyAdded) {
                    // Shouldn't happen, but add it as backup
                    undoData.addBlock(otherPos, blockState);
                } else {
                    // For ores, update the saved state to match current state
                    // Ore vein mods might have modified the block after we saved it
                    String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                    boolean isBlockOre = blockId.contains("_ore") || 
                                   (blockId.contains("deepslate") && (blockId.contains("ore") || blockId.contains("_ore")));
                    
                    if (isBlockOre) {
                        // Re-check state right before breaking to ensure we have the correct state
                        BlockState currentState = world.getBlockState(otherPos);
                        undoData.blocks.stream()
                            .filter(b -> b.pos.equals(otherPos))
                            .findFirst()
                            .ifPresent(b -> b.state = currentState);
                    }
                }
            }
            
            // Get drops
            List<ItemStack> drops = new ArrayList<>();
            if (!player.isCreative()) {
                drops.addAll(Block.getDroppedStacks(blockState, world, otherPos,
                    world.getBlockEntity(otherPos), player, stack));
            }
            
            // Final safety check: Never break unbreakable blocks (bedrock, etc.)
            // This is a redundant check since we already filtered, but extra safety
            String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
            float hardness = blockState.getHardness(world, otherPos);
            if (hardness < 0 || blockId.equals("minecraft:bedrock") || 
                AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                continue; // Skip unbreakable blocks
            }
            
            // CRITICAL: Break block using setBlockState with air to avoid triggering block break events
            // This is the safest way to remove blocks without triggering PlayerBlockBreakEvents
            // Flag 3 = NOTIFY_NEIGHBORS | NOTIFY_LISTENERS (but won't trigger PlayerBlockBreakEvents)
            BlockState stateBeforeBreak = world.getBlockState(otherPos);
            boolean broken = false;
            
            // Verify the block is still there and matches what we expect
            if (!stateBeforeBreak.isAir() && Registries.BLOCK.getId(stateBeforeBreak.getBlock()).toString().equals(blockId)) {
                // Set block to air directly (flag 3 = notify neighbors and listeners, but won't trigger PlayerBlockBreakEvents)
                world.setBlockState(otherPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                
                // Verify it's actually removed
                BlockState stateAfterBreak = world.getBlockState(otherPos);
                if (stateAfterBreak.isAir()) {
                    broken = true;
                }
            } else if (stateBeforeBreak.isAir()) {
                broken = false;
            }
            
            if (broken) {
                // Trigger block break events and drops manually
                blockState.onStacksDropped(world, otherPos, stack, !player.isCreative());
                
                blocksMined++;
                
                // Handle drops
                if (!player.isCreative()) {
                    for (ItemStack drop : drops) {
                        if (autoPickup) {
                            ItemStack originalDrop = drop.copy();
                            int originalCount = originalDrop.getCount();
                            
                            // Try to insert - this modifies drop in place
                            boolean fullyInserted = player.getInventory().insertStack(drop);
                            
                            if (fullyInserted) {
                                // All items were added to inventory
                                if (AreaEnchantMod.config.enableUndo) {
                                    String itemId = Registries.ITEM.getId(originalDrop.getItem()).toString();
                                    undoData.addInventoryItem(itemId, originalCount);
                                }
                            } else {
                                // Some or all items couldn't fit - drop the remainder
                                int insertedCount = originalCount - drop.getCount();
                                int droppedCount = drop.getCount();
                                
                                // Track what was inserted
                                if (insertedCount > 0 && AreaEnchantMod.config.enableUndo) {
                                    String itemId = Registries.ITEM.getId(originalDrop.getItem()).toString();
                                    undoData.addInventoryItem(itemId, insertedCount);
                                }
                                
                                // Drop the remainder
                                if (droppedCount > 0) {
                                    ItemEntity itemEntity = new ItemEntity(world,
                                        otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                                    world.spawnEntity(itemEntity);
                                    // Track dropped item entity for undo
                                    if (AreaEnchantMod.config.enableUndo) {
                                        undoData.addItemEntity(itemEntity.getUuid());
                                    }
                                }
                            }
                        } else {
                            // Drop item in world
                            ItemEntity itemEntity = new ItemEntity(world,
                                otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                            world.spawnEntity(itemEntity);
                            // Track dropped item entity for undo
                            if (AreaEnchantMod.config.enableUndo) {
                                undoData.addItemEntity(itemEntity.getUuid());
                            }
                        }
                    }
                }
                // NOTE: blocksMined is already incremented above, no need to increment again
            }
        }
        
        // Apply durability cost
        if (AreaEnchantMod.config.durabilityScaling && !player.isCreative() && blocksMined > 0) {
            double durabilityMultiplier = getDurabilityMultiplier(level);
            int unbreakingLevel = EnchantmentHelper.getLevel(
                enchantmentRegistry.get().getEntry(Identifier.of("minecraft:unbreaking")).orElse(null),
                stack);
            if (unbreakingLevel > 0) {
                durabilityMultiplier *= (1.0 - (unbreakingLevel * AreaEnchantMod.config.unbreakingDurabilityReduction));
            }
            int durabilityCost = (int) Math.ceil(blocksMined * durabilityMultiplier);
            stack.damage(durabilityCost, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
        }
        
        // Final save of undo data after all blocks are broken (only for instant breaking, batched breaking saves in finish())
        if (AreaEnchantMod.config.enableUndo && !undoData.blocks.isEmpty()) {
            // Save to separate storage (NOT in PlayerData)
            PlayerDataManager.setUndoData(player.getUuid(), undoData);
        }
        
        // Clear breaking flag
        isBreakingArea.put(player.getUuid(), false);
    }
    
    /**
     * Process pending ore breaks that were scheduled because oreharvester bypasses AFTER event.
     * This is called every server tick to check if ores were broken and need area mining.
     */
    public static void processPendingOreBreaks() {
        if (pendingOreBreaks.isEmpty()) {
            return;
        }
        
        // Process all pending ore breaks
        List<UUID> toRemove = new ArrayList<>();
        long currentTick = -1; // Will be set from first pending break's world
        
        for (Map.Entry<UUID, PendingOreBreak> entry : pendingOreBreaks.entrySet()) {
            UUID playerId = entry.getKey();
            PendingOreBreak pending = entry.getValue();
            
            // Validate player and world references (they might be invalid if player disconnected)
            if (pending.player == null || pending.world == null || !pending.player.isAlive()) {
                toRemove.add(playerId);
                continue;
            }
            
            // Get current tick from world
            if (currentTick == -1) {
                currentTick = pending.world.getTime();
            }
            
            // Timeout: Remove pending breaks older than 5 ticks (250ms) to prevent memory leaks
            if (currentTick - pending.scheduledTick > 5) {
                toRemove.add(playerId);
                continue;
            }
            
            // Check if we're already breaking an area (shouldn't happen, but safety check)
            if (isBreakingArea.getOrDefault(playerId, false)) {
                toRemove.add(playerId);
                continue;
            }
            
            // Check if the block was actually broken (is air now)
            BlockState currentState = pending.world.getBlockState(pending.pos);
            if (currentState.isAir()) {
                // Block was broken - process area mining
                // CRITICAL: If we have pre-saved undo data, save it NOW before handleAreaMining
                // (oreharvester already broke all the ores, so we can't save them in handleAreaMining)
                if (pending.preSavedUndoData != null && !pending.preSavedUndoData.blocks.isEmpty()) {
                    // CRITICAL: Track item entities dropped by oreharvester near broken block positions
                    // This prevents duplication when undoing ore mining
                    trackOreItemEntities(pending.preSavedUndoData, pending.world, pending.player);
                    
                    PlayerDataManager.setUndoData(pending.player.getUuid(), pending.preSavedUndoData);
                }
                
                handleAreaMining(pending.player, pending.world, pending.pos, pending.miningFace, pending.originalState);
                toRemove.add(playerId);
            } else {
                // Block still exists - check if it's been more than 1 tick (shouldn't take that long)
                // Actually, let's just keep it for one more tick in case oreharvester is still processing
                // But remove it after a few ticks to prevent memory leaks
                // For now, we'll process it immediately if the block state changed (oreharvester might have modified it)
                String currentBlockId = Registries.BLOCK.getId(currentState.getBlock()).toString();
                String originalBlockId = Registries.BLOCK.getId(pending.originalState.getBlock()).toString();
                if (!currentBlockId.equals(originalBlockId)) {
                    // Block changed - process it
                    // CRITICAL: Track item entities if we have pre-saved undo data
                    // Use the same optimized tracking logic as above
                    if (pending.preSavedUndoData != null && !pending.preSavedUndoData.blocks.isEmpty()) {
                        // Reuse the same optimized item tracking logic (extracted to avoid duplication)
                        trackOreItemEntities(pending.preSavedUndoData, pending.world, pending.player);
                        PlayerDataManager.setUndoData(pending.player.getUuid(), pending.preSavedUndoData);
                    }
                    
                    handleAreaMining(pending.player, pending.world, pending.pos, pending.miningFace, pending.originalState);
                    toRemove.add(playerId);
                }
            }
        }
        
        // Remove processed entries
        for (UUID playerId : toRemove) {
            pendingOreBreaks.remove(playerId);
        }
    }
    
    /**
     * Clear pending ore breaks for a player (called on disconnect).
     */
    public static void clearPendingOreBreaks(UUID playerId) {
        pendingOreBreaks.remove(playerId);
    }
    
    /**
     * Track item entities dropped by oreharvester near broken block positions.
     * This prevents duplication when undoing ore mining.
     * Also tracks what items SHOULD have been dropped, so we can remove them from inventory if they were picked up.
     */
    private static void trackOreItemEntities(PlayerDataManager.UndoData undoData, ServerWorld world, ServerPlayerEntity player) {
        int trackedItems = 0;
        
        // CRITICAL: Calculate what items SHOULD have been dropped from all broken blocks
        // This allows us to track items even if they were picked up into inventory
        java.util.Map<String, Integer> expectedDrops = new java.util.HashMap<>(); // item ID -> total count
        
        // Pre-compute all possible drop items for all blocks to avoid repeated getDroppedStacks calls
        java.util.Set<net.minecraft.item.Item> allPossibleDropItems = new java.util.HashSet<>();
        java.util.Map<BlockPos, java.util.Set<net.minecraft.item.Item>> blockDropItems = new java.util.HashMap<>();
        
        for (PlayerDataManager.UndoData.BlockStateData blockData : undoData.blocks) {
            net.minecraft.block.Block block = blockData.state.getBlock();
            net.minecraft.item.Item blockItem = block.asItem();
            
            if (blockItem != null && !blockItem.equals(net.minecraft.item.Items.AIR)) {
                // Get all possible drops for this block (only compute once per block)
                java.util.Set<net.minecraft.item.Item> drops = new java.util.HashSet<>();
                drops.add(blockItem); // Add the block item itself
                
                // Get actual drops (e.g., redstone from redstone ore)
                try {
                    // Block is already broken, so blockEntity will be null - that's fine
                    List<ItemStack> droppedStacks = Block.getDroppedStacks(
                        blockData.state, world, blockData.pos, 
                        null, // Block entity is null since block is already broken
                        player, player.getMainHandStack()
                    );
                    for (ItemStack drop : droppedStacks) {
                        drops.add(drop.getItem());
                        // Track expected drops with counts
                        String itemId = Registries.ITEM.getId(drop.getItem()).toString();
                        expectedDrops.put(itemId, expectedDrops.getOrDefault(itemId, 0) + drop.getCount());
                    }
                } catch (Exception e) {
                    // If getDroppedStacks fails, just use the block item
                }
                
                blockDropItems.put(blockData.pos, drops);
                allPossibleDropItems.addAll(drops);
            }
        }
        
        // Search for item entities near all broken block positions
        // Use a larger search box that encompasses all broken blocks to find all items at once
        if (!blockDropItems.isEmpty()) {
            // Calculate bounding box for all broken blocks
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blockDropItems.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            
            // Search within 3 blocks of the bounding box
            net.minecraft.util.math.Box searchBox = new net.minecraft.util.math.Box(
                minX - 3, minY - 3, minZ - 3,
                maxX + 3, maxY + 3, maxZ + 3
            );
            
            // Find all item entities in the search area that match possible drops
            List<net.minecraft.entity.ItemEntity> allNearbyItems = world.getEntitiesByClass(
                net.minecraft.entity.ItemEntity.class, searchBox, 
                itemEntity -> allPossibleDropItems.contains(itemEntity.getStack().getItem())
            );
            
            // Track how many items we found as item entities (by item type)
            java.util.Map<String, Integer> foundItemEntities = new java.util.HashMap<>(); // item ID -> total count found
            
            // Match each item to the nearest broken block position
            for (net.minecraft.entity.ItemEntity itemEntity : allNearbyItems) {
                if (undoData.itemEntities.contains(itemEntity.getUuid())) {
                    continue; // Already tracked
                }
                
                // Find the nearest broken block that could have dropped this item
                BlockPos nearestPos = null;
                double nearestDist = Double.MAX_VALUE;
                
                // Get item entity position
                double itemX = itemEntity.getX();
                double itemY = itemEntity.getY();
                double itemZ = itemEntity.getZ();
                
                for (Map.Entry<BlockPos, java.util.Set<net.minecraft.item.Item>> dropEntry : blockDropItems.entrySet()) {
                    if (dropEntry.getValue().contains(itemEntity.getStack().getItem())) {
                        // Calculate distance from item to block center
                        double dx = itemX - (dropEntry.getKey().getX() + 0.5);
                        double dy = itemY - (dropEntry.getKey().getY() + 0.5);
                        double dz = itemZ - (dropEntry.getKey().getZ() + 0.5);
                        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                        if (dist < nearestDist && dist <= 3.5) { // Within 3 blocks (with some tolerance)
                            nearestDist = dist;
                            nearestPos = dropEntry.getKey();
                        }
                    }
                }
                
                if (nearestPos != null) {
                    undoData.addItemEntity(itemEntity.getUuid());
                    trackedItems++;
                    
                    // Track how many of this item type we found
                    String itemId = Registries.ITEM.getId(itemEntity.getStack().getItem()).toString();
                    foundItemEntities.put(itemId, foundItemEntities.getOrDefault(itemId, 0) + itemEntity.getStack().getCount());
                }
            }
            
            // CRITICAL: Track ALL expected items as inventory items that need to be removed
            // This ensures we remove the full expected amount, regardless of whether items are on ground or in inventory
            // When undoing, we'll remove item entities first, then remove from inventory up to the expected total
            for (Map.Entry<String, Integer> expectedEntry : expectedDrops.entrySet()) {
                String itemId = expectedEntry.getKey();
                int expectedCount = expectedEntry.getValue();
                int foundCount = foundItemEntities.getOrDefault(itemId, 0);
                
                // CRITICAL: Always track the FULL expected amount as inventory items
                // This ensures we can remove all items, even if some are on ground and some are in inventory
                // The undo command will handle removing from both sources correctly
                undoData.addInventoryItem(itemId, expectedCount);
            }
        }
    }
    
    /**
     * Find all connected ores of the same type using flood-fill algorithm.
     * This includes ores both inside and outside the pattern, which is critical
     * for compatibility with ore harvester mods that break connected ores.
     * 
     * @param world The server world
     * @param startPos The starting position (center block)
     * @param targetBlock The block type to search for (must match)
     * @param patternBlocks Set of blocks within the pattern (for reference, but we search beyond it)
     * @return Set of all connected block positions of the same type
     */
    private static java.util.Set<BlockPos> findConnectedOres(
            ServerWorld world, BlockPos startPos, net.minecraft.block.Block targetBlock, 
            java.util.Set<BlockPos> patternBlocks) {
        java.util.Set<BlockPos> connected = new java.util.HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.LinkedList<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        
        // Start from the center position
        queue.add(startPos);
        visited.add(startPos);
        
        // Maximum search radius to prevent infinite loops (reasonable limit for ore veins)
        // Most ore veins are within 50 blocks, but we'll use a larger limit for safety
        int maxRadius = 100;
        int startX = startPos.getX();
        int startY = startPos.getY();
        int startZ = startPos.getZ();
        
        // Flood-fill to find all connected blocks of the same type
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            connected.add(current);
            
            // Check all 6 neighbors (up, down, north, south, east, west)
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.offset(dir);
                
                // Skip if already visited
                if (visited.contains(neighbor)) {
                    continue;
                }
                
                // Check if within reasonable distance (prevent infinite search)
                int dx = Math.abs(neighbor.getX() - startX);
                int dy = Math.abs(neighbor.getY() - startY);
                int dz = Math.abs(neighbor.getZ() - startZ);
                if (dx > maxRadius || dy > maxRadius || dz > maxRadius) {
                    continue;
                }
                
                // Check if neighbor is the same block type
                BlockState neighborState = world.getBlockState(neighbor);
                if (neighborState.getBlock() == targetBlock && !neighborState.isAir()) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        
        return connected;
    }
    
    private static double getDurabilityMultiplier(int level) {
        return switch (level) {
            case 1 -> 1.0;
            case 2 -> 1.5;
            case 3 -> 2.0;
            default -> 1.0;
        };
    }
}

