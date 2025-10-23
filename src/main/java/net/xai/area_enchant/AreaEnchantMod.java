package net.xai.area_enchant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.xai.area_enchant.mixin.EntityAccessor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AreaEnchantMod implements ModInitializer {
    public static final RegistryKey<net.minecraft.enchantment.Enchantment> AREA_MINE = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("area_enchant", "area_mine"));
    public static Config config;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onInitialize() {
        loadConfig();
        registerCommands();
        TradeOfferHelper.registerVillagerOffers(VillagerProfession.LIBRARIAN, 5, factories -> {
            factories.add((entity, random) -> {
                ServerWorld world = (ServerWorld) ((EntityAccessor) entity).getWorld();
                int level = random.nextInt(3) + 1; // Random level between 1 and 3
                var enchantmentRegistry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
                Optional<RegistryEntry.Reference<net.minecraft.enchantment.Enchantment>> optionalEntry = enchantmentRegistry.getEntry(AREA_MINE.getValue());
                RegistryEntry<net.minecraft.enchantment.Enchantment> entry = optionalEntry.orElse(null);
                if (entry == null) {
                    return null; // Fallback if enchantment not found
                }
                ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
                ItemEnchantmentsComponent.Builder builder = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
                builder.add(entry, level);
                enchantedBook.set(DataComponentTypes.STORED_ENCHANTMENTS, builder.build());
                var emeraldEntry = Registries.ITEM.getEntry(Items.EMERALD);
                var bookEntry = Registries.ITEM.getEntry(Items.BOOK);
                return new TradeOffer(
                        new TradedItem(emeraldEntry.value(), 5), // Buy: 5 emeralds
                        Optional.of(new TradedItem(bookEntry.value(), 1)), // Buy: 1 book
                        enchantedBook, // Sell: Enchanted book with Area Mine
                        12, // Max uses
                        30, // XP reward
                        0.2f // Price multiplier
                );
            });
        });
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("areamine")
                .requires(source -> source.hasPermissionLevel(2)) // Requires OP level 2
                .then(CommandManager.literal("reload")
                    .executes(AreaMineCommand::reload)
                )
                .then(CommandManager.literal("toggle")
                    .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(AreaMineCommand::toggle)
                    )
                )
                .then(CommandManager.literal("stats")
                    .executes(AreaMineCommand::statsAll)
                    .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(AreaMineCommand::stats)
                    )
                )
            );
        });
    }

    public static void reloadConfig() {
        loadConfig();
    }

    private static void loadConfig() {
        Path configDir = Paths.get("config/area-mine");
        Path configPath = configDir.resolve("config.json");

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = gson.fromJson(json, Config.class);
                
                // Config migration system
                if (config.configVersion < 2) {
                    System.out.println("[Area Mine] Migrating config from version " + config.configVersion + " to 2");
                    migrateConfig(config);
                    config.configVersion = 2;
                    // Save migrated config
                    Files.writeString(configPath, gson.toJson(config));
                    System.out.println("[Area Mine] Config migration complete!");
                }
            } catch (IOException e) {
                e.printStackTrace();
                config = getDefaultConfig();
            }
        } else {
            config = getDefaultConfig();
            try {
                Files.createDirectories(configDir);
                Files.writeString(configPath, gson.toJson(config));
                generateConfigGuide(configDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void migrateConfig(Config oldConfig) {
        // Migration from version 0/1 to 2
        // Add new v1.3.0 fields with defaults if they don't exist
        if (oldConfig.enchantmentConflicts == null) {
            oldConfig.enchantmentConflicts = new ArrayList<>();
        }
        // Set new defaults for v1.3.0 features
        oldConfig.maxBlocksPerActivation = 100;
        oldConfig.preventToolBreaking = true;
        oldConfig.durabilityWarning = true;
        oldConfig.durabilityWarningThreshold = 20;
        oldConfig.actionBarFeedback = true;
        oldConfig.creativeModeBypass = true;
        oldConfig.checkWorldProtection = true;
    }

    private static void generateConfigGuide(Path configDir) {
        try {
            String guide = """
                ================================================================================
                            Area Mine Enchantment - Configuration Guide
                ================================================================================
                
                This file explains all configuration options in config.json
                Edit config.json, then use /areamine reload to apply changes.
                
                --------------------------------------------------------------------------------
                MINING AREA DIMENSIONS
                --------------------------------------------------------------------------------
                levels: Defines mining area size for each enchantment level
                  - horizontal: Width left/right of target block (default: 1, 2, 3)
                  - vertical: Height up/down from target block (default: 2, 2, 3)
                  - depth: Distance forward into wall (default: 1, 2, 3)
                
                Example: Level 2 with {horizontal: 2, vertical: 2, depth: 2}
                         mines a 2×2×2 cube of blocks
                
                --------------------------------------------------------------------------------
                TOOL CONFIGURATION
                --------------------------------------------------------------------------------
                allowedTools: List of tools that can receive the enchantment
                  Default: ["minecraft:iron_pickaxe", "minecraft:diamond_pickaxe", 
                            "minecraft:netherite_pickaxe"]
                  
                enableAxeSupport: Allow enchantment on axes (default: false)
                enableShovelSupport: Allow enchantment on shovels (default: false)
                enableHoeSupport: Allow enchantment on hoes (default: false)
                
                --------------------------------------------------------------------------------
                BLOCK FILTERING
                --------------------------------------------------------------------------------
                blockBlacklist: Blocks that CANNOT be area-mined
                  Default: [] (empty - no restrictions)
                  Example: ["minecraft:spawner", "minecraft:bedrock"]
                  
                blockWhitelist: If set, ONLY these blocks can be area-mined
                  Default: [] (empty - all blocks allowed)
                  Example: ["minecraft:stone", "minecraft:deepslate"]
                  Note: When whitelist is used, blacklist is ignored
                
                --------------------------------------------------------------------------------
                GAMEPLAY FEATURES (All disabled by default)
                --------------------------------------------------------------------------------
                autoPickup: Automatically collect mined blocks to inventory
                  Default: false
                  When true: Blocks go directly to player inventory
                  When false: Blocks drop normally
                
                veinMiningMode: Only mine blocks of the same type
                  Default: false
                  When true: Only mines blocks matching the one you broke
                  Perfect for: Mining ore veins efficiently
                
                respectFortuneAndSilkTouch: Apply Fortune/Silk Touch to area-mined blocks
                  Default: true ✅ ENABLED
                  When true: Fortune/Silk Touch affects all blocks in the area
                  When false: Only the target block gets enchantment bonuses
                
                --------------------------------------------------------------------------------
                BALANCE & COST SYSTEMS (All disabled/0 by default)
                --------------------------------------------------------------------------------
                durabilityScaling: Each mined block damages the tool
                  Default: false
                  When true: Tool loses 1 durability per block mined
                  When false: Tool loses 1 durability per area mine use (original behavior)
                
                xpCostPerBlock: XP cost per block mined
                  Default: 0 (free)
                  Example: 1 = costs 1 XP per block
                  Note: Player must have enough XP or mining will be cancelled
                
                cooldownTicks: Cooldown between area mine uses
                  Default: 0 (no cooldown)
                  Example: 20 = 1 second, 100 = 5 seconds
                  Note: 20 ticks = 1 second in Minecraft
                
                --------------------------------------------------------------------------------
                VISUAL & AUDIO EFFECTS (All disabled by default)
                --------------------------------------------------------------------------------
                particleEffects: Show particles on blocks before mining
                  Default: false
                  When true: Displays enchantment particles on target blocks
                
                soundEffects: Play sound when area mining activates
                  Default: false
                  When true: Plays level-up sound on activation
                
                --------------------------------------------------------------------------------
                MINING PATTERNS
                --------------------------------------------------------------------------------
                miningPattern: Shape of the mining area
                  Default: "cube"
                  Available: "cube" (more patterns coming in future updates)
                
                --------------------------------------------------------------------------------
                ADMIN & PERMISSIONS
                --------------------------------------------------------------------------------
                permissionsEnabled: Enable permission system integration
                  Default: false
                  Note: Currently a placeholder for future permission mod support
                
                ================================================================================
                EXAMPLES - Common Configurations
                ================================================================================
                
                [Balanced Survival Server]
                {
                  "xpCostPerBlock": 1,
                  "durabilityScaling": true,
                  "cooldownTicks": 60,
                  "veinMiningMode": true,
                  "particleEffects": true
                }
                
                [Creative/Peaceful Server]
                {
                  "autoPickup": true,
                  "particleEffects": true,
                  "soundEffects": true
                }
                
                [Hardcore/Difficult Server]
                {
                  "xpCostPerBlock": 5,
                  "durabilityScaling": true,
                  "cooldownTicks": 200,
                  "blockBlacklist": ["minecraft:ancient_debris", "minecraft:diamond_ore"]
                }
                
                ================================================================================
                For more help, visit: https://github.com/ModdedWolf/area-mine
                ================================================================================
                """;
            
            Path guidePath = configDir.resolve("CONFIG_GUIDE.txt");
            Files.writeString(guidePath, guide);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Config getDefaultConfig() {
        Config defaultConfig = new Config();
        defaultConfig.levels.put(1, new Size(1, 2, 1));
        defaultConfig.levels.put(2, new Size(2, 2, 2));
        defaultConfig.levels.put(3, new Size(3, 3, 3));
        defaultConfig.allowedTools.add("minecraft:iron_pickaxe");
        defaultConfig.allowedTools.add("minecraft:diamond_pickaxe");
        defaultConfig.allowedTools.add("minecraft:netherite_pickaxe");
        
        // Feature defaults (keep current behavior)
        defaultConfig.autoPickup = false;
        defaultConfig.veinMiningMode = false;
        defaultConfig.durabilityScaling = false;
        defaultConfig.xpCostPerBlock = 0;
        defaultConfig.cooldownTicks = 0;
        defaultConfig.miningPattern = "cube";
        defaultConfig.particleEffects = false;
        defaultConfig.soundEffects = false;
        defaultConfig.permissionsEnabled = false;
        defaultConfig.respectFortuneAndSilkTouch = true; // ENABLED by default
        
        // Tool type support
        defaultConfig.enableAxeSupport = false;
        defaultConfig.enableShovelSupport = false;
        defaultConfig.enableHoeSupport = false;
        
        // New v1.3.0 features
        defaultConfig.maxBlocksPerActivation = 100;
        defaultConfig.preventToolBreaking = true;
        defaultConfig.durabilityWarning = true;
        defaultConfig.durabilityWarningThreshold = 20;
        defaultConfig.actionBarFeedback = true;
        defaultConfig.creativeModeBypass = true;
        defaultConfig.checkWorldProtection = true;
        // enchantmentConflicts is already initialized as empty ArrayList
        
        return defaultConfig;
    }

    public static class Config {
        public int configVersion = 2; // Version tracking for migrations
        
        public Map<Integer, Size> levels = new HashMap<>();
        public List<String> allowedTools = new ArrayList<>();
        public List<String> blockBlacklist = new ArrayList<>();
        public List<String> blockWhitelist = new ArrayList<>();
        
        // Features
        public boolean autoPickup = false;
        public boolean veinMiningMode = false;
        public boolean durabilityScaling = false;
        public int xpCostPerBlock = 0;
        public int cooldownTicks = 0;
        public String miningPattern = "cube";
        public boolean particleEffects = false;
        public boolean soundEffects = false;
        public boolean permissionsEnabled = false;
        public boolean respectFortuneAndSilkTouch = true;
        
        // Tool types
        public boolean enableAxeSupport = false;
        public boolean enableShovelSupport = false;
        public boolean enableHoeSupport = false;
        
        // New v1.3.0 features
        public int maxBlocksPerActivation = 100;
        public boolean preventToolBreaking = true;
        public boolean durabilityWarning = true;
        public int durabilityWarningThreshold = 20;
        public boolean actionBarFeedback = true;
        public boolean creativeModeBypass = true;
        public List<String> enchantmentConflicts = new ArrayList<>();
        public boolean checkWorldProtection = true;
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