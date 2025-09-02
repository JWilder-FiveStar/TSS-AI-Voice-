package com.example.osrstts.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks character usage for TTS synthesis to provide cost estimates.
 * Maintains rolling monthly totals and provides ElevenLabs tier guidance.
 */
public class UsageTracker {
    private static final String DEFAULT_FILENAME = "usage-tracker.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");
    
    // ElevenLabs pricing tiers (characters per month)
    private static final int[] ELEVEN_LABS_TIERS = {10_000, 30_000, 100_000, 500_000, 1_000_000};
    private static final String[] ELEVEN_LABS_TIER_NAMES = {"Starter", "Creator", "Pro", "Scale", "Max"};
    
    private final Path configFile;
    private final Map<String, Integer> monthlyUsage = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    
    public UsageTracker(String configDir) {
        Path dir = Paths.get(configDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Warning: Could not create config directory: " + dir);
        }
        this.configFile = dir.resolve(DEFAULT_FILENAME);
        loadFromDisk();
    }
    
    /**
     * Add character count for the current month.
     */
    public void addCharacters(int count) {
        if (count <= 0) return;
        
        ensureLoaded();
        String currentMonth = getCurrentMonth();
        monthlyUsage.merge(currentMonth, count, Integer::sum);
        saveToDisk();
    }
    
    /**
     * Get character count for the current month.
     */
    public int currentMonthChars() {
        ensureLoaded();
        return monthlyUsage.getOrDefault(getCurrentMonth(), 0);
    }
    
    /**
     * Get character count for a specific month.
     */
    public int getCharsForMonth(String month) {
        ensureLoaded();
        return monthlyUsage.getOrDefault(month, 0);
    }
    
    /**
     * Get all monthly usage data.
     */
    public Map<String, Integer> getAllMonthlyUsage() {
        ensureLoaded();
        return new HashMap<>(monthlyUsage);
    }
    
    /**
     * Get usage estimate for ElevenLabs tiers.
     */
    public UsageEstimate estimateForElevenLabs() {
        int currentUsage = currentMonthChars();
        
        // Find the suggested tier (next tier above current usage)
        int suggestedTier = ELEVEN_LABS_TIERS[0]; // Default to smallest tier
        String suggestedTierName = ELEVEN_LABS_TIER_NAMES[0];
        
        for (int i = 0; i < ELEVEN_LABS_TIERS.length; i++) {
            if (currentUsage <= ELEVEN_LABS_TIERS[i]) {
                suggestedTier = ELEVEN_LABS_TIERS[i];
                suggestedTierName = ELEVEN_LABS_TIER_NAMES[i];
                break;
            }
        }
        
        // If usage exceeds the largest tier, use the largest tier
        if (currentUsage > ELEVEN_LABS_TIERS[ELEVEN_LABS_TIERS.length - 1]) {
            suggestedTier = ELEVEN_LABS_TIERS[ELEVEN_LABS_TIERS.length - 1];
            suggestedTierName = ELEVEN_LABS_TIER_NAMES[ELEVEN_LABS_TIER_NAMES.length - 1];
        }
        
        double usagePercent = Math.min(100.0, (currentUsage * 100.0) / suggestedTier);
        
        return new UsageEstimate(
            currentUsage,
            suggestedTier,
            suggestedTierName,
            usagePercent,
            getCurrentMonth()
        );
    }
    
    /**
     * Get the suggested tier limit for progress bars.
     */
    public int suggestedTierLimit() {
        return estimateForElevenLabs().suggestedTierLimit();
    }
    
    /**
     * Clear usage data for the current month.
     */
    public void clearCurrentMonth() {
        ensureLoaded();
        monthlyUsage.remove(getCurrentMonth());
        saveToDisk();
    }
    
    /**
     * Clear all usage data.
     */
    public void clearAll() {
        monthlyUsage.clear();
        saveToDisk();
    }
    
    private String getCurrentMonth() {
        return YearMonth.now().format(MONTH_FORMAT);
    }
    
    private void ensureLoaded() {
        if (!loaded) {
            loadFromDisk();
        }
    }
    
    private void loadFromDisk() {
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                TypeReference<Map<String, Integer>> typeRef = new TypeReference<>() {};
                Map<String, Integer> loaded = MAPPER.readValue(json, typeRef);
                monthlyUsage.clear();
                monthlyUsage.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load usage data from " + configFile + ": " + e.getMessage());
        } finally {
            loaded = true;
        }
    }
    
    private void saveToDisk() {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(monthlyUsage);
            Files.writeString(configFile, json);
        } catch (IOException e) {
            System.err.println("Warning: Could not save usage data to " + configFile + ": " + e.getMessage());
        }
    }
    
    /**
     * Usage estimate information for UI display.
     */
    public static record UsageEstimate(
        int currentUsage,
        int suggestedTierLimit,
        String suggestedTierName,
        double usagePercent,
        String month
    ) {
        public String getDisplayText() {
            return String.format("%d / %d chars (%s tier, %.1f%%)", 
                currentUsage, suggestedTierLimit, suggestedTierName, usagePercent);
        }
    }
}