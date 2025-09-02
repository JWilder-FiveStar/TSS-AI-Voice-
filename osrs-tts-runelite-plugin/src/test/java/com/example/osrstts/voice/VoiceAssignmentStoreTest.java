package com.example.osrstts.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VoiceAssignmentStore.
 */
class VoiceAssignmentStoreTest {
    
    @TempDir
    Path tempDir;
    
    private VoiceAssignmentStore store;
    
    @BeforeEach
    void setUp() {
        store = new VoiceAssignmentStore(tempDir.toString());
    }
    
    @Test
    void testPutAndGet() {
        String npcKey = "test-npc";
        VoiceAssignment assignment = VoiceAssignment.auto("ElevenLabs", "test-voice-id", "TestVoice");
        
        store.put(npcKey, assignment);
        Optional<VoiceAssignment> retrieved = store.get(npcKey);
        
        assertTrue(retrieved.isPresent());
        assertEquals(assignment, retrieved.get());
    }
    
    @Test
    void testGetNonExistent() {
        Optional<VoiceAssignment> result = store.get("non-existent");
        assertFalse(result.isPresent());
    }
    
    @Test
    void testRemove() {
        String npcKey = "test-npc";
        VoiceAssignment assignment = VoiceAssignment.user("Azure", "test-voice", "TestVoice");
        
        store.put(npcKey, assignment);
        assertTrue(store.get(npcKey).isPresent());
        
        store.remove(npcKey);
        assertFalse(store.get(npcKey).isPresent());
    }
    
    @Test
    void testPersistence() {
        String npcKey = "persistent-npc";
        VoiceAssignment assignment = VoiceAssignment.auto("ElevenLabs", "persistent-voice", "PersistentVoice");
        
        store.put(npcKey, assignment);
        
        // Create new store instance with same directory
        VoiceAssignmentStore newStore = new VoiceAssignmentStore(tempDir.toString());
        Optional<VoiceAssignment> retrieved = newStore.get(npcKey);
        
        assertTrue(retrieved.isPresent());
        assertEquals(assignment.provider(), retrieved.get().provider());
        assertEquals(assignment.voiceId(), retrieved.get().voiceId());
        assertEquals(assignment.voiceLabel(), retrieved.get().voiceLabel());
        assertEquals(assignment.assignedBy(), retrieved.get().assignedBy());
    }
    
    @Test
    void testSize() {
        assertEquals(0, store.size());
        
        store.put("npc1", VoiceAssignment.auto("ElevenLabs", "voice1", "Voice1"));
        assertEquals(1, store.size());
        
        store.put("npc2", VoiceAssignment.user("Azure", "voice2", "Voice2"));
        assertEquals(2, store.size());
        
        store.remove("npc1");
        assertEquals(1, store.size());
    }
    
    @Test
    void testClear() {
        store.put("npc1", VoiceAssignment.auto("ElevenLabs", "voice1", "Voice1"));
        store.put("npc2", VoiceAssignment.user("Azure", "voice2", "Voice2"));
        assertEquals(2, store.size());
        
        store.clear();
        assertEquals(0, store.size());
        assertFalse(store.get("npc1").isPresent());
        assertFalse(store.get("npc2").isPresent());
    }
}