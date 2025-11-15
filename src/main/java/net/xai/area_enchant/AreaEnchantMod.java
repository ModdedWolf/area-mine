package net.xai.area_enchant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AreaEnchantMod implements ModInitializer {
    public static final RegistryKey<net.minecraft.enchantment.Enchantment> AREA_MINE = RegistryKey.of(RegistryKeys.ENCHANTMENT, Identifier.of("area_enchant", "area_mine"));
    public static Config config;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Environment detection
    private static boolean isSingleplayer = false;
    private static boolean isDedicatedServer = false;
    
    /**
     * Check if running in singleplayer (integrated server)
     */
    public static boolean isSingleplayer() {
        return isSingleplayer;
    }
    
    /**
     * Check if running on dedicated server
     */
    public static boolean isDedicatedServer() {
        return isDedicatedServer;
    }
    
    /**
     * Check if running in any server environment (singleplayer or dedicated)
     */
    public static boolean isServer() {
        return isSingleplayer || isDedicatedServer;
    }

    @Override
    public void onInitialize() {
        System.out.println("[Area Mine] Mod initializing - mixins should be loaded");
        
        // Detect environment: singleplayer (integrated server) vs dedicated server
        // The mod initializer runs on both client and server in singleplayer
        // We can check if we're in a client environment by checking if the client mod initializer exists
        // For now, assume singleplayer if we're not explicitly on a dedicated server
        // In singleplayer, both client and server mods load, so this will be true
        isSingleplayer = true; // Default to singleplayer for now
        System.out.println("[Area Mine] Running in SINGLEPLAYER mode (integrated server)");
        
        loadConfig();
        registerCommands();
        registerServerEvents();
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
        
        // Register networking for client-server pattern sync
        registerNetworking();
    }
    
    private void registerNetworking() {
        // Register network packets for client-server sync
        net.xai.area_enchant.network.NetworkHandler.registerPackets();
        
        // Send pattern to players when they join
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Delay sync to ensure client is ready (especially important for singleplayer)
            server.execute(() -> {
                // Small delay to ensure client receiver is registered
                server.execute(() -> {
                    // Try to send pattern via network
                    // In singleplayer, if this fails, it's fine because config is shared
                    net.xai.area_enchant.network.NetworkHandler.sendPatternToClient(handler.player, config.miningPattern);
                });
            });
        });
    }

    private void registerServerEvents() {
        // Register event-based area mining handler (replaces mixin approach)
        AreaMineHandler.register();
        
        // Reset session blocks on player disconnect
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerDataManager.PlayerData data = PlayerDataManager.get(handler.player.getUuid());
            data.resetSessionBlocks();
            PlayerDataManager.saveToDisk(handler.player.getUuid(), data);
        });
        
        // Save player data when server stops
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            System.out.println("[Area Mine] Saving player data...");
            PlayerDataManager.saveAll();
            System.out.println("[Area Mine] Player data saved!");
        });
        
        // Server tick event for batched block breaking
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (AreaEnchantMod.config.batchedBreaking) {
                BatchedBlockBreaker.tick();
            }
        });
        
        // Periodically save player data (every 5 minutes) and set world save directory
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            // Set world-specific save directory for player data
            try {
                // Get the world save directory - use the server's run directory
                Path serverDir = server.getRunDirectory();
                // For singleplayer, worlds are in saves/ directory
                // For dedicated server, the world is in the server directory
                Path worldSaveDir;
                if (server.isDedicated()) {
                    // Dedicated server: world is in the server directory
                    worldSaveDir = serverDir;
                } else {
                    // Singleplayer: worlds are in saves/ directory
                    // Get the world name from save properties
                    String worldName = server.getSaveProperties().getLevelName();
                    worldSaveDir = serverDir.resolve("saves").resolve(worldName);
                }
                PlayerDataManager.setWorldSaveDirectory(worldSaveDir);
            } catch (Exception e) {
                System.err.println("[Area Mine] Failed to get world save directory: " + e.getMessage());
                e.printStackTrace();
                // Fallback to default
                PlayerDataManager.setWorldSaveDirectory(Paths.get("world"));
            }
            
            server.getWorlds().forEach(world -> {
                // Schedule periodic saves using server tick scheduler
                world.getServer().execute(() -> {
                    new Thread(() -> {
                        while (server.isRunning()) {
                            try {
                                Thread.sleep(300000); // 5 minutes
                                PlayerDataManager.saveAll();
                                System.out.println("[Area Mine] Auto-saved player data");
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }).start();
                });
            });
        });
    }
    
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("areamine")
                .then(CommandManager.literal("reload")
                    .requires(source -> source.hasPermissionLevel(2))
                    .executes(AreaMineCommand::reload)
                )
                .then(CommandManager.literal("toggle")
                    .requires(source -> source.hasPermissionLevel(2))
                    .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                        .executes(AreaMineCommand::toggle)
                    )
                )
                .then(CommandManager.literal("stats")
                    .executes(AreaMineCommand::detailedStats)
                    .then(CommandManager.argument("player", net.minecraft.command.argument.EntityArgumentType.player())
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(AreaMineCommand::stats)
                    )
                    .then(CommandManager.literal("all")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(AreaMineCommand::statsAll)
                    )
                )
                .then(CommandManager.literal("undo")
                    .executes(AreaMineCommand::undo)
                )
                .then(CommandManager.literal("leaderboard")
                    .then(CommandManager.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            builder.suggest("blocks");
                            builder.suggest("diamonds");
                            builder.suggest("tokens");
                            return builder.buildFuture();
                        })
                        .executes(AreaMineCommand::leaderboard)
                    )
                )
                .then(CommandManager.literal("upgrades")
                    .executes(AreaMineCommand::listUpgrades)
                    .then(CommandManager.literal("buy")
                        .then(CommandManager.argument("upgrade", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((context, builder) -> {
                                // Suggest upgrade names with costs
                                builder.suggest("radius_boost", net.minecraft.text.Text.literal("Cost: 100,000 tokens"));
                                builder.suggest("efficiency_boost", net.minecraft.text.Text.literal("Cost: 150,000 tokens"));
                                builder.suggest("durability_boost", net.minecraft.text.Text.literal("Cost: 200,000 tokens"));
                                builder.suggest("auto_pickup", net.minecraft.text.Text.literal("Cost: 250,000 tokens"));
                                return builder.buildFuture();
                            })
                            .executes(AreaMineCommand::upgrade)
                        )
                    )
                )
                .then(CommandManager.literal("pattern")
                    .then(CommandManager.argument("pattern", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((context, builder) -> {
                            // Suggest all available patterns
                            builder.suggest("cube", net.minecraft.text.Text.literal("Traditional 3×3×3 area mining"));
                            builder.suggest("sphere", net.minecraft.text.Text.literal("Spherical radius mining"));
                            builder.suggest("tunnel", net.minecraft.text.Text.literal("Long corridor forward"));
                            builder.suggest("cross", net.minecraft.text.Text.literal("+ shape for ore finding"));
                            builder.suggest("layer", net.minecraft.text.Text.literal("Flat horizontal layers"));
                            builder.suggest("vertical", net.minecraft.text.Text.literal("Straight up/down shaft"));
                            return builder.buildFuture();
                        })
                        .executes(AreaMineCommand::setPattern)
                    )
                )
                .then(CommandManager.literal("patterns")
                    .executes(AreaMineCommand::listPatterns)
                    .then(CommandManager.literal("buy")
                        .then(CommandManager.argument("pattern", com.mojang.brigadier.arguments.StringArgumentType.word())
                            .suggests((context, builder) -> {
                                // Suggest patterns with costs
                                builder.suggest("sphere", net.minecraft.text.Text.literal("Cost: 1,000,000 tokens"));
                                builder.suggest("tunnel", net.minecraft.text.Text.literal("Cost: 2,500,000 tokens"));
                                builder.suggest("cross", net.minecraft.text.Text.literal("Cost: 5,000,000 tokens"));
                                builder.suggest("layer", net.minecraft.text.Text.literal("Cost: 10,000,000 tokens"));
                                builder.suggest("vertical", net.minecraft.text.Text.literal("Cost: 25,000,000 tokens"));
                                return builder.buildFuture();
                            })
                            .executes(AreaMineCommand::buyPattern)
                        )
                    )
                )
            );
        });
    }

    public static void reloadConfig() {
        loadConfig();
    }
    
    public static void saveConfig() {
        try {
            Path configDir = Paths.get("config/area-mine");
            Path configPath = configDir.resolve("config.json");
            Files.writeString(configPath, gson.toJson(config));
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to save config: " + e.getMessage());
        }
    }

    private static void loadConfig() {
        Path configDir = Paths.get("config/area-mine");
        Path configPath = configDir.resolve("config.json");

        if (Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                config = gson.fromJson(json, Config.class);
                
                // Config migration system
                if (config.configVersion < 4) {
                    System.out.println("[Area Mine] Migrating config from version " + config.configVersion + " to 4");
                    migrateConfig(config);
                    config.configVersion = 4;
                    // Save migrated config
                    Files.writeString(configPath, gson.toJson(config));
                    System.out.println("[Area Mine] Config migration complete!");
                }
            } catch (Exception e) {
                System.err.println("[Area Mine] ERROR: Config file is corrupted or malformed!");
                System.err.println("[Area Mine] Creating backup and generating new config...");
                e.printStackTrace();
                
                // Backup the corrupted config
                try {
                    Path backupPath = configDir.resolve("config.json.backup");
                    Files.move(configPath, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("[Area Mine] Corrupted config backed up to: " + backupPath);
                } catch (IOException backupError) {
                    System.err.println("[Area Mine] Failed to backup config: " + backupError.getMessage());
                }
                
                // Generate new default config
                config = getDefaultConfig();
                try {
                    Files.writeString(configPath, gson.toJson(config));
                    generateConfigGuide(configDir);
                    System.out.println("[Area Mine] New default config created successfully!");
                } catch (IOException writeError) {
                    System.err.println("[Area Mine] Failed to write new config: " + writeError.getMessage());
                }
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
        oldConfig.actionBarFeedback = false; // Disabled by default
        oldConfig.sendTokensInChat = true; // Use chat instead
        oldConfig.creativeModeBypass = true;
        oldConfig.checkWorldProtection = true;
        
        // V3.0.0 features - Major overhaul
        oldConfig.durabilityScaling = true;
        oldConfig.durabilityMultiplier = 0.5;
        oldConfig.filterOresOnly = false;
        oldConfig.filterStoneOnly = false;
        oldConfig.filterIgnoreDirtGravel = false;
        oldConfig.filterValuableOnly = false;
        oldConfig.enableEnchantmentSynergies = true;
        oldConfig.efficiencySpeedBonus = 0.2;
        oldConfig.unbreakingDurabilityReduction = 0.15;
        oldConfig.enableUpgradeSystem = true;
        oldConfig.miningTokensPerBlock = 1;
        if (oldConfig.upgradeCosts == null) {
            oldConfig.upgradeCosts = new HashMap<>();
            oldConfig.upgradeCosts.put("radius_boost", 100000);
            oldConfig.upgradeCosts.put("efficiency_boost", 150000);
            oldConfig.upgradeCosts.put("durability_boost", 200000);
            oldConfig.upgradeCosts.put("auto_pickup", 250000);
        }
        
        // V4.0.0 features - Crouch activation, per-pattern configs, pickaxe-only blocks, silk touch incompatibility
        oldConfig.requireCrouch = true; // Default: require crouch
        oldConfig.pickaxeEffectiveOnly = true; // Default: only mine pickaxe-effective blocks
        
        // Add per-pattern tier sizes
        if (oldConfig.patternLevels == null || oldConfig.patternLevels.isEmpty()) {
            oldConfig.patternLevels = new HashMap<>();
            oldConfig.patternLevels.put("cube", Map.of(1, new Size(1, 2, 1), 2, new Size(2, 2, 2), 3, new Size(3, 3, 3)));
            oldConfig.patternLevels.put("sphere", Map.of(1, new Size(1, 1, 1), 2, new Size(2, 2, 2), 3, new Size(3, 3, 3)));
            oldConfig.patternLevels.put("tunnel", Map.of(1, new Size(1, 2, 5), 2, new Size(2, 2, 8), 3, new Size(3, 3, 12)));
            oldConfig.patternLevels.put("cross", Map.of(1, new Size(3, 1, 3), 2, new Size(5, 2, 5), 3, new Size(7, 3, 7)));
            oldConfig.patternLevels.put("layer", Map.of(1, new Size(3, 1, 3), 2, new Size(5, 1, 5), 3, new Size(7, 1, 7)));
            oldConfig.patternLevels.put("vertical", Map.of(1, new Size(1, 5, 1), 2, new Size(1, 8, 1), 3, new Size(1, 12, 1)));
        }
        
        // Add pattern costs for unlocking patterns (v4.0.0)
        if (oldConfig.patternCosts == null || oldConfig.patternCosts.isEmpty()) {
            oldConfig.patternCosts = new HashMap<>();
            oldConfig.patternCosts.put("sphere", 1000000);
            oldConfig.patternCosts.put("tunnel", 2500000);
            oldConfig.patternCosts.put("cross", 5000000);
            oldConfig.patternCosts.put("layer", 10000000);
            oldConfig.patternCosts.put("vertical", 25000000);
        }
        
        // Add Silk Touch to conflicts if not already present
        if (!oldConfig.enchantmentConflicts.contains("minecraft:silk_touch")) {
            oldConfig.enchantmentConflicts.add("minecraft:silk_touch");
        }
        
        // Update config version
        oldConfig.configVersion = 4;
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
        
        // Per-pattern tier sizes (v4)
        defaultConfig.patternLevels.put("cube", Map.of(1, new Size(1, 2, 1), 2, new Size(2, 2, 2), 3, new Size(3, 3, 3)));
        defaultConfig.patternLevels.put("sphere", Map.of(1, new Size(1, 1, 1), 2, new Size(2, 2, 2), 3, new Size(3, 3, 3)));
        defaultConfig.patternLevels.put("tunnel", Map.of(1, new Size(1, 2, 5), 2, new Size(2, 2, 8), 3, new Size(3, 3, 12)));
        defaultConfig.patternLevels.put("cross", Map.of(1, new Size(3, 1, 3), 2, new Size(5, 2, 5), 3, new Size(7, 3, 7)));
        defaultConfig.patternLevels.put("layer", Map.of(1, new Size(3, 1, 3), 2, new Size(5, 1, 5), 3, new Size(7, 1, 7)));
        defaultConfig.patternLevels.put("vertical", Map.of(1, new Size(1, 5, 1), 2, new Size(1, 8, 1), 3, new Size(1, 12, 1)));
        
        defaultConfig.allowedTools.add("minecraft:iron_pickaxe");
        defaultConfig.allowedTools.add("minecraft:diamond_pickaxe");
        defaultConfig.allowedTools.add("minecraft:netherite_pickaxe");
        
        // Feature defaults (keep current behavior)
        defaultConfig.autoPickup = false;
        defaultConfig.veinMiningMode = false;
        defaultConfig.durabilityScaling = true;
        defaultConfig.durabilityMultiplier = 0.5; // 50% durability per block
        defaultConfig.cooldownTicks = 0;
        defaultConfig.miningPattern = "cube";
        defaultConfig.particleEffects = true; // Enable by default
        defaultConfig.soundEffects = true; // Enable by default
        defaultConfig.permissionsEnabled = false;
        defaultConfig.respectFortuneAndSilkTouch = true; // ENABLED by default
        defaultConfig.requireCrouch = true; // v4: Require crouch to activate
        defaultConfig.pickaxeEffectiveOnly = true; // v4: Only mine pickaxe-effective blocks
        
        // Block filters
        defaultConfig.filterOresOnly = false;
        defaultConfig.filterStoneOnly = false;
        defaultConfig.filterIgnoreDirtGravel = false;
        defaultConfig.filterValuableOnly = false;
        
        // Enchantment synergies
        defaultConfig.enableEnchantmentSynergies = true;
        defaultConfig.efficiencySpeedBonus = 0.2;
        defaultConfig.unbreakingDurabilityReduction = 0.15;
        
        // Upgrade system
        defaultConfig.enableUpgradeSystem = true;
        defaultConfig.miningTokensPerBlock = 1;
        defaultConfig.upgradeCosts.put("radius_boost", 100000);
        defaultConfig.upgradeCosts.put("efficiency_boost", 150000);
        defaultConfig.upgradeCosts.put("durability_boost", 200000);
        defaultConfig.upgradeCosts.put("auto_pickup", 250000);
        
        // Tool type support
        defaultConfig.enableAxeSupport = false;
        defaultConfig.enableShovelSupport = false;
        defaultConfig.enableHoeSupport = false;
        
        // New v1.3.0 features
        defaultConfig.maxBlocksPerActivation = 100;
        defaultConfig.preventToolBreaking = true;
        defaultConfig.durabilityWarning = true;
        defaultConfig.durabilityWarningThreshold = 20;
        
        // Async/batched breaking for performance
        defaultConfig.batchedBreaking = true;
        defaultConfig.blocksPerTick = 10;
        defaultConfig.enableUndo = true;
        defaultConfig.actionBarFeedback = false; // Disabled - use chat instead
        defaultConfig.sendTokensInChat = true; // Send token rewards as chat message
        defaultConfig.creativeModeBypass = true;
        defaultConfig.checkWorldProtection = true;
        
        // v4: Silk Touch incompatibility
        defaultConfig.enchantmentConflicts.add("minecraft:silk_touch");
        
        // Pattern unlock costs (cube is free)
        defaultConfig.patternCosts.put("sphere", 1000000);     // 1M tokens
        defaultConfig.patternCosts.put("tunnel", 2500000);     // 2.5M tokens
        defaultConfig.patternCosts.put("cross", 5000000);      // 5M tokens
        defaultConfig.patternCosts.put("layer", 10000000);     // 10M tokens
        defaultConfig.patternCosts.put("vertical", 25000000);  // 25M tokens
        
        // Block Value System - Configure token rewards per block type
        defaultConfig.enableBlockValues = true;
        
        // Common blocks (1-5 tokens)
        defaultConfig.blockValues.put("minecraft:stone", 1);
        defaultConfig.blockValues.put("minecraft:cobblestone", 1);
        defaultConfig.blockValues.put("minecraft:deepslate", 1);
        defaultConfig.blockValues.put("minecraft:cobbled_deepslate", 1);
        defaultConfig.blockValues.put("minecraft:andesite", 1);
        defaultConfig.blockValues.put("minecraft:diorite", 1);
        defaultConfig.blockValues.put("minecraft:granite", 1);
        defaultConfig.blockValues.put("minecraft:tuff", 1);
        defaultConfig.blockValues.put("minecraft:netherrack", 1);
        defaultConfig.blockValues.put("minecraft:end_stone", 2);
        defaultConfig.blockValues.put("minecraft:obsidian", 5);
        
        // Coal & Copper (5-10 tokens)
        defaultConfig.blockValues.put("minecraft:coal_ore", 5);
        defaultConfig.blockValues.put("minecraft:deepslate_coal_ore", 5);
        defaultConfig.blockValues.put("minecraft:copper_ore", 8);
        defaultConfig.blockValues.put("minecraft:deepslate_copper_ore", 8);
        
        // Iron & Lapis (10-15 tokens)
        defaultConfig.blockValues.put("minecraft:iron_ore", 10);
        defaultConfig.blockValues.put("minecraft:deepslate_iron_ore", 10);
        defaultConfig.blockValues.put("minecraft:lapis_ore", 12);
        defaultConfig.blockValues.put("minecraft:deepslate_lapis_ore", 12);
        
        // Gold & Redstone (15-25 tokens)
        defaultConfig.blockValues.put("minecraft:gold_ore", 20);
        defaultConfig.blockValues.put("minecraft:deepslate_gold_ore", 20);
        defaultConfig.blockValues.put("minecraft:nether_gold_ore", 15);
        defaultConfig.blockValues.put("minecraft:redstone_ore", 15);
        defaultConfig.blockValues.put("minecraft:deepslate_redstone_ore", 15);
        
        // Diamond & Emerald (50-100 tokens)
        defaultConfig.blockValues.put("minecraft:diamond_ore", 100);
        defaultConfig.blockValues.put("minecraft:deepslate_diamond_ore", 100);
        defaultConfig.blockValues.put("minecraft:emerald_ore", 75);
        defaultConfig.blockValues.put("minecraft:deepslate_emerald_ore", 75);
        
        // Nether ores (20-50 tokens)
        defaultConfig.blockValues.put("minecraft:nether_quartz_ore", 8);
        
        // Ancient Debris & Rare blocks (500-1000 tokens)
        defaultConfig.blockValues.put("minecraft:ancient_debris", 1000);
        defaultConfig.blockValues.put("minecraft:gilded_blackstone", 25);
        
        // Amethyst (10 tokens)
        defaultConfig.blockValues.put("minecraft:amethyst_cluster", 10);
        defaultConfig.blockValues.put("minecraft:budding_amethyst", 50);
        
        return defaultConfig;
    }

    public static class Config {
        public int configVersion = 4; // Version tracking for migrations (v4: crouch, per-pattern config, pickaxe-only blocks)
        
        public Map<Integer, Size> levels = new HashMap<>();
        public Map<String, Map<Integer, Size>> patternLevels = new HashMap<>(); // Per-pattern tier sizes
        public List<String> allowedTools = new ArrayList<>();
        public List<String> blockBlacklist = new ArrayList<>(Arrays.asList("minecraft:bedrock")); // Bedrock is unbreakable by default
        public List<String> blockWhitelist = new ArrayList<>();
        
        // Features
        public boolean autoPickup = false;
        public boolean veinMiningMode = false;
        public boolean durabilityScaling = true; // Each block costs durability
        public double durabilityMultiplier = 0.5; // 50% durability per block
        public int cooldownTicks = 0;
        public String miningPattern = "cube"; // cube, sphere, tunnel, cross, layer, vertical
        public boolean particleEffects = false;
        public boolean soundEffects = false;
        public boolean permissionsEnabled = false;
        public boolean respectFortuneAndSilkTouch = true;
        public boolean requireCrouch = true; // v4: Only activate while crouching
        public boolean pickaxeEffectiveOnly = true; // v4: Only mine blocks effective with pickaxe
        public String previewDensity = "medium"; // "full" (4), "medium" (2), "minimal" (1)
        
        // Block filters
        public boolean filterOresOnly = false;
        public boolean filterStoneOnly = false;
        public boolean filterIgnoreDirtGravel = false;
        public boolean filterValuableOnly = false;
        
        // Enchantment synergies
        public boolean enableEnchantmentSynergies = true;
        public double efficiencySpeedBonus = 0.2; // 20% faster per Efficiency level (affects tokens)
        public double unbreakingDurabilityReduction = 0.15; // 15% less durability per Unbreaking level
        // Note: Mending works like vanilla - repairs from XP orbs naturally
        
        // Upgrade system
        public boolean enableUpgradeSystem = true;
        public int miningTokensPerBlock = 1; // Default tokens earned per block (when blockValues is empty)
        public Map<String, Integer> upgradeCosts = new HashMap<>(); // Upgrade name -> token cost
        
        // Block Value System - different blocks worth different tokens
        public boolean enableBlockValues = true;
        public Map<String, Integer> blockValues = new HashMap<>(); // Block ID -> token value
        
        // Tool types
        public boolean enableAxeSupport = false;
        public boolean enableShovelSupport = false;
        public boolean enableHoeSupport = false;
        
        // New v1.3.0 features
        public int maxBlocksPerActivation = 100;
        public boolean preventToolBreaking = true;
        public boolean durabilityWarning = true;
        public int durabilityWarningThreshold = 20;
        
        // Async/batched breaking for performance
        public boolean batchedBreaking = true;
        public int blocksPerTick = 10;
        public boolean enableUndo = true;
        public boolean actionBarFeedback = false; // Action bar disabled by default
        public boolean sendTokensInChat = true; // Send token rewards in chat (false = action bar only)
        public boolean creativeModeBypass = true;
        public List<String> enchantmentConflicts = new ArrayList<>();
        public boolean checkWorldProtection = true;
        
        // Pattern unlock costs (cube is always free/unlocked by default)
        public Map<String, Integer> patternCosts = new HashMap<>();
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