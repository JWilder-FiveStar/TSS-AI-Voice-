package com.example.osrstts.voice;

import java.util.Map;
import java.util.Optional;

/**
 * Service layer for managing voice assignments.
 * Provides high-level operations and business logic around voice assignment persistence.
 */
public class VoiceAssignmentService {
    private final VoiceAssignmentStore store;
    
    public VoiceAssignmentService(VoiceAssignmentStore store) {
        this.store = store;
    }
    
    /**
     * Get the current voice assignment for an NPC.
     */
    public Optional<VoiceAssignment> getAssignment(String npcKey) {
        return store.get(npcKey);
    }
    
    /**
     * Assign a voice to an NPC automatically (first-time assignment).
     */
    public void assignAuto(String npcKey, String provider, String voiceId, String voiceLabel) {
        VoiceAssignment assignment = VoiceAssignment.auto(provider, voiceId, voiceLabel);
        store.put(npcKey, assignment);
    }
    
    /**
     * Assign a voice to an NPC by user choice (manual override).
     */
    public void assignUser(String npcKey, String provider, String voiceId, String voiceLabel) {
        VoiceAssignment assignment = VoiceAssignment.user(provider, voiceId, voiceLabel);
        store.put(npcKey, assignment);
    }
    
    /**
     * Remove voice assignment for an NPC (will be re-assigned automatically next time).
     */
    public void clearAssignment(String npcKey) {
        store.remove(npcKey);
    }
    
    /**
     * Check if an NPC has a persistent voice assignment.
     */
    public boolean hasAssignment(String npcKey) {
        return store.get(npcKey).isPresent();
    }
    
    /**
     * Check if an NPC has a user-assigned voice (should not be overridden automatically).
     */
    public boolean hasUserAssignment(String npcKey) {
        return store.get(npcKey)
                .map(VoiceAssignment::isUserAssigned)
                .orElse(false);
    }
    
    /**
     * Get all current voice assignments.
     */
    public Map<String, VoiceAssignment> getAllAssignments() {
        return store.all();
    }
    
    /**
     * Get statistics about assignments.
     */
    public AssignmentStats getStats() {
        Map<String, VoiceAssignment> all = store.all();
        long autoCount = all.values().stream().filter(VoiceAssignment::isAutoAssigned).count();
        long userCount = all.values().stream().filter(VoiceAssignment::isUserAssigned).count();
        
        return new AssignmentStats(all.size(), autoCount, userCount);
    }
    
    /**
     * Clear all assignments (for reset/debugging).
     */
    public void clearAll() {
        store.clear();
    }
    
    /**
     * Statistics about voice assignments.
     */
    public static record AssignmentStats(
        int total,
        long autoAssigned,
        long userAssigned
    ) {}
}