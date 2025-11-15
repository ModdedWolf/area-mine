package net.xai.area_enchant;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
    
    public static void register() {
        System.out.println("[Area Mine] Registering AreaMineHandler events");
        
        // Capture mining face when player starts/stops breaking
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                // Try to get the face from the player's look direction
                Direction face = getMiningFace(serverPlayer, pos);
                if (face != null) {
                    playerMiningFaces.put(player.getUuid(), face);
                    System.out.println("[Area Mine] [EVENT] Captured mining face: " + face + " for player " + player.getName().getString());
                }
            }
            return true; // Allow the break to continue
        });
        
        // Handle area mining after block is broken
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || !(world instanceof ServerWorld serverWorld)) {
                return;
            }
            
            System.out.println("[Area Mine] [EVENT] Block broken at " + pos + " by " + player.getName().getString());
            
            // Check if we're already breaking an area (prevent recursion)
            if (isBreakingArea.getOrDefault(player.getUuid(), false)) {
                System.out.println("[Area Mine] [EVENT] Already breaking area, skipping");
                return;
            }
            
            Direction miningFace = playerMiningFaces.remove(player.getUuid());
            if (miningFace == null) {
                System.out.println("[Area Mine] [EVENT] No mining face captured");
                return;
            }
            
            // Center the cube on the block that was just broken
            // The cube will break blocks around the broken block position
            System.out.println("[Area Mine] [EVENT] Block broken at: " + pos + ", Mining face: " + miningFace);
            
            handleAreaMining(serverPlayer, serverWorld, pos, miningFace);
        });
    }
    
    private static Direction getMiningFace(ServerPlayerEntity player, BlockPos pos) {
        // Get the direction the player is looking
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        
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
    
    private static void handleAreaMining(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Direction miningFace) {
        System.out.println("[Area Mine] [EVENT] handleAreaMining called at " + pos + " with face " + miningFace);
        
        var stack = player.getMainHandStack();
        if (stack.isEmpty()) {
            System.out.println("[Area Mine] [EVENT] Empty hand stack");
            return;
        }
        
        Optional<net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment>> enchantmentRegistry = 
            world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
        if (enchantmentRegistry.isEmpty()) {
            System.out.println("[Area Mine] [EVENT] No enchantment registry");
            return;
        }
        
        RegistryEntry<net.minecraft.enchantment.Enchantment> entry = enchantmentRegistry.get()
            .getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
        if (entry == null) {
            System.out.println("[Area Mine] [EVENT] Area Mine enchantment not found in registry");
            return;
        }
        
        int level = EnchantmentHelper.getLevel(entry, stack);
        System.out.println("[Area Mine] [EVENT] Area Mine level: " + level);
        if (level <= 0) {
            System.out.println("[Area Mine] [EVENT] No Area Mine enchantment on tool");
            return;
        }
        
        // Check if crouch is required
        if (AreaEnchantMod.config.requireCrouch && !player.isSneaking()) {
            System.out.println("[Area Mine] [EVENT] Crouch required but player not sneaking");
            return;
        }
        
        // Get player data
        PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
        playerData.ensureDefaultPattern();
        
        if (playerData.isDisabled()) {
            System.out.println("[Area Mine] [EVENT] Area Mine disabled for player");
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
        
        System.out.println("[Area Mine] [EVENT] Size config for level " + level + ": " + horizontalSize + "x" + verticalSize + "x" + depthSize);
        
        // Apply radius boost upgrade
        if (playerData.hasUpgrade("radius_boost")) {
            horizontalSize++;
            verticalSize++;
            depthSize++;
        }
        
        // Get blocks to mine
        List<BlockPos> blocksToMine = MiningPattern.getBlocksToMine(
            currentPattern,
            pos,
            miningFace,
            horizontalSize,
            verticalSize,
            depthSize
        );
        
        System.out.println("[Area Mine] [EVENT] BlocksToMine before filtering: " + blocksToMine.size());
        System.out.println("[Area Mine] [EVENT] Mining face: " + miningFace + ", Center: " + pos);
        // Count blocks by type for debugging
        Map<String, Integer> blockTypeCount = new HashMap<>();
        // Also check the actual range of positions
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos bp : blocksToMine) {
            BlockState bs = world.getBlockState(bp);
            String type = bs.isAir() ? "AIR" : Registries.BLOCK.getId(bs.getBlock()).toString();
            blockTypeCount.put(type, blockTypeCount.getOrDefault(type, 0) + 1);
            minX = Math.min(minX, bp.getX());
            maxX = Math.max(maxX, bp.getX());
            minY = Math.min(minY, bp.getY());
            maxY = Math.max(maxY, bp.getY());
            minZ = Math.min(minZ, bp.getZ());
            maxZ = Math.max(maxZ, bp.getZ());
        }
        System.out.println("[Area Mine] [EVENT] Block types in area: " + blockTypeCount);
        System.out.println("[Area Mine] [EVENT] Position range: X[" + minX + " to " + maxX + "] Y[" + minY + " to " + maxY + "] Z[" + minZ + " to " + maxZ + "]");
        System.out.println("[Area Mine] [EVENT] Dimensions: " + (maxX - minX + 1) + "x" + (maxY - minY + 1) + "x" + (maxZ - minZ + 1));
        System.out.println("[Area Mine] [EVENT] Expected: 26 blocks in 3x3x3 cube. Found: " + blocksToMine.size() + " blocks");
        
        // Verify the cube includes all positions
        int expectedCount = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1) - 1; // -1 for center
        if (blocksToMine.size() != expectedCount) {
            System.out.println("[Area Mine] [EVENT] WARNING: Expected " + expectedCount + " blocks but found " + blocksToMine.size());
        }
        
        // Filter blocks
        BlockState originalBlock = world.getBlockState(pos);
        List<BlockPos> filteredBlocks = new ArrayList<>();
        int airCount = 0;
        int blacklistCount = 0;
        int veinMiningCount = 0;
        
        for (BlockPos blockPos : blocksToMine) {
            BlockState blockState = world.getBlockState(blockPos);
            
            // Skip air
            if (blockState.isAir()) {
                airCount++;
                continue;
            }
            
            // Skip the original block (already broken)
            if (blockPos.equals(pos)) {
                continue;
            }
            
            // Check blacklist/whitelist
            String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
            
            // Check for unbreakable blocks (bedrock has hardness -1.0f)
            if (blockState.getHardness(world, blockPos) < 0) {
                blacklistCount++;
                continue;
            }
            
            if (!AreaEnchantMod.config.blockWhitelist.isEmpty()) {
                if (!AreaEnchantMod.config.blockWhitelist.contains(blockId)) {
                    blacklistCount++;
                    continue;
                }
            } else if (AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                blacklistCount++;
                continue;
            }
            
            // Apply block filters (pickaxe-effective, ores-only, stone-only, etc.)
            if (!BlockFilter.shouldMineBlock(blockState, AreaEnchantMod.config)) {
                blacklistCount++;
                continue;
            }
            
            // Vein mining check
            if (AreaEnchantMod.config.veinMiningMode && !originalBlock.isAir()) {
                String originalId = Registries.BLOCK.getId(originalBlock.getBlock()).toString();
                if (!blockId.equals(originalId)) {
                    veinMiningCount++;
                    continue;
                }
            }
            
            filteredBlocks.add(blockPos);
        }
        
        System.out.println("[Area Mine] [EVENT] Filtered: " + filteredBlocks.size() + " blocks (air: " + airCount + ", filtered: " + blacklistCount + ", vein: " + veinMiningCount + ")");
        
        if (filteredBlocks.isEmpty()) {
            System.out.println("[Area Mine] [EVENT] No blocks to mine after filtering");
            return;
        }
        
        System.out.println("[Area Mine] [EVENT] Found " + filteredBlocks.size() + " blocks to mine!");
        player.sendMessage(Text.literal("§a[Area Mine] Mining " + filteredBlocks.size() + " blocks!"), false);
        
        // Set breaking flag
        isBreakingArea.put(player.getUuid(), true);
        
        // Initialize undo data
        PlayerDataManager.UndoData undoData = new PlayerDataManager.UndoData();
        
        // Use batched breaking if enabled
        if (AreaEnchantMod.config.batchedBreaking) {
            BatchedBlockBreaker.startBreaking(player, world, filteredBlocks, stack, level, undoData);
            isBreakingArea.put(player.getUuid(), false);
            return;
        }
        
        // Instant breaking
        int blocksMined = 0;
        boolean autoPickup = AreaEnchantMod.config.autoPickup || playerData.hasUpgrade("auto_pickup");
        
        for (BlockPos otherPos : filteredBlocks) {
            BlockState blockState = world.getBlockState(otherPos);
            if (blockState.isAir()) {
                continue;
            }
            
            // Save for undo
            undoData.addBlock(otherPos, blockState);
            
            // Get drops
            List<ItemStack> drops = new ArrayList<>();
            if (!player.isCreative()) {
                drops.addAll(Block.getDroppedStacks(blockState, world, otherPos,
                    world.getBlockEntity(otherPos), player, stack));
            }
            
            // Break block
            boolean broken = world.breakBlock(otherPos, false);
            if (broken) {
                blockState.onStacksDropped(world, otherPos, stack, !player.isCreative());
                
                // Handle drops
                if (!player.isCreative()) {
                    for (ItemStack drop : drops) {
                        if (autoPickup) {
                            ItemStack originalDrop = drop.copy();
                            if (!player.getInventory().insertStack(drop)) {
                                // Inventory full, drop item
                                ItemEntity itemEntity = new ItemEntity(world,
                                    otherPos.getX() + 0.5, otherPos.getY() + 0.5, otherPos.getZ() + 0.5, drop);
                                world.spawnEntity(itemEntity);
                                // Track dropped item entity for undo
                                if (AreaEnchantMod.config.enableUndo) {
                                    undoData.addItemEntity(itemEntity.getUuid());
                                }
                            } else {
                                // Item was added to inventory, track it for undo
                                if (AreaEnchantMod.config.enableUndo) {
                                    String itemId = Registries.ITEM.getId(originalDrop.getItem()).toString();
                                    undoData.addInventoryItem(itemId, originalDrop.getCount());
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
                blocksMined++;
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
        
        // Clear breaking flag
        isBreakingArea.put(player.getUuid(), false);
        
        System.out.println("[Area Mine] [EVENT] Mined " + blocksMined + " blocks!");
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

