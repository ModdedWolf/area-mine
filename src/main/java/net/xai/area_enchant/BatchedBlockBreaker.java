package net.xai.area_enchant;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
        // Cancel any existing task for this player
        cancelTask(player.getUuid());
        
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
            if (cancelled || currentIndex >= blocks.size()) {
                finish();
                return;
            }
            
            int batchSize = AreaEnchantMod.config.blocksPerTick;
            int endIndex = Math.min(currentIndex + batchSize, blocks.size());
            
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
            
            for (int i = currentIndex; i < endIndex; i++) {
                BlockPos otherPos = blocks.get(i);
                BlockState blockState = world.getBlockState(otherPos);
                
                if (blockState.isAir()) {
                    continue;
                }
                
                String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                
                // Store for undo if enabled
                if (AreaEnchantMod.config.enableUndo) {
                    undoData.addBlock(otherPos, blockState);
                }
                
                // Get drops using Block.getDroppedStacks (respects Fortune, Silk Touch, etc.)
                List<ItemStack> drops = new ArrayList<>();
                if (!player.isCreative()) {
                    drops.addAll(Block.getDroppedStacks(blockState, world, otherPos, 
                        world.getBlockEntity(otherPos), player, tool));
                }
                
                // Break block
                world.breakBlock(otherPos, false);
                
                // Add drops to inventory or drop in world
                if (!player.isCreative()) {
                    for (ItemStack drop : drops) {
                        if (autoPickup) {
                            if (!player.getInventory().insertStack(drop)) {
                                Block.dropStack(world, otherPos, drop);
                            }
                        } else {
                            Block.dropStack(world, otherPos, drop);
                        }
                    }
                }
                
                blocksMined++;
                blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
            }
            
            // Apply durability ONCE for this batch (not per block!)
            if (!player.isCreative() && tool.isDamageable()) {
                // Count actual blocks mined in this batch (not indices!)
                int actualBlocksMinedInBatch = 0;
                for (int i = currentIndex; i < endIndex; i++) {
                    if (i < blocks.size() && !world.getBlockState(blocks.get(i)).isAir()) {
                        actualBlocksMinedInBatch++;
                    }
                }
                
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
                        player.sendMessage(Text.literal("§c[Area Mine] Stopped to prevent tool breaking!"), true);
                        cancelled = true;
                    }
                }
            }
            
            currentIndex = endIndex;
        }
        
        private void finish() {
            if (blocksMined > 0) {
                PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
                
                // Update stats
                playerData.addBlocksMined(blocksMined);
                for (Map.Entry<String, Integer> entry : blockCounts.entrySet()) {
                    playerData.addBlockTypeStats(entry.getKey(), entry.getValue());
                }
                playerData.addDimensionStats(dimensionId, blocksMined);
                
                // Save undo data
                if (AreaEnchantMod.config.enableUndo) {
                    playerData.setLastOperation(undoData);
                }
                
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
            finish();
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

