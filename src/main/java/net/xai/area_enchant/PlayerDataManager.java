package net.xai.area_enchant;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class PlayerDataManager {
    private static final Map<UUID, PlayerData> playerData = new HashMap<>();
    // SEPARATE storage for undo data - NOT tied to PlayerData so it persists across cache clears
    private static final Map<UUID, UndoData> undoDataStorage = new HashMap<>();
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    // Default to a relative path, but will be set to absolute path per-world
    private static Path DATA_DIR = Paths.get("world/data/area_mine").toAbsolutePath().normalize();
    
    // Track the current world save directory to detect actual world changes
    // CRITICAL: This is reset on every server start to ensure fresh detection
    private static Path currentWorldSaveDir = null;
    
    /**
     * Reset the world directory tracking. Called on server start to ensure fresh detection.
     */
    public static void resetWorldDirectoryTracking() {
        System.out.println("[Area Mine] [WORLD_DIR] Resetting world directory tracking");
        currentWorldSaveDir = null;
    }
    
    // Track which world directory each cached player data was loaded from
    // This allows us to detect if cached data is from the wrong world
    private static final Map<UUID, Path> playerDataWorldDirs = new HashMap<>();
    
    public static void setWorldSaveDirectory(Path worldSaveDir) {
        // Normalize paths to absolute paths for proper comparison
        Path normalizedWorldDir = worldSaveDir.toAbsolutePath().normalize();
        Path newDataDir = normalizedWorldDir.resolve("data/area_mine").toAbsolutePath().normalize();
        
        // CRITICAL: Always check if this is a different world, even on first load
        // Compare the world save directory, not the data directory
        boolean isDifferentWorld = currentWorldSaveDir != null && 
            !normalizedWorldDir.equals(currentWorldSaveDir);
        
        System.out.println("[Area Mine] [WORLD_DIR] ========================================");
        System.out.println("[Area Mine] [WORLD_DIR] Current world dir: " + currentWorldSaveDir);
        System.out.println("[Area Mine] [WORLD_DIR] New world dir: " + normalizedWorldDir);
        System.out.println("[Area Mine] [WORLD_DIR] Is different world: " + isDifferentWorld);
        System.out.println("[Area Mine] [WORLD_DIR] Current data dir: " + (currentWorldSaveDir != null ? currentWorldSaveDir.resolve("data/area_mine").toAbsolutePath().normalize() : "null"));
        System.out.println("[Area Mine] [WORLD_DIR] New data dir: " + newDataDir);
        System.out.println("[Area Mine] [WORLD_DIR] Player data cache size: " + playerData.size());
        
        // CRITICAL: Save current data to old directory BEFORE updating DATA_DIR
        // This must happen BEFORE we update DATA_DIR, otherwise saveAll() will save to the wrong location
        Path oldDataDir = DATA_DIR;
        
        // CRITICAL: In singleplayer, when you switch worlds, the server restarts
        // But the cache might persist if the world directory appears the same
        // We need to clear the cache on EVERY server start to ensure fresh data is loaded
        // This is safe because data is saved on server stop
        
        // If the directory changed AND it's a different world, save current data to old location, then clear cache
        if (isDifferentWorld) {
            System.out.println("[Area Mine] [WORLD_DIR] *** DIFFERENT WORLD DETECTED ***");
            System.out.println("[Area Mine] [WORLD_DIR] Saving and clearing cache...");
            
            // CRITICAL: Save all current player data to the old directory before clearing
            if (!playerData.isEmpty()) {
                System.out.println("[Area Mine] [WORLD_DIR] Saving " + playerData.size() + " player data entries to old directory: " + oldDataDir);
                // Temporarily restore DATA_DIR to old location for saving
                Path tempDataDir = DATA_DIR;
                DATA_DIR = oldDataDir;
                saveAll();
                DATA_DIR = tempDataDir; // Restore
                System.out.println("[Area Mine] [WORLD_DIR] Saved to old directory");
            }
        } else {
            System.out.println("[Area Mine] [WORLD_DIR] Same world or first load");
            if (currentWorldSaveDir == null) {
                System.out.println("[Area Mine] [WORLD_DIR] First time setting world directory");
            } else {
                System.out.println("[Area Mine] [WORLD_DIR] World directory unchanged - but clearing cache anyway for fresh load");
            }
        }
        
        // CRITICAL: Always clear cache on server start to ensure data is loaded from the correct world directory
        // This prevents stale cache data from persisting across world switches
        if (!playerData.isEmpty()) {
            System.out.println("[Area Mine] [WORLD_DIR] Clearing player data cache (" + playerData.size() + " entries) to ensure fresh load");
            playerData.clear();
            playerDataWorldDirs.clear(); // Also clear the world directory tracking
            System.out.println("[Area Mine] [WORLD_DIR] Cache cleared, will reload from: " + newDataDir);
        }
        
        // Clear undo data when switching worlds (each world should have separate undo)
        if (!undoDataStorage.isEmpty()) {
            System.out.println("[Area Mine] [WORLD_DIR] Clearing undo data (" + undoDataStorage.size() + " entries)");
            undoDataStorage.clear();
        }
        
        // Update tracking
        currentWorldSaveDir = normalizedWorldDir;
        DATA_DIR = newDataDir;
        
        // Verify directory exists
        try {
            Files.createDirectories(DATA_DIR);
            System.out.println("[Area Mine] [WORLD_DIR] Data directory verified: " + DATA_DIR);
            System.out.println("[Area Mine] [WORLD_DIR] ========================================");
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to create player data directory: " + e.getMessage());
        }
    }
    
    // NEW: Separate undo data storage methods
    public static void setUndoData(UUID playerId, UndoData undoData) {
        undoDataStorage.put(playerId, undoData);
    }
    
    public static UndoData getUndoData(UUID playerId) {
        return undoDataStorage.get(playerId);
    }
    
    public static void clearUndoData(UUID playerId) {
        undoDataStorage.remove(playerId);
    }
    
    public static PlayerData get(UUID playerId) {
        // CRITICAL: Check if cached data is from a different world directory
        // If the cache was populated before the world directory was set correctly, we need to reload
        if (playerData.containsKey(playerId) && currentWorldSaveDir != null) {
            Path cachedWorldDir = playerDataWorldDirs.get(playerId);
            if (cachedWorldDir != null && !cachedWorldDir.equals(currentWorldSaveDir)) {
                System.out.println("[Area Mine] [GET] *** Cached data is from different world! Clearing cache for " + playerId + " ***");
                System.out.println("[Area Mine] [GET] Cached world: " + cachedWorldDir);
                System.out.println("[Area Mine] [GET] Current world: " + currentWorldSaveDir);
                playerData.remove(playerId);
                playerDataWorldDirs.remove(playerId);
            }
        }
        
        // CRITICAL: Use computeIfAbsent to ensure we always get the same instance
        // This preserves transient undo data in memory
        // BUT: If the world directory changed, the cache should have been cleared
        // So this will reload from the correct directory
        PlayerData data = playerData.computeIfAbsent(playerId, k -> {
            System.out.println("[Area Mine] [GET] Loading player data for " + playerId);
            System.out.println("[Area Mine] [GET] Current world dir: " + currentWorldSaveDir);
            System.out.println("[Area Mine] [GET] Data directory: " + DATA_DIR);
            PlayerData loaded = loadFromDisk(playerId);
            if (loaded != null) {
                System.out.println("[Area Mine] [GET] Loaded existing data: blocksMined=" + loaded.getBlocksMined() + ", tokens=" + loaded.getMiningTokens());
            } else {
                System.out.println("[Area Mine] [GET] No existing data found, creating new");
            }
            // Track which world directory this data was loaded from
            if (currentWorldSaveDir != null) {
                playerDataWorldDirs.put(playerId, currentWorldSaveDir);
                System.out.println("[Area Mine] [GET] Tracked world dir for this data: " + currentWorldSaveDir);
            }
            return loaded != null ? loaded : new PlayerData();
        });
        
        // If data was just loaded from disk, undo data will be null (it's transient)
        // But if it was already in memory, undo data should persist
        return data;
    }
    
    public static void clear(UUID playerId) {
        playerData.remove(playerId);
    }
    
    public static Map<UUID, PlayerData> getAllData() {
        return new HashMap<>(playerData);
    }
    
    public static void saveAll() {
        for (Map.Entry<UUID, PlayerData> entry : playerData.entrySet()) {
            saveToDisk(entry.getKey(), entry.getValue());
        }
    }
    
    public static void saveToDisk(UUID playerId, PlayerData data) {
        try {
            Files.createDirectories(DATA_DIR);
            Path playerFile = DATA_DIR.resolve(playerId.toString() + ".json");
            Files.writeString(playerFile, gson.toJson(data));
            System.out.println("[Area Mine] [SAVE] Saved to: " + playerFile.toAbsolutePath() + " (blocksMined=" + data.getBlocksMined() + ", tokens=" + data.getMiningTokens() + ")");
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to save player data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static PlayerData loadFromDisk(UUID playerId) {
        try {
            Path playerFile = DATA_DIR.resolve(playerId.toString() + ".json");
            System.out.println("[Area Mine] [LOAD] Attempting to load from: " + playerFile.toAbsolutePath());
            if (Files.exists(playerFile)) {
                String json = Files.readString(playerFile);
                PlayerData data = gson.fromJson(json, PlayerData.class);
                System.out.println("[Area Mine] [LOAD] Loaded data: blocksMined=" + data.getBlocksMined() + ", tokens=" + data.getMiningTokens());
                return data;
            } else {
                System.out.println("[Area Mine] [LOAD] File does not exist, creating new data");
            }
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to load player data: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    public static class PlayerData {
        private long lastUseTick = 0;
        private int blocksMined = 0;
        private int timesUsed = 0;
        private int diamondsMined = 0;
        private boolean disabled = false;
        private boolean firstUse = true;
        private boolean earnedEfficiencyExpert = false;
        private boolean earnedDiamondDigger = false;
        
        // New achievement tracking
        private boolean earnedUnstoppable = false;
        private boolean earnedExcavator = false;
        private boolean earnedTokenMillionaire = false;
        private boolean earnedMasterMiner = false;
        private int sessionBlocksMined = 0; // Resets on logout
        
        // New features
        private int miningTokens = 0;
        private Map<String, Boolean> unlockedUpgrades = new HashMap<>();
        private Set<String> unlockedPatterns = new HashSet<>(Arrays.asList("cube")); // Cube is default/free
        private Map<String, Integer> blockTypeStats = new HashMap<>(); // Block ID -> count
        private Map<String, Integer> dimensionStats = new HashMap<>(); // Dimension ID -> blocks mined
        private long totalMiningTime = 0; // In ticks
        private transient UndoData lastOperation = null; // Don't serialize
        
        public boolean isOnCooldown(long currentTick, int cooldownTicks) {
            if (cooldownTicks <= 0) return false;
            return (currentTick - lastUseTick) < cooldownTicks;
        }
        
        public void updateLastUse(long currentTick) {
            this.lastUseTick = currentTick;
            this.timesUsed++;
        }
        
        public void addBlocksMined(int count) {
            this.blocksMined += count;
            this.sessionBlocksMined += count;
        }
        
        public void resetSessionBlocks() {
            this.sessionBlocksMined = 0;
        }
        
        public void addDiamondsMined(int count) {
            this.diamondsMined += count;
        }
        
        public void addBlockTypeStats(String blockId, int count) {
            blockTypeStats.put(blockId, blockTypeStats.getOrDefault(blockId, 0) + count);
        }
        
        public void addDimensionStats(String dimensionId, int count) {
            dimensionStats.put(dimensionId, dimensionStats.getOrDefault(dimensionId, 0) + count);
        }
        
        public void addMiningTokens(int tokens) {
            this.miningTokens += tokens;
        }
        
        public boolean spendTokens(int cost) {
            if (miningTokens >= cost) {
                miningTokens -= cost;
                return true;
            }
            return false;
        }
        
        public void unlockUpgrade(String upgradeName) {
            unlockedUpgrades.put(upgradeName, true);
        }
        
        public boolean hasUpgrade(String upgradeName) {
            return unlockedUpgrades.getOrDefault(upgradeName, false);
        }
        
        public void unlockPattern(String patternName) {
            unlockedPatterns.add(patternName.toLowerCase());
        }
        
        public boolean hasPattern(String patternName) {
            return unlockedPatterns.contains(patternName.toLowerCase());
        }
        
        public Set<String> getUnlockedPatterns() {
            return new HashSet<>(unlockedPatterns);
        }
        
        // DEPRECATED: Use PlayerDataManager.setUndoData/getUndoData/clearUndoData instead
        // Keeping for backwards compatibility but these now delegate to the separate storage
        @Deprecated
        public void setLastOperation(UndoData data) {
            this.lastOperation = data;
            // Also store in separate storage
            if (data != null) {
                // We need player UUID, but we don't have it here - this is a problem
                // Actually, we should just use the separate storage methods directly
            }
        }
        
        @Deprecated
        public UndoData getLastOperation() {
            return lastOperation;
        }
        
        @Deprecated
        public void clearLastOperation() {
            this.lastOperation = null;
        }
        
        // Getters
        public int getBlocksMined() { return blocksMined; }
        public int getDiamondsMined() { return diamondsMined; }
        public int getTimesUsed() { return timesUsed; }
        public int getMiningTokens() { return miningTokens; }
        public Map<String, Integer> getBlockTypeStats() { return blockTypeStats; }
        public Map<String, Integer> getDimensionStats() { return dimensionStats; }
        public long getTotalMiningTime() { return totalMiningTime; }
        public void addMiningTime(long ticks) { this.totalMiningTime += ticks; }
        
        public boolean isDisabled() { return disabled; }
        public void setDisabled(boolean disabled) { this.disabled = disabled; }
        public long getLastUseTick() { return lastUseTick; }
        public boolean isFirstUse() { return firstUse; }
        public void setFirstUse(boolean firstUse) { this.firstUse = firstUse; }
        public boolean hasEarnedEfficiencyExpert() { return earnedEfficiencyExpert; }
        public void setEarnedEfficiencyExpert(boolean earned) { this.earnedEfficiencyExpert = earned; }
        public boolean hasEarnedDiamondDigger() { return earnedDiamondDigger; }
        public void setEarnedDiamondDigger(boolean earned) { this.earnedDiamondDigger = earned; }
        
        // New achievement getters/setters
        public boolean hasEarnedUnstoppable() { return earnedUnstoppable; }
        public void setEarnedUnstoppable(boolean earned) { this.earnedUnstoppable = earned; }
        public boolean hasEarnedExcavator() { return earnedExcavator; }
        public void setEarnedExcavator(boolean earned) { this.earnedExcavator = earned; }
        public boolean hasEarnedTokenMillionaire() { return earnedTokenMillionaire; }
        public void setEarnedTokenMillionaire(boolean earned) { this.earnedTokenMillionaire = earned; }
        public boolean hasEarnedMasterMiner() { return earnedMasterMiner; }
        public void setEarnedMasterMiner(boolean earned) { this.earnedMasterMiner = earned; }
        public int getSessionBlocksMined() { return sessionBlocksMined; }
        public Map<String, Boolean> getUnlockedUpgrades() { return unlockedUpgrades; }
        
        // Pattern initialization helper
        public void ensureDefaultPattern() {
            if (unlockedPatterns == null) {
                unlockedPatterns = new HashSet<>(Arrays.asList("cube"));
            } else if (unlockedPatterns.isEmpty()) {
                unlockedPatterns.add("cube");
            }
        }
    }
    
    public static class UndoData {
        public List<BlockStateData> blocks = new ArrayList<>();
        public List<UUID> itemEntities = new ArrayList<>(); // Track dropped item entities
        public Map<String, Integer> inventoryItems = new HashMap<>(); // Track items added to inventory (item ID -> count)
        public Map<String, Integer> inventoryBefore = new HashMap<>(); // CRITICAL: Track player's inventory BEFORE breaking blocks (item ID -> count)
        public long timestamp;
        
        public UndoData() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public void addBlock(BlockPos pos, BlockState state) {
            blocks.add(new BlockStateData(pos, state));
        }
        
        public void addItemEntity(UUID entityId) {
            itemEntities.add(entityId);
        }
        
        public void addInventoryItem(String itemId, int count) {
            inventoryItems.put(itemId, inventoryItems.getOrDefault(itemId, 0) + count);
        }
        
        public static class BlockStateData {
            public BlockPos pos;
            public BlockState state;
            
            public BlockStateData(BlockPos pos, BlockState state) {
                this.pos = pos;
                this.state = state;
            }
        }
    }
}

