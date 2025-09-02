package com.example.osrstts.npc;

import java.util.Set;

/**
 * Metadata about an NPC including gender information for voice selection.
 */
public record NpcMetadata(
    String npcKey,            // unique key for the NPC (prefer npcId if available, else canonicalized name)
    String name,              // display name
    Integer id,               // RuneScape NPC ID if available
    String gender,            // "male" | "female" | "unknown"
    Set<String> tags          // tags like "elf", "dwarf", "pirate", "royalty", "guard", "kid", etc.
) {
    
    public boolean isMale() {
        return "male".equals(gender);
    }
    
    public boolean isFemale() {
        return "female".equals(gender);
    }
    
    public boolean isUnknownGender() {
        return "unknown".equals(gender) || gender == null;
    }
    
    public boolean hasTag(String tag) {
        return tags != null && tags.contains(tag.toLowerCase());
    }
    
    public boolean hasAnyTag(String... tags) {
        if (this.tags == null || tags == null) return false;
        for (String tag : tags) {
            if (this.tags.contains(tag.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Generate a canonical NPC key from name and id.
     * Prefer ID if available, otherwise use canonicalized name.
     */
    public static String generateNpcKey(String name, Integer id) {
        if (id != null && id > 0) {
            return "npc_" + id;
        }
        if (name != null && !name.isBlank()) {
            // Canonicalize name: lowercase, replace spaces/underscores with dashes, remove special chars
            return name.toLowerCase()
                    .replaceAll("[\\s_]+", "-")
                    .replaceAll("[^a-z0-9-]", "")
                    .replaceAll("-+", "-")
                    .replaceAll("^-|-$", "");
        }
        return "unknown";
    }
}