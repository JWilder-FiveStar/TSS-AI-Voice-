package com.example.osrstts.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for providing NPC metadata including gender resolution and tag inference.
 * Sources: local mapping file -> heuristics -> fallbacks
 */
public class NpcMetadataService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Map<String, NpcData> npcDataMap = new HashMap<>();
    private final Map<String, String> genderOverrides = new HashMap<>();
    private volatile boolean loaded = false;
    
    public NpcMetadataService() {
        loadMappings();
    }
    
    /**
     * Get metadata for an NPC by name and optional ID.
     */
    public NpcMetadata get(Integer npcId, String npcName) {
        ensureLoaded();
        
        String npcKey = NpcMetadata.generateNpcKey(npcName, npcId);
        String gender = resolveGender(npcName, npcId);
        Set<String> tags = resolveTags(npcName, npcId);
        
        return new NpcMetadata(npcKey, npcName, npcId, gender, tags);
    }
    
    /**
     * Get metadata for an NPC by name only.
     */
    public NpcMetadata get(String npcName) {
        return get(null, npcName);
    }
    
    private String resolveGender(String npcName, Integer npcId) {
        // 1. Check explicit gender overrides by ID
        if (npcId != null) {
            String idKey = "npc_" + npcId;
            String override = genderOverrides.get(idKey);
            if (override != null) return override;
        }
        
        // 2. Check explicit gender overrides by name
        if (npcName != null) {
            String nameKey = npcName.toLowerCase().trim();
            String override = genderOverrides.get(nameKey);
            if (override != null) return override;
        }
        
        // 3. Check mapping file data
        NpcData data = findNpcData(npcName, npcId);
        if (data != null && data.gender != null) {
            return data.gender;
        }
        
        // 4. Use heuristic gender guessing (existing logic from VoiceSelector)
        return guessGender(npcName);
    }
    
    private Set<String> resolveTags(String npcName, Integer npcId) {
        Set<String> tags = new HashSet<>();
        
        // Add gender as a tag
        String gender = resolveGender(npcName, npcId);
        if (gender != null && !gender.equals("unknown")) {
            tags.add(gender);
        }
        
        // Check mapping file for explicit tags
        NpcData data = findNpcData(npcName, npcId);
        if (data != null && data.tags != null) {
            tags.addAll(data.tags);
        }
        
        // Infer tags from name patterns
        if (npcName != null) {
            String name = npcName.toLowerCase();
            
            // Age/type tags
            if (name.contains("kid") || name.contains("child") || name.contains("boy") || name.contains("girl")) {
                tags.add("kid");
            }
            
            // Race tags
            if (name.contains("elf") || name.contains("elven")) {
                tags.add("elf");
            }
            if (name.contains("dwarf") || name.contains("dwarven")) {
                tags.add("dwarf");
            }
            if (name.contains("goblin")) {
                tags.add("goblin");
            }
            if (name.contains("gnome")) {
                tags.add("gnome");
            }
            
            // Role tags
            if (name.contains("king") || name.contains("queen") || name.contains("prince") || name.contains("princess")) {
                tags.add("royalty");
            }
            if (name.contains("guard") || name.contains("soldier") || name.contains("warrior")) {
                tags.add("guard");
            }
            if (name.contains("wizard") || name.contains("mage") || name.contains("witch")) {
                tags.add("wizard");
            }
            if (name.contains("pirate") || name.contains("captain")) {
                tags.add("pirate");
            }
            if (name.contains("ghost") || name.contains("spirit") || name.contains("undead") || name.contains("skeleton")) {
                tags.add("ghost");
            }
            if (name.contains("bandit") || name.contains("thief") || name.contains("rogue")) {
                tags.add("bandit");
            }
        }
        
        return tags;
    }
    
    private NpcData findNpcData(String npcName, Integer npcId) {
        if (npcId != null) {
            NpcData data = npcDataMap.get("npc_" + npcId);
            if (data != null) return data;
        }
        
        if (npcName != null) {
            // Try exact name match first
            NpcData data = npcDataMap.get(npcName.toLowerCase().trim());
            if (data != null) return data;
            
            // Try canonicalized name
            String canonicalized = NpcMetadata.generateNpcKey(npcName, null);
            return npcDataMap.get(canonicalized);
        }
        
        return null;
    }
    
    /**
     * Simple gender guesser by name (reused from VoiceSelector).
     */
    private String guessGender(String npcName) {
        if (npcName == null || npcName.isBlank()) return "unknown";
        String n = npcName.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        
        // Known exceptions and obvious names
        Set<String> maleNames = Set.of("noah", "adam", "arthur", "merlin", "lancelot", "galahad", "hans", "ryan", "owen", "george");
        Set<String> femaleNames = Set.of("alina", "anna", "anne", "ella", "jenny", "libby", "sonia", "maisie");
        
        if (maleNames.contains(n)) return "male";
        if (femaleNames.contains(n)) return "female";
        
        // Heuristic suffixes
        String[] femaleSuffixes = {"a", "ia", "na", "la", "ra", "ssa", "ella", "ette", "ine", "lyn", "lynn", "beth", "anne", "anna", "eva", "ina", "ika"};
        String[] maleSuffixes = {"us", "o", "an", "en", "ar", "ik", "as", "is", "or", "er", "ath", "son"};
        
        for (String suf : femaleSuffixes) {
            if (n.endsWith(suf)) return "female";
        }
        for (String suf : maleSuffixes) {
            if (n.endsWith(suf)) return "male";
        }
        
        return "male"; // default bias
    }
    
    private void ensureLoaded() {
        if (!loaded) {
            loadMappings();
        }
    }
    
    private void loadMappings() {
        try {
            // Try to load from resources/osrs-voices.json
            InputStream is = getClass().getResourceAsStream("/osrs-voices.json");
            if (is != null) {
                String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                parseMappingJson(json);
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not load NPC mappings: " + e.getMessage());
        } finally {
            loaded = true;
        }
    }
    
    private void parseMappingJson(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            
            // Parse NPC data if present
            JsonNode npcNode = root.get("npcs");
            if (npcNode != null && npcNode.isObject()) {
                npcNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    JsonNode value = entry.getValue();
                    
                    String gender = value.has("gender") ? value.get("gender").asText() : null;
                    Set<String> tags = new HashSet<>();
                    
                    if (value.has("tags") && value.get("tags").isArray()) {
                        value.get("tags").forEach(tag -> tags.add(tag.asText().toLowerCase()));
                    }
                    
                    npcDataMap.put(key.toLowerCase(), new NpcData(gender, tags));
                });
            }
            
            // Parse gender overrides if present
            JsonNode genderNode = root.get("genderOverrides");
            if (genderNode != null && genderNode.isObject()) {
                genderNode.fields().forEachRemaining(entry -> 
                    genderOverrides.put(entry.getKey().toLowerCase(), entry.getValue().asText())
                );
            }
        } catch (Exception e) {
            System.err.println("Warning: Could not parse NPC mapping JSON: " + e.getMessage());
        }
    }
    
    private static class NpcData {
        final String gender;
        final Set<String> tags;
        
        NpcData(String gender, Set<String> tags) {
            this.gender = gender;
            this.tags = tags;
        }
    }
}