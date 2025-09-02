package com.example.osrstts.tts;

/**
 * Represents an ElevenLabs voice.
 */
public record ElevenVoice(
    String id,          // voice ID (UUID-like string)
    String name,        // display name like "Rachel"
    String category     // category if available (e.g., "premade", "generated", etc.)
) {
    
    /**
     * Create a formatted display string for UI dropdowns: "Name (id)"
     */
    public String toDisplayString() {
        return name + " (" + id + ")";
    }
    
    /**
     * Parse a display string back to get the voice ID
     */
    public static String extractVoiceId(String displayString) {
        if (displayString == null) return null;
        String s = displayString.trim();
        int i = s.lastIndexOf('(');
        int j = s.lastIndexOf(')');
        if (i >= 0 && j > i) {
            return s.substring(i + 1, j).trim();
        }
        // If no parentheses, assume the whole string is an ID
        return s;
    }
    
    /**
     * Check if a string looks like a valid ElevenLabs voice ID format
     */
    public static boolean isValidVoiceId(String id) {
        return id != null && id.matches("[A-Za-z0-9]{20,}");
    }
}