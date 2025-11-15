package net.xai.area_enchant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class BatchedBlockBreaker {
    private static final Map<UUID, BreakingTask> activeTasks = new HashMap<>();
    
    public static void startBreaking(ServerPlayerEntity player, ServerWorld world, List<BlockPos> blocks, 
                                    ItemStack tool, int level, PlayerDataManager.UndoData undoData) {
        // CRITICAL: Ensure flag is set BEFORE cancelling old task
        // This prevents race conditions where block break events fire between cancel and start
        // The flag should already be set by handleAreaMining(), but ensure it's set here too
        if (!AreaMineHandler.isBreakingArea(player.getUuid())) {
            AreaMineHandler.setBreakingArea(player.getUuid(), true);
        }
        
        // Cancel any existing task for this player (but don't clear flag - we're starting a new one)
        BreakingTask oldTask = activeTasks.remove(player.getUuid());
        if (oldTask != null) {
            oldTask.cancel();
            // CRITICAL: Do NOT call finish() here - it would clear the flag
            // Also, we've removed the task from activeTasks, so tick() won't call finish() on it
            // The flag stays set for the new task
            // NOTE: The old task's finish() should NOT be called because it's no longer in activeTasks
        }
        
        // CRITICAL: Only save undo data if it has blocks and doesn't overwrite existing data
        // This prevents recursive calls from clearing valid undo data
        // CRITICAL: This also prevents ore vein mods from breaking connected ores and triggering new area mine events
        // that would overwrite the undo data
        System.out.println("[Area Mine] [BATCHED] startBreaking called with undo data: " + 
            (undoData != null ? undoData.blocks.size() + " blocks" : "null"));
        if (AreaEnchantMod.config.enableUndo && undoData != null && !undoData.blocks.isEmpty()) {
            // Check if undo data already exists - if so, don't overwrite it
            PlayerDataManager.UndoData existingUndo = PlayerDataManager.getUndoData(player.getUuid());
            if (existingUndo != null && !existingUndo.blocks.isEmpty()) {
                System.err.println("[Area Mine] [BATCHED] WARNING: Undo data already exists (" + existingUndo.blocks.size() + 
                    " blocks), NOT overwriting. New data has " + undoData.blocks.size() + " blocks");
                System.err.println("[Area Mine] [BATCHED] WARNING: This might be caused by ore vein mod breaking connected ores!");
                // CRITICAL: Use the existing undo data instead - don't overwrite it
                // This prevents ore vein mods from clearing undo data
                undoData = existingUndo;
            } else {
                System.out.println("[Area Mine] [BATCHED] Saving undo data: " + undoData.blocks.size() + " blocks");
                PlayerDataManager.setUndoData(player.getUuid(), undoData);
            }
        } else {
            System.err.println("[Area Mine] [BATCHED] WARNING: Not saving undo data in startBreaking! enableUndo=" + 
                AreaEnchantMod.config.enableUndo + ", undoData=" + (undoData != null ? undoData.blocks.size() + " blocks" : "null"));
        }
        
        // Create new breaking task
        BreakingTask task = new BreakingTask(player, world, blocks, tool, level, undoData);
        activeTasks.put(player.getUuid(), task);
        
        // Start processing
        task.processNextBatch();
    }
    
    public static void cancelTask(UUID playerId) {
        BreakingTask task = activeTasks.remove(playerId);
        if (task != null) {
            task.cancel();
            // CRITICAL: Clear the flag when cancelling to prevent it from being stuck
            // If a new task starts, it will set the flag again
            AreaMineHandler.clearBreakingFlag(playerId);
        }
    }
    
    public static void tick() {
        List<UUID> toRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, BreakingTask> entry : activeTasks.entrySet()) {
            BreakingTask task = entry.getValue();
            if (task.isFinished()) {
                task.finish();
                toRemove.add(entry.getKey());
            } else {
                task.processNextBatch();
            }
        }
        
        toRemove.forEach(activeTasks::remove);
    }
    
    public static class BreakingTask {
        private final ServerPlayerEntity player;
        private final ServerWorld world;
        private final List<BlockPos> blocks;
        private final ItemStack tool;
        private final int enchantmentLevel;
        private final PlayerDataManager.UndoData undoData;
        private int currentIndex = 0;
        private boolean cancelled = false;
        private int blocksMined = 0;
        private final long startTime;
        private final String dimensionId;
        private final Map<String, Integer> blockCounts = new HashMap<>();
        
        public BreakingTask(ServerPlayerEntity player, ServerWorld world, List<BlockPos> blocks, 
                          ItemStack tool, int enchantmentLevel, PlayerDataManager.UndoData undoData) {
            this.player = player;
            this.world = world;
            this.blocks = blocks;
            this.tool = tool;
            this.enchantmentLevel = enchantmentLevel;
            this.undoData = undoData;
            this.startTime = world.getTime();
            this.dimensionId = world.getRegistryKey().getValue().toString();
        }
        
        public void processNextBatch() {
            if (cancelled) {
                // If cancelled, finish() should have been called by cancelTask()
                // But if we get here, it means tick() found a cancelled task, so finish it
                finish();
                return;
            }
            
            // CRITICAL: Check if we're already done BEFORE processing
            if (currentIndex >= blocks.size()) {
                finish();
                return;
            }
            
            // Continue processing until all blocks are broken or we hit the per-tick limit
            int batchSize = AreaEnchantMod.config.blocksPerTick;
            int processedThisTick = 0;
            
            PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
            
            // Calculate durability multiplier
            double durabilityMultiplier = getDurabilityMultiplier();
            
            // Get Unbreaking level
            int unbreakingLevel = 0;
            var enchantmentRegistry = world.getRegistryManager().getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
            if (enchantmentRegistry.isPresent()) {
                var unbreakingEntry = enchantmentRegistry.get().getEntry(net.minecraft.util.Identifier.of("minecraft:unbreaking"));
                if (unbreakingEntry.isPresent()) {
                    unbreakingLevel = EnchantmentHelper.getLevel(unbreakingEntry.get(), tool);
                }
            }
            
            if (unbreakingLevel > 0) {
                durabilityMultiplier *= (1.0 - (unbreakingLevel * AreaEnchantMod.config.unbreakingDurabilityReduction));
            }
            
            boolean autoPickup = AreaEnchantMod.config.autoPickup || playerData.hasUpgrade("auto_pickup");
            
            // Process blocks until we hit the batch size limit or run out of blocks
            // CRITICAL: Only count successfully broken blocks towards the batch limit
            // This ensures we actually break the intended number of blocks, not just process them
            while (currentIndex < blocks.size() && processedThisTick < batchSize) {
                int i = currentIndex;
                BlockPos otherPos = blocks.get(i);
                BlockState blockState = world.getBlockState(otherPos);
                
                // CRITICAL: Always increment currentIndex to move to next block
                // But only increment processedThisTick if we actually broke a block
                currentIndex++;
                
                if (blockState.isAir()) {
                    continue; // Skip air blocks, don't count towards batch limit
                }
                
                String blockId = net.minecraft.registry.Registries.BLOCK.getId(blockState.getBlock()).toString();
                
                // Final safety check: Never break unbreakable blocks (bedrock, etc.)
                // This is a redundant check since we already filtered, but extra safety
                float hardness = blockState.getHardness(world, otherPos);
                if (hardness < 0 || blockId.equals("minecraft:bedrock") || 
                    AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                    continue; // Skip unbreakable blocks, don't count towards batch limit
                }
                
                // Store for undo if enabled - CRITICAL: Save BEFORE breaking
                // NOTE: Blocks should already be pre-added in AreaMineHandler, but add here as backup
                if (AreaEnchantMod.config.enableUndo && undoData != null) {
                    // Check if block is already in undo data (it should be pre-added)
                    boolean alreadyAdded = undoData.blocks.stream().anyMatch(b -> b.pos.equals(otherPos));
                    if (!alreadyAdded) {
                        // CRITICAL: Get the block state RIGHT BEFORE breaking - this is critical for ores
                        // Ore vein mods might have modified the block state between when we pre-added it and now
                        // By getting the state right before breaking, we ensure we have the correct state
                        // This is especially important if other mods process block break events and modify blocks
                        BlockState stateToSave = world.getBlockState(otherPos);
                        
                        // CRITICAL: For ores, make sure we're getting the full state with all properties
                        // Some ores have properties that need to be preserved (like deepslate_iron_ore vs iron_ore)
                        // If an ore vein mod modified the block, we want to save the CURRENT state, not the old one
                        undoData.addBlock(otherPos, stateToSave);
                    } else {
                        // Block is already in undo data, but CRITICAL: Re-check state if it's an ore
                        // Ore vein mods might have modified the block state after we saved it
                        // For ores, we should update the saved state to match the current state
                        String currentBlockId = net.minecraft.registry.Registries.BLOCK.getId(blockState.getBlock()).toString();
                        boolean isOre = currentBlockId.contains("_ore") || 
                                       (currentBlockId.contains("deepslate") && (currentBlockId.contains("ore") || currentBlockId.contains("_ore")));
                        
                        if (isOre) {
                            // For ores, re-check the state right before breaking
                            // This ensures we have the correct state even if an ore vein mod modified it
                            BlockState currentState = world.getBlockState(otherPos);
                            // Update the saved state if it's different
                            undoData.blocks.stream()
                                .filter(b -> b.pos.equals(otherPos))
                                .findFirst()
                                .ifPresent(b -> b.state = currentState);
                        }
                    }
                }
                
                // Break blocks directly - use breakBlock which properly handles block breaking
                // Get drops with enchantments (Fortune, Silk Touch, etc.) BEFORE breaking
                List<ItemStack> drops = new ArrayList<>();
                if (!player.isCreative()) {
                    drops.addAll(Block.getDroppedStacks(blockState, world, otherPos, 
                        world.getBlockEntity(otherPos), player, tool));
                }
                
                // CRITICAL: Break block using setBlockState with air to avoid triggering block break events
                // This is the safest way to remove blocks without triggering PlayerBlockBreakEvents
                // Flag 3 = NOTIFY_NEIGHBORS | NOTIFY_LISTENERS (but won't trigger PlayerBlockBreakEvents)
                boolean broken = false;
                BlockState stateBeforeBreak = world.getBlockState(otherPos);
                
                // Verify the block is still there and matches what we expect
                if (!stateBeforeBreak.isAir() && net.minecraft.registry.Registries.BLOCK.getId(stateBeforeBreak.getBlock()).toString().equals(blockId)) {
                    // Set block to air directly (flag 3 = notify neighbors and listeners, but won't trigger PlayerBlockBreakEvents)
                    // This is the safest way to remove blocks programmatically
                    world.setBlockState(otherPos, net.minecraft.block.Blocks.AIR.getDefaultState(), 3);
                
                    // Verify it's actually removed
                    BlockState stateAfterBreak = world.getBlockState(otherPos);
                    if (stateAfterBreak.isAir()) {
                        broken = true;
                    } else {
                        System.err.println("[Area Mine] WARNING: setBlockState failed! Block still there: " + 
                            net.minecraft.registry.Registries.BLOCK.getId(stateAfterBreak.getBlock()));
                        broken = false;
                    }
                } else {
                    broken = false;
                }
                
                if (broken) {
                    // Trigger block break events and drops manually
                    blockState.onStacksDropped(world, otherPos, tool, !player.isCreative());
                    
                    blocksMined++;
                    String blockIdStr = blockId;
                    blockCounts.put(blockIdStr, blockCounts.getOrDefault(blockIdStr, 0) + 1);
                    processedThisTick++; // CRITICAL: Only count successfully broken blocks towards batch limit
                    
                    // Handle drops (auto-pickup or drop in world)
                if (!player.isCreative()) {
                    for (ItemStack drop : drops) {
                        if (autoPickup) {
                                ItemStack originalDrop = drop.copy();
                                int originalCount = originalDrop.getCount();
                                
                                // Try to insert - this modifies drop in place
                                boolean fullyInserted = player.getInventory().insertStack(drop);
                                
                                if (fullyInserted) {
                                    // All items were added to inventory
                                    if (AreaEnchantMod.config.enableUndo && undoData != null) {
                                        String itemId = net.minecraft.registry.Registries.ITEM.getId(originalDrop.getItem()).toString();
                                        undoData.addInventoryItem(itemId, originalCount);
                                    }
                                } else {
                                    // Some or all items couldn't fit - drop the remainder
                                    int insertedCount = originalCount - drop.getCount();
                                    int droppedCount = drop.getCount();
                                    
                                    // Track what was inserted
                                    if (insertedCount > 0 && AreaEnchantMod.config.enableUndo && undoData != null) {
                                        String itemId = net.minecraft.registry.Registries.ITEM.getId(originalDrop.getItem()).toString();
                                        undoData.addInventoryItem(itemId, insertedCount);
                                    }
                                    
                                    // Drop the remainder
                                    if (droppedCount > 0) {
                                        net.minecraft.entity.ItemEntity itemEntity = new net.minecraft.entity.ItemEntity(
                                            world, otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                                        world.spawnEntity(itemEntity);
                                        // Track dropped item entity for undo
                                        if (AreaEnchantMod.config.enableUndo && undoData != null) {
                                            undoData.addItemEntity(itemEntity.getUuid());
                                        }
                                    }
                            }
                        } else {
                                // Drop item in world
                                net.minecraft.entity.ItemEntity itemEntity = new net.minecraft.entity.ItemEntity(
                                    world, otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                                world.spawnEntity(itemEntity);
                                // Track dropped item entity for undo
                                if (AreaEnchantMod.config.enableUndo && undoData != null) {
                                    undoData.addItemEntity(itemEntity.getUuid());
                        }
                    }
                }
                    }
                } else {
                    // Block couldn't be broken - log it but continue
                    // CRITICAL: Don't count failed breaks towards batch limit
                    BlockState currentState = world.getBlockState(otherPos);
                    String currentBlockId = net.minecraft.registry.Registries.BLOCK.getId(currentState.getBlock()).toString();
                    // If block is already air, that's fine - it was probably broken by something else
                    // But if it's still the same block, that's a problem
                    if (!currentState.isAir() && currentBlockId.equals(blockId)) {
                        System.err.println("[Area Mine] CRITICAL: Block at " + otherPos + " failed to break! This should not happen!");
                    }
                    // Don't increment processedThisTick - failed breaks don't count towards batch limit
                }
            }
            
            // Apply durability ONCE for this batch (not per block!)
            // NOTE: processedThisTick now only counts successfully broken blocks
            if (!player.isCreative() && tool.isDamageable() && processedThisTick > 0) {
                // Use the number of blocks actually broken this tick (processedThisTick now only counts successful breaks)
                int actualBlocksMinedInBatch = processedThisTick;
                
                if (actualBlocksMinedInBatch > 0) {
                    double totalDurabilityFraction = actualBlocksMinedInBatch * durabilityMultiplier;
                    int damage = (int) Math.ceil(totalDurabilityFraction);
                    
                    if (damage > 0) {
                        tool.damage(damage, player, net.minecraft.entity.EquipmentSlot.MAINHAND);
                    }
                    
                    // Check for durability warning
                    if (AreaEnchantMod.config.durabilityWarning) {
                        int remaining = tool.getMaxDamage() - tool.getDamage();
                        double percentage = (remaining / (double) tool.getMaxDamage()) * 100;
                        if (percentage <= AreaEnchantMod.config.durabilityWarningThreshold) {
                            player.sendMessage(Text.literal("§c[Area Mine] Tool durability low: " + 
                                (int)percentage + "%"), true);
                        }
                    }
                    
                    // Prevent tool breaking
                    if (AreaEnchantMod.config.preventToolBreaking && tool.getDamage() >= tool.getMaxDamage() - 1) {
                        player.sendMessage(Text.literal("§c[Area Mine] Stopped to prevent tool breaking!"), false);
                        cancelled = true;
                    }
                }
                // NOTE: currentIndex is already incremented at the start of the loop
                // processedThisTick is only incremented when blocks are successfully broken
            }
            
            // If we've processed all blocks, finish
            if (currentIndex >= blocks.size()) {
                finish();
            }
        }
        
        private void finish() {
            // CRITICAL: Clear the isBreakingArea flag FIRST to prevent re-entry
            // This must be done before any other operations to prevent block break events
            // from triggering new area mine events while we're still processing
            AreaMineHandler.clearBreakingFlag(player.getUuid());
            
            // CRITICAL: Save undo data FIRST, before updating stats
            // This ensures undo data is always saved, even if stats update fails
            System.out.println("[Area Mine] [BATCHED] finish() called, undo data: " + 
                (undoData != null ? undoData.blocks.size() + " blocks" : "null"));
            if (AreaEnchantMod.config.enableUndo && undoData != null && !undoData.blocks.isEmpty()) {
                // Save to storage
                System.out.println("[Area Mine] [BATCHED] Saving undo data in finish(): " + undoData.blocks.size() + " blocks");
                PlayerDataManager.setUndoData(player.getUuid(), undoData);
                
                // Immediately verify it was saved
                PlayerDataManager.UndoData verifyData = PlayerDataManager.getUndoData(player.getUuid());
                if (verifyData == null) {
                    System.err.println("[Area Mine] [BATCHED] CRITICAL: Undo data was NOT saved! Retrying...");
                    // Retry saving
                    PlayerDataManager.setUndoData(player.getUuid(), undoData);
                    verifyData = PlayerDataManager.getUndoData(player.getUuid());
                    if (verifyData == null) {
                        System.err.println("[Area Mine] [BATCHED] CRITICAL: Undo data save FAILED after retry!");
                    } else {
                        System.out.println("[Area Mine] [BATCHED] Undo data saved successfully after retry: " + verifyData.blocks.size() + " blocks");
                    }
                } else {
                    System.out.println("[Area Mine] [BATCHED] Undo data verified: " + verifyData.blocks.size() + " blocks");
                }
            } else if (AreaEnchantMod.config.enableUndo && (undoData == null || undoData.blocks.isEmpty())) {
                System.err.println("[Area Mine] [BATCHED] WARNING: Undo data not saved in finish()! enableUndo=" + 
                    AreaEnchantMod.config.enableUndo + ", undoData=" + (undoData != null ? "exists" : "null") + 
                    ", blocks=" + (undoData != null ? undoData.blocks.size() : 0));
            }
            
            if (blocksMined > 0) {
                PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
                
                // Update stats
                playerData.addBlocksMined(blocksMined);
                for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
                    playerData.addBlockTypeStats(entry.getKey(), entry.getValue());
                }
                playerData.addDimensionStats(dimensionId, blocksMined);
                
                // Calculate mining time
                long endTime = world.getTime();
                playerData.addMiningTime(endTime - startTime);
                
                // Calculate and award tokens using block values
                int tokensEarned = 0;
                if (AreaEnchantMod.config.enableUpgradeSystem) {
                    tokensEarned = calculateTokensFromBlocks(blockCounts);
                    
                    // Get Efficiency level
                    int efficiencyLevel = 0;
                    var enchantmentRegistry = world.getRegistryManager().getOptional(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
                    if (enchantmentRegistry.isPresent()) {
                        var efficiencyEntry = enchantmentRegistry.get().getEntry(net.minecraft.util.Identifier.of("minecraft:efficiency"));
                        if (efficiencyEntry.isPresent()) {
                            efficiencyLevel = EnchantmentHelper.getLevel(efficiencyEntry.get(), tool);
                        }
                    }
                    
                    if (efficiencyLevel > 0 && AreaEnchantMod.config.enableEnchantmentSynergies) {
                        double efficiencyBonus = efficiencyLevel * AreaEnchantMod.config.efficiencySpeedBonus;
                        tokensEarned = (int) (tokensEarned * (1.0 + efficiencyBonus));
                    }
                    
                    if (playerData.hasUpgrade("efficiency_boost")) {
                        tokensEarned = (int) (tokensEarned * 1.5);
                    }
                    
                    playerData.addMiningTokens(tokensEarned);
                    AdvancementTracker.onTokensEarned(player, tokensEarned);
                }
                
                AdvancementTracker.onBlocksMined(player, blocksMined);
                
                // Play completion sound
                if (AreaEnchantMod.config.soundEffects) {
                    world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                        SoundCategory.PLAYERS, 0.3f, 1.5f);
                }
                
                // Send feedback
                String feedbackMessage = "§a[Area Mine] §f" + blocksMined + " blocks";
                if (AreaEnchantMod.config.enableUpgradeSystem && tokensEarned > 0) {
                    feedbackMessage += " §7| §e+" + String.format("%,d", tokensEarned) + " tokens";
                }
                
                // Send as action bar (overlay above hotbar)
                if (AreaEnchantMod.config.actionBarFeedback) {
                    player.sendMessage(Text.literal(feedbackMessage), true);
                }
                
                // Send as chat message (always enabled)
                player.sendMessage(Text.literal(feedbackMessage), false);
            }
        }
        
        private double getDurabilityMultiplier() {
            return switch (enchantmentLevel) {
                case 1 -> 0.1;
                case 2 -> 0.2;
                case 3 -> 0.3;
                default -> AreaEnchantMod.config.durabilityMultiplier;
            };
        }
        
        public void cancel() {
            cancelled = true;
            // NOTE: finish() is called by cancelTask() after setting cancelled
            // This ensures the flag is cleared when a task is cancelled
        }
        
        public boolean isFinished() {
            return cancelled || currentIndex >= blocks.size();
        }
        
        /**
         * Calculate tokens earned from blocks based on block values
         * Uses the Block Value System if enabled, otherwise falls back to miningTokensPerBlock
         */
        private int calculateTokensFromBlocks(Map<String, Integer> blockCounts) {
            int totalTokens = 0;
            
            if (AreaEnchantMod.config.enableBlockValues && !AreaEnchantMod.config.blockValues.isEmpty()) {
                // Use block-specific values
                for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
                    String blockId = entry.getKey();
                    int count = entry.getValue();
                    int valuePerBlock = AreaEnchantMod.config.blockValues.getOrDefault(blockId, AreaEnchantMod.config.miningTokensPerBlock);
                    totalTokens += count * valuePerBlock;
                }
            } else {
                // Fall back to flat rate per block
                int totalBlocks = blockCounts.values().stream().mapToInt(Integer::intValue).sum();
                totalTokens = totalBlocks * AreaEnchantMod.config.miningTokensPerBlock;
            }
            
            return totalTokens;
        }
    }
}

