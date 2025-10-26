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
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_DIR = Paths.get("world/data/area_mine");
    
    public static PlayerData get(UUID playerId) {
        return playerData.computeIfAbsent(playerId, k -> {
            PlayerData data = loadFromDisk(playerId);
            return data != null ? data : new PlayerData();
        });
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
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to save player data: " + e.getMessage());
        }
    }
    
    public static PlayerData loadFromDisk(UUID playerId) {
        try {
            Path playerFile = DATA_DIR.resolve(playerId.toString() + ".json");
            if (Files.exists(playerFile)) {
                String json = Files.readString(playerFile);
                return gson.fromJson(json, PlayerData.class);
            }
        } catch (IOException e) {
            System.err.println("[Area Mine] Failed to load player data: " + e.getMessage());
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
        
        public void setLastOperation(UndoData data) {
            this.lastOperation = data;
        }
        
        public UndoData getLastOperation() {
            return lastOperation;
        }
        
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
        public long timestamp;
        
        public UndoData() {
            this.timestamp = System.currentTimeMillis();
        }
        
        public void addBlock(BlockPos pos, BlockState state) {
            blocks.add(new BlockStateData(pos, state));
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

