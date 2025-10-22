package net.xai.area_enchant;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

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
}

