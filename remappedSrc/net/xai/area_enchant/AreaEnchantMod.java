package net.xai.area_enchant;

import com.google.gson.Gson;
import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AreaEnchantMod implements ModInitializer {
    public static final RegistryKey<net.minecraft.enchantment.Enchantment> AREA_MINE = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("area_enchant", "area_mine"));
    public static Config config;

    @Override
    public void onInitialize() {
        loadConfig();
    }

    private void loadConfig() {
        Path configPath = Paths.get("config/area_enchant.json");
        Gson gson = new Gson();

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = gson.fromJson(json, Config.class);
            } catch (IOException e) {
                e.printStackTrace();
                config = getDefaultConfig();
            }
        } else {
            config = getDefaultConfig();
            try {
                Files.createDirectories(configPath.getParent());
                Files.writeString(configPath, gson.toJson(config));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Config getDefaultConfig() {
        Config defaultConfig = new Config();
        defaultConfig.levels.put(1, new Size(1, 2, 1));
        defaultConfig.levels.put(2, new Size(2, 2, 2));
        defaultConfig.levels.put(3, new Size(3, 3, 3));
        defaultConfig.allowedTools.add("minecraft:iron_pickaxe");
        defaultConfig.allowedTools.add("minecraft:diamond_pickaxe");
        defaultConfig.allowedTools.add("minecraft:netherite_pickaxe");
        return defaultConfig;
    }

    public static class Config {
        public Map<Integer, Size> levels = new HashMap<>();
        public List<String> allowedTools = new ArrayList<>();
    }

    public static class Size {
        public int horizontal;
        public int vertical;
        public int depth;

        public Size(int horizontal, int vertical, int depth) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.depth = depth;
        }
    }
}