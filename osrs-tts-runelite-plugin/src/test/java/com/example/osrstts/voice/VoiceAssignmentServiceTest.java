package com.example.osrstts.voice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VoiceAssignmentService.
 */
class VoiceAssignmentServiceTest {
    
    @TempDir
    Path tempDir;
    
    private VoiceAssignmentService service;
    
    @BeforeEach
    void setUp() {
        VoiceAssignmentStore store = new VoiceAssignmentStore(tempDir.toString());
        service = new VoiceAssignmentService(store);
    }
    
    @Test
    void testAutoAssignment() {
        String npcKey = "test-npc";
        service.assignAuto(npcKey, "ElevenLabs", "test-voice-id", "TestVoice");
        
        assertTrue(service.hasAssignment(npcKey));
        assertFalse(service.hasUserAssignment(npcKey));
        
        var assignment = service.getAssignment(npcKey);
        assertTrue(assignment.isPresent());
        assertEquals("auto", assignment.get().assignedBy());
    }
    
    @Test
    void testUserAssignment() {
        String npcKey = "test-npc";
        service.assignUser(npcKey, "Azure", "test-voice", "TestVoice");
        
        assertTrue(service.hasAssignment(npcKey));
        assertTrue(service.hasUserAssignment(npcKey));
        
        var assignment = service.getAssignment(npcKey);
        assertTrue(assignment.isPresent());
        assertEquals("user", assignment.get().assignedBy());
    }
    
    @Test
    void testClearAssignment() {
        String npcKey = "test-npc";
        service.assignUser(npcKey, "ElevenLabs", "test-voice", "TestVoice");
        assertTrue(service.hasAssignment(npcKey));
        
        service.clearAssignment(npcKey);
        assertFalse(service.hasAssignment(npcKey));
    }
    
    @Test
    void testAssignmentStats() {
        service.assignAuto("npc1", "ElevenLabs", "voice1", "Voice1");
        service.assignAuto("npc2", "Azure", "voice2", "Voice2");
        service.assignUser("npc3", "Polly", "voice3", "Voice3");
        
        var stats = service.getStats();
        assertEquals(3, stats.total());
        assertEquals(2, stats.autoAssigned());
        assertEquals(1, stats.userAssigned());
    }
    
    @Test
    void testGetAllAssignments() {
        service.assignAuto("npc1", "ElevenLabs", "voice1", "Voice1");
        service.assignUser("npc2", "Azure", "voice2", "Voice2");
        
        Map<String, VoiceAssignment> all = service.getAllAssignments();
        assertEquals(2, all.size());
        assertTrue(all.containsKey("npc1"));
        assertTrue(all.containsKey("npc2"));
    }
}