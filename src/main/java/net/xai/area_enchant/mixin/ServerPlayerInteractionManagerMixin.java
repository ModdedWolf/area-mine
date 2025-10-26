package net.xai.area_enchant.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.xai.area_enchant.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerPlayerInteractionManagerMixin {
    @Shadow public abstract boolean tryBreakBlock(BlockPos pos);

    @Shadow public ServerWorld world;

    @Shadow public ServerPlayerEntity player;

    private Direction miningFace;
    private boolean isBreakingArea = false;

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void captureMiningFace(BlockPos pos, PlayerActionC2SPacket.Action action, Direction face, int maxUpdateDepth, int sequence, CallbackInfo ci) {
        if (action == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK) {
            miningFace = face;
        }
    }

    @Inject(method = "tryBreakBlock", at = @At("RETURN"))
    private void onBlockBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() || isBreakingArea || miningFace == null) {
            miningFace = null;
            return;
        }

        var stack = player.getMainHandStack();
        Optional<net.minecraft.registry.Registry<Enchantment>> enchantmentRegistry = world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
        if (enchantmentRegistry.isEmpty()) {
            miningFace = null;
            return;
        }
        RegistryEntry<Enchantment> entry = enchantmentRegistry.get().getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
        if (entry == null) {
            miningFace = null;
            return;
        }
        int level = EnchantmentHelper.getLevel(entry, stack);
        if (level <= 0) {
            miningFace = null;
            return;
        }
        
        // Check if crouch is required (v4)
        if (AreaEnchantMod.config.requireCrouch && !player.isSneaking()) {
            miningFace = null;
            return;
        }

        // Creative mode bypass
        if (player.isCreative() && AreaEnchantMod.config.creativeModeBypass) {
            // Creative players can use freely
        }

        // Check enchantment conflicts
        if (!AreaEnchantMod.config.enchantmentConflicts.isEmpty()) {
            for (String conflictId : AreaEnchantMod.config.enchantmentConflicts) {
                var conflictEnchant = enchantmentRegistry.get().getEntry(Identifier.of(conflictId));
                if (conflictEnchant.isPresent() && EnchantmentHelper.getLevel(conflictEnchant.get(), stack) > 0) {
                    if (AreaEnchantMod.config.actionBarFeedback) {
                        player.sendMessage(Text.literal("§cArea Mine conflicts with " + conflictId), true);
                    }
                    miningFace = null;
                    return;
                }
            }
        }

        // Get enchantment synergies
        int efficiencyLevel = 0;
        int unbreakingLevel = 0;
        
        if (AreaEnchantMod.config.enableEnchantmentSynergies) {
            // Get Efficiency level (increases token rewards)
            var efficiencyEntry = enchantmentRegistry.get().getEntry(Identifier.of("minecraft:efficiency"));
            if (efficiencyEntry.isPresent()) {
                efficiencyLevel = EnchantmentHelper.getLevel(efficiencyEntry.get(), stack);
            }
            
            // Get Unbreaking level (reduces durability cost)
            var unbreakingEntry = enchantmentRegistry.get().getEntry(Identifier.of("minecraft:unbreaking"));
            if (unbreakingEntry.isPresent()) {
                unbreakingLevel = EnchantmentHelper.getLevel(unbreakingEntry.get(), stack);
            }
            
            // Mending works like vanilla - repairs from XP orbs naturally
        }

        // Tool durability check with 10% warning
        if (AreaEnchantMod.config.preventToolBreaking && !player.isCreative()) {
            int currentDurability = stack.getMaxDamage() - stack.getDamage();
            int maxDurability = stack.getMaxDamage();
            int estimatedBlocks = estimateBlockCount(pos, level);
            
            // Calculate durability cost with tier scaling and Unbreaking reduction
            double durabilityMultiplier = getDurabilityMultiplier(level);
            if (unbreakingLevel > 0) {
                durabilityMultiplier *= (1.0 - (unbreakingLevel * AreaEnchantMod.config.unbreakingDurabilityReduction));
            }
            int estimatedCost = (int) Math.ceil(estimatedBlocks * durabilityMultiplier);
            
            // Check if tool would break
            if (currentDurability <= estimatedCost) {
                if (AreaEnchantMod.config.actionBarFeedback) {
                    player.sendMessage(Text.literal("§cTool too damaged! Would break."), true);
                }
                miningFace = null;
                return;
            }
            
            // 10% durability warning
            double durabilityPercent = (double) currentDurability / maxDurability;
            if (durabilityPercent <= 0.10) {
                if (AreaEnchantMod.config.actionBarFeedback) {
                    player.sendMessage(Text.literal("§c⚠ WARNING: Tool at " + String.format("%.1f", durabilityPercent * 100) + "% durability!"), true);
                }
            } else if (AreaEnchantMod.config.durabilityWarning && currentDurability <= AreaEnchantMod.config.durabilityWarningThreshold) {
                if (AreaEnchantMod.config.actionBarFeedback) {
                    player.sendMessage(Text.literal("§eWarning: Tool durability low (" + currentDurability + ")"), true);
                }
            }
        }

        // Check if player has area mine disabled
        PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
        playerData.ensureDefaultPattern();
        
        if (playerData.isDisabled()) {
            miningFace = null;
            return;
        }
        
        // Check if player has unlocked the current pattern
        String currentPattern = AreaEnchantMod.config.miningPattern;
        if (!playerData.hasPattern(currentPattern)) {
            if (AreaEnchantMod.config.actionBarFeedback) {
                player.sendMessage(Text.literal("§c[Area Mine] You haven't unlocked the " + currentPattern + " pattern! Use /areamine patterns"), true);
            }
            miningFace = null;
            return;
        }

        // Check cooldown
        if (AreaEnchantMod.config.cooldownTicks > 0) {
            long currentTick = world.getTime();
            if (playerData.isOnCooldown(currentTick, AreaEnchantMod.config.cooldownTicks)) {
                if (AreaEnchantMod.config.actionBarFeedback) {
                    long ticksRemaining = AreaEnchantMod.config.cooldownTicks - (currentTick - playerData.getLastUseTick());
                    double secondsRemaining = ticksRemaining / 20.0;
                    player.sendMessage(Text.literal(String.format("§e[Area Mine] On cooldown: %.1fs remaining", secondsRemaining)), true);
                }
                miningFace = null;
                return;
            }
            playerData.updateLastUse(currentTick);
        } else {
            playerData.updateLastUse(world.getTime());
        }

        // v4: Get per-pattern tier sizes (reuse currentPattern from unlock check)
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

        // Get blocks to mine using the selected pattern
        List<BlockPos> blocksToMine = MiningPattern.getBlocksToMine(
            currentPattern, 
            pos, 
            miningFace,
            horizontalSize,
            verticalSize,
            depthSize
        );

        // Get the original block type for vein mining
        BlockState originalBlock = world.getBlockState(pos);
        
        // Filter blocks based on configuration
        List<BlockPos> filteredBlocks = new ArrayList<>();
        for (BlockPos blockPos : blocksToMine) {
            BlockState blockState = world.getBlockState(blockPos);
            
            // Check blacklist/whitelist
            String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
            if (!AreaEnchantMod.config.blockBlacklist.isEmpty() && AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                continue;
            }
            if (!AreaEnchantMod.config.blockWhitelist.isEmpty() && !AreaEnchantMod.config.blockWhitelist.contains(blockId)) {
                continue;
            }
            
            // Apply block filters
            if (!BlockFilter.shouldMineBlock(blockState, AreaEnchantMod.config)) {
                continue;
            }
            
            // Vein mining mode - only mine same block type
            if (AreaEnchantMod.config.veinMiningMode) {
                if (!blockState.getBlock().equals(originalBlock.getBlock())) {
                    continue;
                }
            }
            
            // World protection check
            if (AreaEnchantMod.config.checkWorldProtection) {
                // Check if player can modify blocks
                // This will be respected by protection plugins/mods
                if (!player.canModifyBlocks()) {
                    continue;
                }
            }
            
            filteredBlocks.add(blockPos);
            
            // Max block limit check
            if (filteredBlocks.size() >= AreaEnchantMod.config.maxBlocksPerActivation) {
                break;
            }
        }
        
        // Max blocks warning
        if (filteredBlocks.size() >= AreaEnchantMod.config.maxBlocksPerActivation && AreaEnchantMod.config.actionBarFeedback) {
            player.sendMessage(Text.literal("§eReached max block limit (" + AreaEnchantMod.config.maxBlocksPerActivation + ")"), true);
        }

        // Particles effect
        if (AreaEnchantMod.config.particleEffects) {
            for (BlockPos blockPos : filteredBlocks) {
                world.spawnParticles(ParticleTypes.ENCHANT,
                        blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                        3, 0.5, 0.5, 0.5, 0.0);
            }
        }

        // Sound effect
        if (AreaEnchantMod.config.soundEffects && !filteredBlocks.isEmpty()) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.3f, 2.0f);
        }

        // Initialize undo data
        PlayerDataManager.UndoData undoData = new PlayerDataManager.UndoData();

        isBreakingArea = true;
        
        // Play sound when area mining starts
        if (AreaEnchantMod.config.soundEffects) {
            world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_ANVIL_USE, 
                net.minecraft.sound.SoundCategory.PLAYERS, 0.5f, 2.0f);
        }
        
        // Use batched breaking system if enabled
        if (AreaEnchantMod.config.batchedBreaking) {
            net.xai.area_enchant.BatchedBlockBreaker.startBreaking(player, world, filteredBlocks, 
                player.getMainHandStack(), level, undoData);
            isBreakingArea = false;
            miningFace = null;
            return;
        }
        
        // Otherwise use instant breaking (original system)
        int blocksMined = 0;
        long startTime = world.getTime();
        String dimensionId = world.getRegistryKey().getValue().toString();
        Map<String, Integer> blockCounts = new HashMap<>();
        
        // Calculate durability multiplier with tier scaling and Unbreaking
        double durabilityMultiplier = getDurabilityMultiplier(level);
        if (unbreakingLevel > 0) {
            durabilityMultiplier *= (1.0 - (unbreakingLevel * AreaEnchantMod.config.unbreakingDurabilityReduction));
        }
        
        // Check if auto-pickup is enabled (config or upgrade)
        boolean autoPickup = AreaEnchantMod.config.autoPickup || playerData.hasUpgrade("auto_pickup");
        
        try {
            for (BlockPos otherPos : filteredBlocks) {
                BlockState blockState = world.getBlockState(otherPos);
                
                // Skip air blocks - don't mine, track, or count them
                if (blockState.isAir()) {
                    continue;
                }
                
                // Save block state for undo
                undoData.addBlock(otherPos, blockState);
                
                // Track diamond mining for advancement
                AdvancementTracker.onDiamondMined(player, blockState.getBlock());
                
                // Track block type stats
                String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                blockCounts.put(blockId, blockCounts.getOrDefault(blockId, 0) + 1);
                
                // Handle Fortune/Silk Touch
                if (AreaEnchantMod.config.respectFortuneAndSilkTouch && autoPickup) {
                    // Get drops with enchantments
                    List<ItemStack> drops = Block.getDroppedStacks(blockState, world, otherPos, world.getBlockEntity(otherPos), player, stack);
                    
                    // Remove the block
                    world.removeBlock(otherPos, false);
                    blockState.onStacksDropped(world, otherPos, stack, true);
                    
                    // Auto-pickup
                    for (ItemStack drop : drops) {
                        if (!player.getInventory().insertStack(drop)) {
                            // Inventory full, drop item
                            ItemEntity itemEntity = new ItemEntity(world, otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                            world.spawnEntity(itemEntity);
                        }
                    }
                } else {
                    // Standard breaking (handles Fortune/Silk Touch automatically)
                    tryBreakBlock(otherPos);
                }
                
                // Mending works like vanilla - it will repair from XP orbs (ores, mobs, etc.)
                // No custom repair logic needed - vanilla handles it automatically
                
                blocksMined++;
            }
            
            // Apply durability cost ONCE for all blocks mined (not per block)
            if (AreaEnchantMod.config.durabilityScaling && !player.isCreative() && blocksMined > 0) {
                // Calculate total durability cost based on blocks mined
                double totalDurabilityFraction = blocksMined * durabilityMultiplier;
                int totalDamage = (int) Math.ceil(totalDurabilityFraction);
                
                if (totalDamage > 0) {
                    stack.damage(totalDamage, player, EquipmentSlot.MAINHAND);
                }
            }
            
            // Update stats
            playerData.addBlocksMined(blocksMined);
            playerData.addDimensionStats(dimensionId, blocksMined);
            for (Map.Entry<String, Integer> blockEntry : blockCounts.entrySet()) {
                playerData.addBlockTypeStats(blockEntry.getKey(), blockEntry.getValue());
            }
            
            // Award mining tokens
            if (AreaEnchantMod.config.enableUpgradeSystem) {
                // Calculate base tokens using block values
                int tokensEarned = calculateTokensFromBlocks(blockCounts);
                
                // Efficiency synergy: Bonus tokens based on efficiency level
                if (efficiencyLevel > 0 && AreaEnchantMod.config.enableEnchantmentSynergies) {
                    double efficiencyBonus = efficiencyLevel * AreaEnchantMod.config.efficiencySpeedBonus;
                    tokensEarned = (int) (tokensEarned * (1.0 + efficiencyBonus));
                }
                
                // Efficiency boost upgrade
                if (playerData.hasUpgrade("efficiency_boost")) {
                    tokensEarned = (int) (tokensEarned * 1.5);
                }
                
                playerData.addMiningTokens(tokensEarned);
                
                // Check achievement
                AdvancementTracker.onTokensEarned(player, tokensEarned);
            }
            
            // Calculate mining time
            long endTime = world.getTime();
            playerData.addMiningTime(endTime - startTime);
            
            // Store undo data
            playerData.setLastOperation(undoData);
            
            // Track first use advancement
            if (playerData.isFirstUse()) {
                playerData.setFirstUse(false);
                AdvancementTracker.onFirstUse(player);
            }
            
            // Check advancement progress
            AdvancementTracker.onBlocksMined(player, blocksMined);
            
            // Play completion sound
            if (AreaEnchantMod.config.soundEffects && blocksMined > 0) {
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                    net.minecraft.sound.SoundCategory.PLAYERS, 0.3f, 1.5f);
            }
            
            // Feedback messages
            if (blocksMined > 0) {
                int tokensEarned = 0;
                
                // Calculate tokens for display
                if (AreaEnchantMod.config.enableUpgradeSystem) {
                    tokensEarned = calculateTokensFromBlocks(blockCounts);
                    
                    // Calculate token bonuses (but don't display which enchantments)
                    if (efficiencyLevel > 0 && AreaEnchantMod.config.enableEnchantmentSynergies) {
                        double efficiencyBonus = efficiencyLevel * AreaEnchantMod.config.efficiencySpeedBonus;
                        tokensEarned = (int) (tokensEarned * (1.0 + efficiencyBonus));
                    }
                    if (playerData.hasUpgrade("efficiency_boost")) {
                        tokensEarned = (int) (tokensEarned * 1.5);
                    }
                }
                
                // Build message
                StringBuilder message = new StringBuilder("§a[Area Mine] §f" + blocksMined + " blocks");
                if (AreaEnchantMod.config.enableUpgradeSystem && tokensEarned > 0) {
                    message.append(" §7| §e+").append(String.format("%,d", tokensEarned)).append(" tokens");
                }
                
                // Send as action bar (overlay above hotbar)
                if (AreaEnchantMod.config.actionBarFeedback) {
                    player.sendMessage(Text.literal(message.toString()), true);
                }
                
                // Send as chat message (always enabled for instant breaking)
                player.sendMessage(Text.literal(message.toString()), false);
            }
            
            // Save player data after significant operation
            PlayerDataManager.saveToDisk(player.getUuid(), playerData);
            
        } finally {
            isBreakingArea = false;
            miningFace = null;
        }
    }

    private int estimateBlockCount(BlockPos pos, int level) {
        // v4: Use per-pattern tier sizes
        String currentPattern = AreaEnchantMod.config.miningPattern;
        var sizeConfig = AreaEnchantMod.config.patternLevels.containsKey(currentPattern) ?
            AreaEnchantMod.config.patternLevels.get(currentPattern).getOrDefault(level, new AreaEnchantMod.Size(level, level, level)) :
            AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
        return sizeConfig.horizontal * sizeConfig.vertical * sizeConfig.depth;
    }
    
    /**
     * Get durability multiplier that scales by enchantment tier
     * Tier I: 10%, Tier II: 20%, Tier III: 30%
     */
    private double getDurabilityMultiplier(int level) {
        return switch (level) {
            case 1 -> 0.1; // 10% durability per block
            case 2 -> 0.2; // 20% durability per block
            case 3 -> 0.3; // 30% durability per block
            default -> AreaEnchantMod.config.durabilityMultiplier; // Fallback to config
        };
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
