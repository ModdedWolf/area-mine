package net.xai.area_enchant;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.*;


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
    
    public static int undo(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            PlayerDataManager.UndoData undoData = data.getLastOperation();
            
            if (undoData == null || undoData.blocks.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] No operation to undo!"), false);
                return 0;
            }
            
            // Check if undo is too old (more than 5 minutes)
            if (System.currentTimeMillis() - undoData.timestamp > 300000) {
                data.clearLastOperation();
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Undo expired (too old)!"), false);
                return 0;
            }
            
            net.minecraft.server.world.ServerWorld world = source.getWorld();
            
            // Remove item entities that were dropped on the ground
            int removedEntities = 0;
            for (java.util.UUID entityId : undoData.itemEntities) {
                net.minecraft.entity.Entity entity = world.getEntity(entityId);
                if (entity instanceof net.minecraft.entity.ItemEntity) {
                    entity.remove(net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    removedEntities++;
                }
            }
            
            // Remove items from player inventory (tracked items)
            int removedFromInventory = 0;
            for (Map.Entry<String, Integer> entry : undoData.inventoryItems.entrySet()) {
                String itemId = entry.getKey();
                int count = entry.getValue();
                
                net.minecraft.util.Identifier identifier = net.minecraft.util.Identifier.of(itemId);
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(identifier);
                
                if (item != null) {
                    int removed = 0;
                    for (int i = 0; i < player.getInventory().size() && removed < count; i++) {
                        ItemStack stack = player.getInventory().getStack(i);
                        if (stack.getItem() == item) {
                            int toRemove = Math.min(stack.getCount(), count - removed);
                            stack.decrement(toRemove);
                            removed += toRemove;
                            removedFromInventory += toRemove;
                        }
                    }
                }
            }
            
            // Restore blocks
            int restored = 0;
            for (PlayerDataManager.UndoData.BlockStateData blockData : undoData.blocks) {
                // Restore the block
                world.setBlockState(blockData.pos, blockData.state);
                restored++;
            }
            
            data.clearLastOperation();
            final int finalRestored = restored;
            final int finalRemovedEntities = removedEntities;
            final int finalRemovedInventory = removedFromInventory;
            source.sendFeedback(() -> Text.literal("§a[Area Mine] Restored " + finalRestored + " blocks! Removed " + 
                finalRemovedEntities + " dropped items and " + finalRemovedInventory + " items from inventory."), false);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            e.printStackTrace();
            return 0;
        }
    }
    
    public static int leaderboard(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        String type = StringArgumentType.getString(context, "type");
        
        Map<UUID, PlayerDataManager.PlayerData> allData = PlayerDataManager.getAllData();
        if (allData.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§e[Area Mine] No player data recorded yet."), false);
            return 0;
        }
        
        List<Map.Entry<UUID, PlayerDataManager.PlayerData>> sorted = new ArrayList<>(allData.entrySet());
        
        switch (type.toLowerCase()) {
            case "blocks" -> sorted.sort((a, b) -> 
                Integer.compare(b.getValue().getBlocksMined(), a.getValue().getBlocksMined()));
            case "diamonds" -> sorted.sort((a, b) -> 
                Integer.compare(b.getValue().getDiamondsMined(), a.getValue().getDiamondsMined()));
            case "tokens" -> sorted.sort((a, b) -> 
                Integer.compare(b.getValue().getMiningTokens(), a.getValue().getMiningTokens()));
            default -> {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Invalid type! Use: blocks, diamonds, or tokens"), false);
                return 0;
            }
        }
        
        source.sendFeedback(() -> Text.literal("§e[Area Mine] Leaderboard - " + type.toUpperCase()), false);
        
        int rank = 1;
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            Map.Entry<UUID, PlayerDataManager.PlayerData> entry = sorted.get(i);
            PlayerDataManager.PlayerData data = entry.getValue();
            UUID playerId = entry.getKey();
            
            // Get player name from server
            String playerName = playerId.toString();
            try {
                var server = source.getServer();
                var playerEntity = server.getPlayerManager().getPlayer(playerId);
                if (playerEntity != null) {
                    playerName = playerEntity.getName().getString();
                }
                // If player is offline, show first 8 chars of UUID
                if (playerName.equals(playerId.toString())) {
                    playerName = playerId.toString().substring(0, 8) + "...";
                }
            } catch (Exception e) {
                // Fallback to shortened UUID if name lookup fails
                playerName = playerId.toString().substring(0, 8) + "...";
            }
            
            int value = switch (type.toLowerCase()) {
                case "blocks" -> data.getBlocksMined();
                case "diamonds" -> data.getDiamondsMined();
                case "tokens" -> data.getMiningTokens();
                default -> 0;
            };
            
            final int finalRank = rank;
            final int finalValue = value;
            final String finalPlayerName = playerName;
            source.sendFeedback(() -> Text.literal("  §7" + finalRank + ". §f" + finalPlayerName + " §7- §a" + finalValue), false);
            rank++;
        }
        
        return Command.SINGLE_SUCCESS;
    }
    
    public static int upgrade(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            String upgradeName = StringArgumentType.getString(context, "upgrade");
            
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            
            if (data.hasUpgrade(upgradeName)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] You already have this upgrade!"), false);
                return 0;
            }
            
            Integer cost = AreaEnchantMod.config.upgradeCosts.get(upgradeName);
            if (cost == null) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Unknown upgrade! Available: " + 
                    String.join(", ", AreaEnchantMod.config.upgradeCosts.keySet())), false);
                return 0;
            }
            
            if (!data.spendTokens(cost)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Not enough tokens! Need " + cost + ", have " + data.getMiningTokens()), false);
                return 0;
            }
            
            data.unlockUpgrade(upgradeName);
            source.sendFeedback(() -> Text.literal("§a[Area Mine] Unlocked upgrade: " + upgradeName + "!"), false);
            
            // Check achievement
            AdvancementTracker.onUpgradePurchased(player);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int listUpgrades(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            
            source.sendFeedback(() -> Text.literal("§e[Area Mine] Available Upgrades (Tokens: §a" + data.getMiningTokens() + "§e):"), false);
            
            for (Map.Entry<String, Integer> entry : AreaEnchantMod.config.upgradeCosts.entrySet()) {
                String upgradeName = entry.getKey();
                int cost = entry.getValue();
                boolean owned = data.hasUpgrade(upgradeName);
                
                if (owned) {
                    source.sendFeedback(() -> Text.literal("  §a✓ §f" + upgradeName + " §7(Cost: " + cost + " tokens) §a- OWNED"), false);
                } else {
                    source.sendFeedback(() -> Text.literal("  §7○ §f" + upgradeName + " §7- Cost: §e" + cost + " tokens"), false);
                }
            }
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int detailedStats(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            
            source.sendFeedback(() -> Text.literal("§e[Area Mine] Detailed Stats:"), false);
            source.sendFeedback(() -> Text.literal("  §7Blocks mined: §f" + data.getBlocksMined()), false);
            source.sendFeedback(() -> Text.literal("  §7Times used: §f" + data.getTimesUsed()), false);
            source.sendFeedback(() -> Text.literal("  §7Mining tokens: §a" + data.getMiningTokens()), false);
            source.sendFeedback(() -> Text.literal("  §7Status: " + (data.isDisabled() ? "§cDisabled" : "§aEnabled")), false);
            
            // Top 5 mined blocks
            if (!data.getBlockTypeStats().isEmpty()) {
                source.sendFeedback(() -> Text.literal("  §e Top Mined Blocks:"), false);
                data.getBlockTypeStats().entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                    .limit(5)
                    .forEach(entry -> {
                        source.sendFeedback(() -> Text.literal("    §7" + entry.getKey() + ": §f" + entry.getValue()), false);
                    });
            }
            
            // Dimension stats
            if (!data.getDimensionStats().isEmpty()) {
                source.sendFeedback(() -> Text.literal("  §e Dimension Stats:"), false);
                data.getDimensionStats().forEach((dim, count) -> {
                    source.sendFeedback(() -> Text.literal("    §7" + dim + ": §f" + count + " blocks"), false);
                });
            }
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int setPattern(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            String pattern = StringArgumentType.getString(context, "pattern").toLowerCase();
            
            // Validate pattern
            List<String> validPatterns = Arrays.asList("cube", "sphere", "tunnel", "cross", "layer", "vertical");
            if (!validPatterns.contains(pattern)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Invalid pattern! Valid patterns: cube, sphere, tunnel, cross, layer, vertical"), false);
                return 0;
            }
            
            // Update config
            AreaEnchantMod.config.miningPattern = pattern;
            
            // Save config to disk
            AreaEnchantMod.saveConfig();
            
            // Sync pattern to all online players
            net.xai.area_enchant.network.NetworkHandler.sendPatternToAllPlayers(source.getServer(), pattern);
            
            // Send confirmation with pattern details
            String description = switch (pattern) {
                case "cube" -> "Traditional 3×3×3 area mining";
                case "sphere" -> "Spherical radius mining";
                case "tunnel" -> "Long corridor forward";
                case "cross" -> "+ shape for ore finding";
                case "layer" -> "Flat horizontal layers";
                case "vertical" -> "Straight up/down shaft";
                default -> pattern;
            };
            
            source.sendFeedback(() -> Text.literal("§a[Area Mine] Mining pattern changed to: §e" + pattern), true);
            source.sendFeedback(() -> Text.literal("§7" + description), false);
            source.sendFeedback(() -> Text.literal("§7Config saved automatically!"), false);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int listPatterns(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            data.ensureDefaultPattern();
            
            source.sendFeedback(() -> Text.literal("§6§l[Area Mine] Available Patterns"), false);
            source.sendFeedback(() -> Text.literal("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
            
            // List all patterns with unlock status
            Map<String, String> patterns = new LinkedHashMap<>();
            patterns.put("cube", "Traditional 3×3×3 area mining");
            patterns.put("sphere", "Spherical radius mining");
            patterns.put("tunnel", "Long corridor forward");
            patterns.put("cross", "+ shape for ore finding");
            patterns.put("layer", "Flat horizontal layers");
            patterns.put("vertical", "Straight up/down shaft");
            
            for (Map.Entry<String, String> entry : patterns.entrySet()) {
                String patternName = entry.getKey();
                String desc = entry.getValue();
                boolean unlocked = data.hasPattern(patternName);
                int cost = AreaEnchantMod.config.patternCosts.getOrDefault(patternName, 0);
                
                if (patternName.equals("cube")) {
                    source.sendFeedback(() -> Text.literal("§a✓ §e" + patternName + " §7- " + desc + " §a(FREE)"), false);
                } else if (unlocked) {
                    source.sendFeedback(() -> Text.literal("§a✓ §e" + patternName + " §7- " + desc + " §a(UNLOCKED)"), false);
                } else {
                    source.sendFeedback(() -> Text.literal("§c✗ §7" + patternName + " §7- " + desc + " §c(Locked - " + String.format("%,d", cost) + " tokens)"), false);
                }
            }
            
            source.sendFeedback(() -> Text.literal("§e━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"), false);
            source.sendFeedback(() -> Text.literal("§7Your tokens: §a" + String.format("%,d", data.getMiningTokens())), false);
            source.sendFeedback(() -> Text.literal("§7Use §e/areamine patterns buy <pattern> §7to unlock"), false);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
    
    public static int buyPattern(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        try {
            ServerPlayerEntity player = source.getPlayerOrThrow();
            PlayerDataManager.PlayerData data = PlayerDataManager.get(player.getUuid());
            data.ensureDefaultPattern();
            
            String pattern = StringArgumentType.getString(context, "pattern").toLowerCase();
            
            // Validate pattern
            List<String> validPatterns = Arrays.asList("sphere", "tunnel", "cross", "layer", "vertical");
            if (!validPatterns.contains(pattern)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Invalid pattern! Available for purchase: sphere, tunnel, cross, layer, vertical"), false);
                return 0;
            }
            
            // Check if already unlocked
            if (data.hasPattern(pattern)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] You already have this pattern unlocked!"), false);
                return 0;
            }
            
            // Get cost
            int cost = AreaEnchantMod.config.patternCosts.getOrDefault(pattern, 0);
            if (cost == 0) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] This pattern cannot be purchased (no cost set)."), false);
                return 0;
            }
            
            // Check if player has enough tokens
            if (data.getMiningTokens() < cost) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Not enough tokens! Need " + String.format("%,d", cost) + ", have " + String.format("%,d", data.getMiningTokens())), false);
                return 0;
            }
            
            // Purchase pattern
            if (!data.spendTokens(cost)) {
                source.sendFeedback(() -> Text.literal("§c[Area Mine] Failed to purchase pattern."), false);
                return 0;
            }
            
            data.unlockPattern(pattern);
            PlayerDataManager.saveToDisk(player.getUuid(), data);
            
            String description = switch (pattern) {
                case "sphere" -> "Spherical radius mining";
                case "tunnel" -> "Long corridor forward";
                case "cross" -> "+ shape for ore finding";
                case "layer" -> "Flat horizontal layers";
                case "vertical" -> "Straight up/down shaft";
                default -> pattern;
            };
            
            source.sendFeedback(() -> Text.literal("§a§l[Area Mine] Pattern Unlocked!"), true);
            source.sendFeedback(() -> Text.literal("§e" + pattern + " §7- " + description), false);
            source.sendFeedback(() -> Text.literal("§7Cost: §c-" + String.format("%,d", cost) + " tokens"), false);
            source.sendFeedback(() -> Text.literal("§7Remaining tokens: §a" + String.format("%,d", data.getMiningTokens())), false);
            source.sendFeedback(() -> Text.literal("§7Use §e/areamine pattern " + pattern + " §7to activate it!"), false);
            
            return Command.SINGLE_SUCCESS;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Area Mine] Error: " + e.getMessage()), false);
            return 0;
        }
    }
}

