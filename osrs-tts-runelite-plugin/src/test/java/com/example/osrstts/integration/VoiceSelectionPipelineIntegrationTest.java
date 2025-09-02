package com.example.osrstts.integration;

import com.example.osrstts.npc.NpcMetadata;
import com.example.osrstts.npc.NpcMetadataService;
import com.example.osrstts.voice.VoiceAssignment;
import com.example.osrstts.voice.VoiceAssignmentService;
import com.example.osrstts.voice.VoiceAssignmentStore;
import com.example.osrstts.voice.VoiceSelectionPipeline;
import com.example.osrstts.voice.VoiceSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the complete voice selection pipeline.
 */
class VoiceSelectionPipelineIntegrationTest {
    
    @TempDir
    Path tempDir;
    
    private VoiceSelectionPipeline pipeline;
    private VoiceAssignmentService assignmentService;
    private NpcMetadataService metadataService;
    
    @BeforeEach
    void setUp() {
        // Initialize components
        VoiceAssignmentStore store = new VoiceAssignmentStore(tempDir.toString());
        assignmentService = new VoiceAssignmentService(store);
        metadataService = new NpcMetadataService();
        
        // Create a basic voice selector for testing
        VoiceSelector selector = new VoiceSelector(
            "ElevenLabs", 
            "auto", 
            null, // No mapping file for test
            "Adam (pNInz6obpgDQGcFmaJgB)", 
            "Rachel (21m00Tcm4TlvDq8ikWAM)", 
            "Bella (EXAVITQu4vr4xnSDxMaL)"
        );
        
        pipeline = new VoiceSelectionPipeline(assignmentService, metadataService, selector);
    }
    
    @Test
    void testFirstTimeVoiceSelection() {
        // First time encountering an NPC should auto-assign and persist
        String npcName = "Test Dwarf";
        Integer npcId = 1234;
        String dialogueText = "Hello, adventurer!";
        Set<String> tags = Set.of("dwarf", "male");
        
        // Should not have any assignment initially
        assertFalse(assignmentService.hasAssignment("npc_1234"));
        
        // Select voice
        VoiceSelectionPipeline.ProviderChoice choice = pipeline.selectVoice(npcId, npcName, dialogueText, tags);
        
        // Should have created an auto assignment
        assertTrue(assignmentService.hasAssignment("npc_1234"));
        assertFalse(assignmentService.hasUserAssignment("npc_1234"));
        
        // Choice should be reasonable
        assertNotNull(choice);
        assertEquals("ElevenLabs", choice.provider());
        assertNotNull(choice.voiceId());
    }
    
    @Test
    void testPersistentVoiceSelection() {
        // Test that the same NPC gets the same voice across calls
        String npcName = "Consistent NPC";
        Integer npcId = 5678;
        String dialogueText1 = "First line of dialogue";
        String dialogueText2 = "Second line of dialogue";
        Set<String> tags = Set.of("elf", "female");
        
        // First selection
        VoiceSelectionPipeline.ProviderChoice choice1 = pipeline.selectVoice(npcId, npcName, dialogueText1, tags);
        
        // Second selection with different dialogue
        VoiceSelectionPipeline.ProviderChoice choice2 = pipeline.selectVoice(npcId, npcName, dialogueText2, tags);
        
        // Should be identical
        assertEquals(choice1.provider(), choice2.provider());
        assertEquals(choice1.voiceId(), choice2.voiceId());
        assertEquals(choice1.voiceLabel(), choice2.voiceLabel());
    }
    
    @Test
    void testUserOverride() {
        // Test that user assignments override automatic selections
        String npcName = "Override Test NPC";
        Integer npcId = 9999;
        String dialogueText = "Test dialogue";
        Set<String> tags = Set.of("goblin", "male");
        
        // First get automatic selection
        VoiceSelectionPipeline.ProviderChoice autoChoice = pipeline.selectVoice(npcId, npcName, dialogueText, tags);
        
        // Manually assign a different voice
        pipeline.assignVoiceManually(npcId, npcName, "Azure", "en-GB-RyanNeural", "Ryan");
        
        // Next selection should use the manual assignment
        VoiceSelectionPipeline.ProviderChoice manualChoice = pipeline.selectVoice(npcId, npcName, dialogueText, tags);
        
        assertNotEquals(autoChoice.provider(), manualChoice.provider());
        assertEquals("Azure", manualChoice.provider());
        assertEquals("en-GB-RyanNeural", manualChoice.voiceId());
        assertEquals("Ryan", manualChoice.voiceLabel());
        
        // Should be marked as user assignment
        assertTrue(assignmentService.hasUserAssignment("npc_9999"));
    }
    
    @Test
    void testGenderGuardrails() {
        // Test that gender guardrails work correctly
        pipeline.setGenderStrictness(VoiceSelectionPipeline.GenderStrictness.PREFER);
        
        String maleNpcName = "Male Warrior";
        Integer maleNpcId = 1111;
        String femaleNpcName = "Female Mage";
        Integer femaleNpcId = 2222;
        
        VoiceSelectionPipeline.ProviderChoice maleChoice = pipeline.selectVoice(
            maleNpcId, maleNpcName, "Battle cry!", Set.of("male", "warrior"));
        VoiceSelectionPipeline.ProviderChoice femaleChoice = pipeline.selectVoice(
            femaleNpcId, femaleNpcName, "Magic words!", Set.of("female", "wizard"));
        
        // Should get appropriate voice assignments (this is heuristic, depends on voice mapping)
        assertNotNull(maleChoice);
        assertNotNull(femaleChoice);
        
        // Assignments should be different
        assertNotEquals(maleChoice.voiceId(), femaleChoice.voiceId());
    }
    
    @Test
    void testClearAssignment() {
        // Test clearing voice assignments
        String npcName = "Clear Test NPC";
        Integer npcId = 7777;
        
        // Create initial assignment
        VoiceSelectionPipeline.ProviderChoice initialChoice = pipeline.selectVoice(npcId, npcName, "Hello", Set.of("male"));
        assertTrue(assignmentService.hasAssignment("npc_7777"));
        
        // Clear the assignment
        pipeline.clearVoiceAssignment(npcId, npcName);
        assertFalse(assignmentService.hasAssignment("npc_7777"));
        
        // Next selection should create a new auto assignment
        VoiceSelectionPipeline.ProviderChoice newChoice = pipeline.selectVoice(npcId, npcName, "Hello again", Set.of("male"));
        assertTrue(assignmentService.hasAssignment("npc_7777"));
        assertFalse(assignmentService.hasUserAssignment("npc_7777"));
    }
    
    @Test
    void testNpcMetadataGeneration() {
        // Test that NPC metadata is properly generated
        NpcMetadata metadata1 = metadataService.get(1234, "Test Elf Archer");
        assertEquals("npc_1234", metadata1.npcKey());
        assertEquals("Test Elf Archer", metadata1.name());
        assertEquals(Integer.valueOf(1234), metadata1.id());
        assertTrue(metadata1.hasTag("elf"));
        
        // Test name-only metadata
        NpcMetadata metadata2 = metadataService.get("Female Wizard Anna");
        assertNotNull(metadata2.npcKey());
        assertEquals("Female Wizard Anna", metadata2.name());
        assertTrue(metadata2.hasTag("female"));
        assertTrue(metadata2.hasTag("wizard"));
    }
}