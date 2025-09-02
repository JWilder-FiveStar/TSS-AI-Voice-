package com.example.osrstts.voice;

/**
 * Represents a persistent voice assignment for an NPC.
 * Once assigned, this voice should be used consistently for the NPC unless manually overridden.
 */
public record VoiceAssignment(
    String provider,       // "ElevenLabs" | "Azure" | "Polly"
    String voiceId,        // provider-specific voice id (e.g., ElevenLabs voice UUID)
    String voiceLabel,     // human-readable label like "Rachel" (optional)
    long assignedAtEpochMs,
    String assignedBy      // "auto" | "user"
) {
    
    public static VoiceAssignment auto(String provider, String voiceId, String voiceLabel) {
        return new VoiceAssignment(provider, voiceId, voiceLabel, System.currentTimeMillis(), "auto");
    }
    
    public static VoiceAssignment user(String provider, String voiceId, String voiceLabel) {
        return new VoiceAssignment(provider, voiceId, voiceLabel, System.currentTimeMillis(), "user");
    }
    
    public boolean isUserAssigned() {
        return "user".equals(assignedBy);
    }
    
    public boolean isAutoAssigned() {
        return "auto".equals(assignedBy);
    }
}