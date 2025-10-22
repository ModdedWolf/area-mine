package net.xai.area_enchant;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.Map;
import java.util.UUID;

public class AreaMineCommand {
    
    public static int reload(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            AreaEnchantMod.reloadConfig();
            source.sendFeedback(() -> Text.literal("§a[Area Mine] Config reloaded successfully!"), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Failed to reload config: " + e.getMessage()), true);
            return 0;
        }
    }
    
    public static int toggle(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            PlayerDataManager.PlayerData data = PlayerDataManager.get(target.getUuid());
            data.setDisabled(!data.isDisabled());
            
            String status = data.isDisabled() ? "§cdisabled" : "§aenabled";
            source.sendFeedback(() -> Text.literal("§e[Area Mine] Area Mine is now " + status + " §efor " + target.getName().getString()), true);
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int stats(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
            PlayerDataManager.PlayerData data = PlayerDataManager.get(target.getUuid());
            
            source.sendFeedback(() -> Text.literal("§e[Area Mine] Stats for " + target.getName().getString() + ":"), false);
            source.sendFeedback(() -> Text.literal("  §7Blocks mined: §f" + data.getBlocksMined()), false);
            source.sendFeedback(() -> Text.literal("  §7Times used: §f" + data.getTimesUsed()), false);
            source.sendFeedback(() -> Text.literal("  §7Status: " + (data.isDisabled() ? "§cDisabled" : "§aEnabled")), false);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int statsAll(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        Map<UUID, PlayerDataManager.PlayerData> allData = PlayerDataManager.getAllData();
        
        if (allData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[Area Mine] No player data recorded yet."), false);
            return 0;
        }
        
        source.sendFeedback(() -> Text.literal("§e[Area Mine] All Player Stats:"), false);
        
        final int[] totalBlocks = {0};
        final int[] totalUses = {0};
        
        for (Map.Entry<UUID, PlayerDataManager.PlayerData> entry : allData.entrySet()) {
            PlayerDataManager.PlayerData data = entry.getValue();
            totalBlocks[0] += data.getBlocksMined();
            totalUses[0] += data.getTimesUsed();
        }
        
        source.sendFeedback(() -> Text.literal("  §7Total blocks mined: §f" + totalBlocks[0]), false);
        source.sendFeedback(() -> Text.literal("  §7Total uses: §f" + totalUses[0]), false);
        source.sendFeedback(() -> Text.literal("  §7Players tracked: §f" + allData.size()), false);
        
        return Command.SINGLE_SUCCESS;
    }
}

