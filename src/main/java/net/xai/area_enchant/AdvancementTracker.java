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
        
        // Check for "Efficiency Expert" - 1000 blocks
        if (data.getBlocksMined() >= 1000 && !data.hasEarnedEfficiencyExpert()) {
            data.setEarnedEfficiencyExpert(true);
            player.sendMessage(net.minecraft.text.Text.literal("§6§lAdvancement Made! §r§aEfficiency Expert - Mine 1,000 blocks with Area Mine"));
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
            }
        }
    }
}

