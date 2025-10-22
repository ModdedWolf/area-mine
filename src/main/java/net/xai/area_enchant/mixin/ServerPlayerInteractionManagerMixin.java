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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.xai.area_enchant.AreaEnchantMod;
import net.xai.area_enchant.PlayerDataManager;
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

        // Check if player has area mine disabled
        PlayerDataManager.PlayerData playerData = PlayerDataManager.get(player.getUuid());
        if (playerData.isDisabled()) {
            miningFace = null;
            return;
        }

        // Check cooldown
        if (AreaEnchantMod.config.cooldownTicks > 0) {
            long currentTick = world.getTime();
            if (playerData.isOnCooldown(currentTick, AreaEnchantMod.config.cooldownTicks)) {
                miningFace = null;
                return;
            }
            playerData.updateLastUse(currentTick);
        } else {
            playerData.updateLastUse(world.getTime());
        }

        // Check XP cost
        if (AreaEnchantMod.config.xpCostPerBlock > 0) {
            int estimatedBlocks = estimateBlockCount(pos, level);
            int totalXpCost = estimatedBlocks * AreaEnchantMod.config.xpCostPerBlock;
            if (player.experienceLevel < totalXpCost && player.totalExperience < totalXpCost) {
                player.sendMessage(Text.literal("§c[Area Mine] Not enough XP! Need " + totalXpCost + " XP."), true);
                miningFace = null;
                return;
            }
        }

        var sizeConfig = AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
        int horizontalSize = sizeConfig.horizontal;
        int verticalSize = sizeConfig.vertical;
        int depthSize = sizeConfig.depth;

        Direction depthDir = miningFace.getOpposite();
        Direction.Axis depthAxis = depthDir.getAxis();
        int depthOffset = switch (depthAxis) {
            case X -> depthDir.getOffsetX();
            case Y -> depthDir.getOffsetY();
            case Z -> depthDir.getOffsetZ();
        };

        int minX = pos.getX();
        int maxX = pos.getX();
        int minY = pos.getY();
        int maxY = pos.getY();
        int minZ = pos.getZ();
        int maxZ = pos.getZ();

        // Calculate mining area based on pattern
        if (AreaEnchantMod.config.miningPattern.equals("cube")) {
            // Depth (biased extension)
            int depthComp = switch (depthAxis) {
                case X -> pos.getX();
                case Y -> pos.getY();
                case Z -> pos.getZ();
            };
            int extend = depthSize - 1;
            int depthEnd = depthComp + extend * depthOffset;
            int depthMin = Math.min(depthComp, depthEnd);
            int depthMax = Math.max(depthComp, depthEnd);
            switch (depthAxis) {
                case X -> { minX = depthMin; maxX = depthMax; }
                case Y -> { minY = depthMin; maxY = depthMax; }
                case Z -> { minZ = depthMin; maxZ = depthMax; }
            }

            // Perpendicular axes (centered-ish)
            for (var perpAxis : Direction.Axis.values()) {
                if (perpAxis == depthAxis) continue;
                int pSize = (perpAxis == Direction.Axis.Y) ? verticalSize : horizontalSize;
                int lower = getLower(pSize);
                int upper = getUpper(pSize);
                int pComp = switch (perpAxis) {
                    case X -> pos.getX();
                    case Y -> pos.getY();
                    case Z -> pos.getZ();
                };
                int pMin = pComp + lower;
                int pMax = pComp + upper;
                switch (perpAxis) {
                    case X -> { minX = pMin; maxX = pMax; }
                    case Y -> { minY = pMin; maxY = pMax; }
                    case Z -> { minZ = pMin; maxZ = pMax; }
                }
            }
        }

        // Get the original block type for vein mining
        BlockState originalBlock = world.getBlockState(pos);
        
        // Collect blocks to mine
        List<BlockPos> blocksToMine = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos otherPos = new BlockPos(x, y, z);
                    if (otherPos.equals(pos)) continue;
                    
                    BlockState blockState = world.getBlockState(otherPos);
                    
                    // Check blacklist/whitelist
                    String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
                    if (!AreaEnchantMod.config.blockBlacklist.isEmpty() && AreaEnchantMod.config.blockBlacklist.contains(blockId)) {
                        continue;
                    }
                    if (!AreaEnchantMod.config.blockWhitelist.isEmpty() && !AreaEnchantMod.config.blockWhitelist.contains(blockId)) {
                        continue;
                    }
                    
                    // Vein mining mode - only mine same block type
                    if (AreaEnchantMod.config.veinMiningMode) {
                        if (!blockState.getBlock().equals(originalBlock.getBlock())) {
                            continue;
                        }
                    }
                    
                    blocksToMine.add(otherPos);
                }
            }
        }

        // Particles effect
        if (AreaEnchantMod.config.particleEffects) {
            for (BlockPos blockPos : blocksToMine) {
                world.spawnParticles(ParticleTypes.ENCHANT,
                        blockPos.getX() + 0.5, blockPos.getY() + 0.5, blockPos.getZ() + 0.5,
                        3, 0.5, 0.5, 0.5, 0.0);
            }
        }

        // Sound effect
        if (AreaEnchantMod.config.soundEffects && !blocksToMine.isEmpty()) {
            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.3f, 2.0f);
        }

        isBreakingArea = true;
        int blocksMined = 0;
        
        try {
            for (BlockPos otherPos : blocksToMine) {
                BlockState blockState = world.getBlockState(otherPos);
                
                // Handle Fortune/Silk Touch
                if (AreaEnchantMod.config.respectFortuneAndSilkTouch && AreaEnchantMod.config.autoPickup) {
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
                
                // Durability cost
                if (AreaEnchantMod.config.durabilityScaling) {
                    stack.damage(1, player, EquipmentSlot.MAINHAND);
                } else {
                    // Original behavior - still damage tool
                    stack.damage(1, player, EquipmentSlot.MAINHAND);
                }
                
                // XP cost
                if (AreaEnchantMod.config.xpCostPerBlock > 0) {
                    player.addExperience(-AreaEnchantMod.config.xpCostPerBlock);
                }
                
                blocksMined++;
            }
            
            // Update stats
            playerData.addBlocksMined(blocksMined);
            
        } finally {
            isBreakingArea = false;
            miningFace = null;
        }
    }

    private int estimateBlockCount(BlockPos pos, int level) {
        var sizeConfig = AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
        return sizeConfig.horizontal * sizeConfig.vertical * sizeConfig.depth;
    }

    private int getLower(int size) {
        return -((size - 1) / 2);
    }

    private int getUpper(int size) {
        return size / 2;
    }
}
