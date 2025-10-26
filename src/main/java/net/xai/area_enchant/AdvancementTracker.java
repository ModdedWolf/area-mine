package net.xai.area_enchant;

import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;

public class AdvancementTracker {
    
    public static void onFirstUse(ServerPlayerEntity player) {
        // Advancement system placeholder
        // Full implementation would use AdvancementProgress API
        // For now, just track in player data
    }
    
    public static void onBlocksMined(ServerPlayerEntity player, int count) {
        PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
        
        // Check for "Efficiency Expert" - 10,000 blocks
        if (data.getBlocksMined() >= 10000 && !data.hasEarnedEfficiencyExpert()) {
            data.setEarnedEfficiencyExpert(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lAdvancement Made! §r§aEfficiency Expert - Mine 10,000 blocks with Area Mine"));
            PlayerDataManager.saveToDisk(player.getUuid(), data);
        }
        
        // Check for "Unstoppable" - 100,000 blocks
        if (data.getBlocksMined() >= 100000 && !data.hasEarnedUnstoppable()) {
            data.setEarnedUnstoppable(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lChallenge Complete! §r§6Unstoppable - Mine 100,000 blocks with Area Mine"));
            PlayerDataManager.saveToDisk(player.getUuid(), data);
        }
        
        // Check for "Excavator" - 1,000 blocks in one session
        if (data.getSessionBlocksMined() >= 1000 && !data.hasEarnedExcavator()) {
            data.setEarnedExcavator(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lChallenge Complete! §r§eExcavator - Mine 1,000 blocks in one session"));
            PlayerDataManager.saveToDisk(player.getUuid(), data);
        }
    }
    
    public static void onTokensEarned(ServerPlayerEntity player, int tokens) {
        PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
        
        // Check for "Token Millionaire" - 1,000,000 tokens
        if (data.getMiningTokens() >= 1000000 && !data.hasEarnedTokenMillionaire()) {
            data.setEarnedTokenMillionaire(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lChallenge Complete! §r§eToken Millionaire - Earn 1,000,000 mining tokens"));
            PlayerDataManager.saveToDisk(player.getUuid(), data);
        }
    }
    
    public static void onUpgradePurchased(ServerPlayerEntity player) {
        PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
        
        // Check for "Master Miner" - all 4 upgrades
        if (data.getUnlockedUpgrades().size() >= 4 && !data.hasEarnedMasterMiner()) {
            data.setEarnedMasterMiner(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lChallenge Complete! §r§dMaster Miner - Unlock all 4 upgrades"));
            PlayerDataManager.saveToDisk(player.getUuid(), data);
        }
    }
    
    public static void onDiamondMined(ServerPlayerEntity player, Block block) {
        // Check if it's a diamond ore
        String blockId = Registries.BLOCK.getId(block).toString();
        if (blockId.contains("diamond_ore")) {
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            data.addDiamondsMined(1);
            
            // Check for "Diamond Digger" - 100 diamonds
            if (data.getDiamondsMined() >= 100 && !data.hasEarnedDiamondDigger()) {
                data.setEarnedDiamondDigger(true);
                player.sendMessage(net.minecraft.text.Text.literal("§6§lChallenge Complete! §r§aDiamond Digger - Mine 100 diamonds with Area Mine"));
                // Save advancement progress
                PlayerDataManager.saveToDisk(player.getUuid(), data);
            }
        }
    }
}

