package net.xai.area_enchant;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

public class BlockFilter {
    
    public static boolean shouldMineBlock(BlockState blockState, AreaEnchantMod.Config config) {
        String blockId = Registries.BLOCK.getId(blockState.getBlock()).toString();
        
        // v4: Check if pickaxe-effective only mode is enabled
        if (config.pickaxeEffectiveOnly && !isPickaxeEffective(blockState, blockId)) {
            return false;
        }
        
        // Check if any filter is enabled
        if (config.filterOresOnly && !isOreBlock(blockId)) {
            return false;
        }
        
        if (config.filterStoneOnly && !isStoneBlock(blockId)) {
            return false;
        }
        
        if (config.filterIgnoreDirtGravel && isDirtOrGravel(blockId)) {
            return false;
        }
        
        if (config.filterValuableOnly && !isValuableBlock(blockId)) {
            return false;
        }
        
        return true;
    }
    
    // v4: Check if block is effective for pickaxe mining
    private static boolean isPickaxeEffective(BlockState blockState, String blockId) {
        // Check if the block is best mined with a pickaxe
        // Pickaxes are effective on stone, ores, metals, bricks, concrete, etc.
        // NOT effective on: dirt, sand, gravel, wood, leaves, crops, etc.
        
        // Explicitly exclude non-pickaxe blocks first
        if (blockId.contains("dirt") ||
            blockId.contains("grass") ||
            blockId.contains("sand") ||
            blockId.contains("gravel") ||
            blockId.contains("log") ||
            blockId.contains("wood") ||
            blockId.contains("leaves") ||
            blockId.contains("wool") ||
            blockId.contains("carpet") ||
            blockId.contains("planks") ||
            blockId.contains("crop") ||
            blockId.contains("wheat") ||
            blockId.contains("carrot") ||
            blockId.contains("potato") ||
            blockId.contains("beetroot") ||
            blockId.contains("soul_sand") ||
            blockId.contains("soul_soil") ||
            blockId.contains("farmland") ||
            blockId.contains("mycelium") ||
            blockId.contains("podzol")) {
            return false;
        }
        
        // Explicitly whitelist common mining blocks (these are always pickaxe blocks)
        if (blockId.contains("stone") ||
            blockId.contains("netherrack") ||
            blockId.contains("basalt") ||
            blockId.contains("blackstone") ||
            blockId.contains("ore") ||
            blockId.contains("deepslate") ||
            blockId.contains("concrete") ||
            blockId.contains("terracotta") ||
            blockId.contains("brick") ||
            blockId.contains("obsidian") ||
            blockId.contains("ancient_debris") ||
            blockId.contains("nether_gold") ||
            blockId.contains("quartz") ||
            blockId.contains("glowstone") ||
            blockId.contains("magma") ||
            blockId.equals("minecraft:end_stone")) {
            return true;
        }
        
        // For other blocks, check if pickaxe mines faster than hand
        ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
        float pickaxeSpeed = pickaxe.getMiningSpeedMultiplier(blockState);
        
        // If pickaxe has a mining speed >= 1.0, it's effective on this block
        return pickaxeSpeed >= 1.0f;
    }
    
    private static boolean isOreBlock(String blockId) {
        return blockId.contains("_ore") || 
               blockId.contains("ancient_debris") ||
               blockId.contains("raw_") ||
               blockId.equals("minecraft:glowstone") ||
               blockId.equals("minecraft:redstone") ||
               blockId.equals("minecraft:lapis");
    }
    
    private static boolean isStoneBlock(String blockId) {
        return blockId.contains("stone") || 
               blockId.contains("deepslate") ||
               blockId.contains("andesite") ||
               blockId.contains("diorite") ||
               blockId.contains("granite") ||
               blockId.contains("tuff") ||
               blockId.contains("basalt") ||
               blockId.contains("netherrack") ||
               blockId.equals("minecraft:end_stone");
    }
    
    private static boolean isDirtOrGravel(String blockId) {
        return blockId.contains("dirt") || 
               blockId.contains("gravel") ||
               blockId.contains("sand");
    }
    
    private static boolean isValuableBlock(String blockId) {
        return blockId.contains("diamond") ||
               blockId.contains("emerald") ||
               blockId.contains("gold") ||
               blockId.contains("iron") ||
               blockId.contains("copper") ||
               blockId.contains("redstone") ||
               blockId.contains("lapis") ||
               blockId.contains("coal") ||
               blockId.contains("quartz") ||
               blockId.contains("ancient_debris") ||
               blockId.contains("netherite");
    }
}

