package net.xai.area_enchant;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {
    private static final Map<UUID, PlayerData> playerData = new HashMap<>();
    
    public static PlayerData get(UUID playerId) {
        return playerData.computeIfAbsent(playerId, k -> new PlayerData());
    }
    
    public static void clear(UUID playerId) {
        playerData.remove(playerId);
    }
    
    public static Map<UUID, PlayerData> getAllData() {
        return new HashMap<>(playerData);
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
        }
        
        public void addDiamondsMined(int count) {
            this.diamondsMined += count;
        }
        
        public int getBlocksMined() {
            return blocksMined;
        }
        
        public int getDiamondsMined() {
            return diamondsMined;
        }
        
        public int getTimesUsed() {
            return timesUsed;
        }
        
        public boolean isDisabled() {
            return disabled;
        }
        
        public void setDisabled(boolean disabled) {
            this.disabled = disabled;
        }
        
        public long getLastUseTick() {
            return lastUseTick;
        }
        
        public boolean isFirstUse() {
            return firstUse;
        }
        
        public void setFirstUse(boolean firstUse) {
            this.firstUse = firstUse;
        }
        
        public boolean hasEarnedEfficiencyExpert() {
            return earnedEfficiencyExpert;
        }
        
        public void setEarnedEfficiencyExpert(boolean earned) {
            this.earnedEfficiencyExpert = earned;
        }
        
        public boolean hasEarnedDiamondDigger() {
            return earnedDiamondDigger;
        }
        
        public void setEarnedDiamondDigger(boolean earned) {
            this.earnedDiamondDigger = earned;
        }
    }
}

