package net.xai.area_enchant;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Optional;

/**
 * Client-side visual preview system for Area Mine
 */
public class MinecraftPreview {
    
    private static long lastPreviewTime = 0;
    private static final long PREVIEW_COOLDOWN = 100; // ms between previews (faster refresh)
    
    public static void showPreviewParticles(MinecraftClient client) {
        try {
            ClientPlayerEntity player = client.player;
            ClientWorld world = client.world;
            
            if (player == null || world == null) return;
            
            // Throttle preview updates
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastPreviewTime < PREVIEW_COOLDOWN) return;
            lastPreviewTime = currentTime;
            
            // Check if player is looking at a block
            if (client.crosshairTarget == null || client.crosshairTarget.getType() != HitResult.Type.BLOCK) {
                return;
            }
            
            BlockHitResult hitResult = (BlockHitResult) client.crosshairTarget;
            BlockPos targetPos = hitResult.getBlockPos();
            Direction face = hitResult.getSide();
            
            // Check if player has Area Mine enchantment
            ItemStack stack = player.getMainHandStack();
            Optional<net.minecraft.registry.Registry<Enchantment>> enchantmentRegistry = 
                world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
            
            if (enchantmentRegistry.isEmpty()) return;
            
            RegistryEntry<Enchantment> entry = enchantmentRegistry.get()
                .getEntry(AreaEnchantMod.AREA_MINE.getValue()).orElse(null);
            
            if (entry == null) return;
            
            int level = EnchantmentHelper.getLevel(entry, stack);
            if (level <= 0) return;
            
            // Check if player is crouching (required in v4)
            if (!player.isSneaking()) return;
            
            // Get the mining pattern and calculate blocks
            String pattern = AreaEnchantMod.config.miningPattern;
            var sizeConfig = AreaEnchantMod.config.patternLevels.containsKey(pattern) ?
                AreaEnchantMod.config.patternLevels.get(pattern).getOrDefault(level, new AreaEnchantMod.Size(level, level, level)) :
                AreaEnchantMod.config.levels.getOrDefault(level, new AreaEnchantMod.Size(level, level, level));
            
            List<BlockPos> blocksToMine = MiningPattern.getBlocksToMine(
                pattern,
                targetPos,
                face,
                sizeConfig.horizontal,
                sizeConfig.vertical,
                sizeConfig.depth
            );
            
            // Spawn preview particles for each block using MinecraftClient's particle manager
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.particleManager == null) return;
            
            // Get particle type based on pattern
            var particleType = getParticleForPattern(pattern);
            
            for (BlockPos pos : blocksToMine) {
                if (world.getBlockState(pos).isAir()) continue;
                
                // Draw particles along all 12 edges of each block for clear box outline
                drawBlockOutline(mc, pos, particleType);
            }
            
            // Highlight the target block with enchantment particles
            for (int i = 0; i < 12; i++) {
                double offsetX = (Math.random() - 0.5) * 0.8;
                double offsetY = (Math.random() - 0.5) * 0.8;
                double offsetZ = (Math.random() - 0.5) * 0.8;
                
                mc.particleManager.addParticle(
                    ParticleTypes.ENCHANT,
                    targetPos.getX() + 0.5 + offsetX,
                    targetPos.getY() + 0.5 + offsetY,
                    targetPos.getZ() + 0.5 + offsetZ,
                    0.0, 0.1, 0.0
                );
            }
            
        } catch (Exception e) {
            // Silently fail if preview doesn't work
        }
    }
    
    /**
     * Draw a clear outline around a block by spawning particles along all 12 edges
     */
    private static void drawBlockOutline(MinecraftClient mc, BlockPos pos, net.minecraft.particle.SimpleParticleType particleType) {
        double x = pos.getX();
        double y = pos.getY();
        double z = pos.getZ();
        
        // Get particle density from config
        int particlesPerEdge = switch (AreaEnchantMod.config.previewDensity.toLowerCase()) {
            case "high" -> 3;
            case "low" -> 1;
            default -> 2; // medium
        };
        
        // Bottom face edges (4 edges)
        drawLine(mc, x, y, z, x + 1, y, z, particlesPerEdge, particleType);         // Bottom front
        drawLine(mc, x, y, z + 1, x + 1, y, z + 1, particlesPerEdge, particleType); // Bottom back
        drawLine(mc, x, y, z, x, y, z + 1, particlesPerEdge, particleType);         // Bottom left
        drawLine(mc, x + 1, y, z, x + 1, y, z + 1, particlesPerEdge, particleType); // Bottom right
        
        // Top face edges (4 edges)
        drawLine(mc, x, y + 1, z, x + 1, y + 1, z, particlesPerEdge, particleType);         // Top front
        drawLine(mc, x, y + 1, z + 1, x + 1, y + 1, z + 1, particlesPerEdge, particleType); // Top back
        drawLine(mc, x, y + 1, z, x, y + 1, z + 1, particlesPerEdge, particleType);         // Top left
        drawLine(mc, x + 1, y + 1, z, x + 1, y + 1, z + 1, particlesPerEdge, particleType); // Top right
        
        // Vertical edges (4 edges)
        drawLine(mc, x, y, z, x, y + 1, z, particlesPerEdge, particleType);         // Front left
        drawLine(mc, x + 1, y, z, x + 1, y + 1, z, particlesPerEdge, particleType); // Front right
        drawLine(mc, x, y, z + 1, x, y + 1, z + 1, particlesPerEdge, particleType); // Back left
        drawLine(mc, x + 1, y, z + 1, x + 1, y + 1, z + 1, particlesPerEdge, particleType); // Back right
    }
    
    /**
     * Draw a line of particles between two points
     */
    private static void drawLine(MinecraftClient mc, double x1, double y1, double z1, 
                                   double x2, double y2, double z2, int particles,
                                   net.minecraft.particle.SimpleParticleType particleType) {
        for (int i = 0; i <= particles; i++) {
            double t = (double) i / particles;
            double x = x1 + (x2 - x1) * t;
            double y = y1 + (y2 - y1) * t;
            double z = z1 + (z2 - z1) * t;
            
            mc.particleManager.addParticle(
                particleType,
                x, y, z,
                0.0, 0.0, 0.0
            );
        }
    }
    
    /**
     * Get particle type based on mining pattern
     * Cube: Purple, Sphere: Orange, Tunnel: Yellow, Cross: Green, Layer: Cyan, Vertical: Purple Portal
     */
    private static net.minecraft.particle.SimpleParticleType getParticleForPattern(String pattern) {
        return switch (pattern.toLowerCase()) {
            case "cube" -> ParticleTypes.ENCHANT;           // Purple (subtle, not bright)
            case "sphere" -> ParticleTypes.WAX_ON;          // Orange sparkles (stable, no flicker)
            case "tunnel" -> ParticleTypes.FLAME;           // Yellow/Orange
            case "cross" -> ParticleTypes.HAPPY_VILLAGER;   // Green
            case "layer" -> ParticleTypes.GLOW;             // Cyan/Teal (subtle, non-intrusive)
            case "vertical" -> ParticleTypes.PORTAL;        // Purple
            default -> ParticleTypes.ENCHANT;               // Fallback to purple (subtle)
        };
    }
}
