package net.xai.area_enchant;

import com.google.gson.Gson;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.object.builder.v1.trade.TradeOfferHelper;
import net.minecraft.enchantment.EnchantmentLevelEntry;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradedItem;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.World;
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
    private static final Gson gson = new Gson();

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
            );
        });
    }

    public static void reloadConfig() {
        loadConfig();
    }

    private static void loadConfig() {
        Path configPath = Paths.get("config/area_enchant.json");

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

    private static Config getDefaultConfig() {
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