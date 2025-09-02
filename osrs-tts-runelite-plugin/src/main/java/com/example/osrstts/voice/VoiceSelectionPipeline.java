package com.example.osrstts.voice;

import com.example.osrstts.npc.NpcMetadata;
import com.example.osrstts.npc.NpcMetadataService;
import com.example.osrstts.tts.ElevenVoice;

import java.util.*;

/**
 * Orchestrates the voice selection process with persistence and gender guardrails.
 * This is the main entry point for voice selection that integrates all the components.
 */
public class VoiceSelectionPipeline {
    
    public enum GenderStrictness {
        STRICT,   // Never allow gender mismatches
        PREFER,   // Prefer gender matches but allow mismatches if no alternatives
        OFF       // Ignore gender completely
    }
    
    private final VoiceAssignmentService assignmentService;
    private final NpcMetadataService metadataService;
    private final VoiceSelector selector;
    private GenderStrictness genderStrictness = GenderStrictness.PREFER;
    
    public VoiceSelectionPipeline(
            VoiceAssignmentService assignmentService,
            NpcMetadataService metadataService,
            VoiceSelector selector) {
        this.assignmentService = assignmentService;
        this.metadataService = metadataService;
        this.selector = selector;
    }
    
    /**
     * Select voice for an NPC with full pipeline processing.
     * Returns the provider choice (provider, voiceId, voiceLabel) and optional style.
     */
    public ProviderChoice selectVoice(Integer npcId, String npcName, String dialogueText, Set<String> additionalTags) {
        // Get NPC metadata
        NpcMetadata metadata = metadataService.get(npcId, npcName);
        String npcKey = metadata.npcKey();
        
        // Check for existing persistent assignment
        Optional<VoiceAssignment> existing = assignmentService.getAssignment(npcKey);
        if (existing.isPresent()) {
            VoiceAssignment assignment = existing.get();
            return new ProviderChoice(
                assignment.provider(),
                assignment.voiceId(),
                assignment.voiceLabel(),
                inferStyle(dialogueText)
            );
        }
        
        // No persistent assignment - compute new selection
        Set<String> allTags = new HashSet<>(metadata.tags());
        if (additionalTags != null) {
            allTags.addAll(additionalTags);
        }
        
        // Apply gender guardrails
        Set<String> filteredTags = applyGenderGuardrails(allTags, metadata);
        
        // Use selector to pick voice
        VoiceSelection selection = selector.select(npcName, dialogueText, filteredTags);
        
        // Parse the voice information
        String provider = selector.getProvider(); // Assume selector exposes current provider
        String voiceId = extractVoiceId(selection.voiceName);
        String voiceLabel = extractVoiceLabel(selection.voiceName);
        
        // Persist the auto-assignment for future consistency
        assignmentService.assignAuto(npcKey, provider, voiceId, voiceLabel);
        
        return new ProviderChoice(provider, voiceId, voiceLabel, selection.style);
    }
    
    /**
     * Manually assign a voice to an NPC (user override).
     */
    public void assignVoiceManually(Integer npcId, String npcName, String provider, String voiceId, String voiceLabel) {
        NpcMetadata metadata = metadataService.get(npcId, npcName);
        assignmentService.assignUser(metadata.npcKey(), provider, voiceId, voiceLabel);
    }
    
    /**
     * Clear voice assignment for an NPC (will be auto-assigned next time).
     */
    public void clearVoiceAssignment(Integer npcId, String npcName) {
        NpcMetadata metadata = metadataService.get(npcId, npcName);
        assignmentService.clearAssignment(metadata.npcKey());
    }
    
    /**
     * Set gender strictness for voice selection.
     */
    public void setGenderStrictness(GenderStrictness strictness) {
        this.genderStrictness = strictness;
    }
    
    public GenderStrictness getGenderStrictness() {
        return genderStrictness;
    }
    
    /**
     * Apply gender guardrails to filter voice candidates.
     */
    private Set<String> applyGenderGuardrails(Set<String> tags, NpcMetadata metadata) {
        if (genderStrictness == GenderStrictness.OFF) {
            return tags;
        }
        
        Set<String> filtered = new HashSet<>(tags);
        
        // If we have gender information, prioritize it
        if (metadata.isMale()) {
            filtered.remove("female");
            filtered.add("male");
        } else if (metadata.isFemale()) {
            filtered.remove("male");
            filtered.add("female");
        }
        
        return filtered;
    }
    
    /**
     * Filter voice list by gender if strictness requires it.
     */
    public List<ElevenVoice> filterVoicesByGender(List<ElevenVoice> voices, String requiredGender) {
        if (genderStrictness == GenderStrictness.OFF || requiredGender == null || "unknown".equals(requiredGender)) {
            return voices;
        }
        
        // This is a simplified filter - in a real implementation, you'd need 
        // voice metadata that includes gender information
        List<ElevenVoice> filtered = new ArrayList<>();
        for (ElevenVoice voice : voices) {
            if (isVoiceCompatibleWithGender(voice, requiredGender)) {
                filtered.add(voice);
            }
        }
        
        // If strict mode and no matches, return empty list
        // If prefer mode and no matches, return all voices
        if (filtered.isEmpty() && genderStrictness == GenderStrictness.PREFER) {
            return voices;
        }
        
        return filtered;
    }
    
    /**
     * Check if a voice is compatible with the required gender.
     * This is a heuristic based on voice names - in a real implementation,
     * you'd want proper voice metadata.
     */
    private boolean isVoiceCompatibleWithGender(ElevenVoice voice, String requiredGender) {
        String name = voice.name().toLowerCase();
        
        // Common male names
        String[] maleNames = {"adam", "antoni", "arnold", "brian", "charlie", "daniel", "ethan", "fin", "giovanni", "james", "josh", "liam", "matilda", "michael", "sam", "thomas", "william"};
        // Common female names
        String[] femaleNames = {"alice", "bella", "charlotte", "domi", "elli", "freya", "grace", "isabella", "lily", "mimi", "nicole", "rachel", "sarah", "serena", "sophia", "zoe"};
        
        if ("male".equals(requiredGender)) {
            for (String maleName : maleNames) {
                if (name.contains(maleName)) return true;
            }
        } else if ("female".equals(requiredGender)) {
            for (String femaleName : femaleNames) {
                if (name.contains(femaleName)) return true;
            }
        }
        
        // Default: allow if not clearly mismatched
        return true;
    }
    
    /**
     * Extract voice ID from voice name (handles "Name (ID)" format).
     */
    private String extractVoiceId(String voiceName) {
        if (voiceName == null) return null;
        
        // Try to extract from "Name (ID)" format
        String extracted = ElevenVoice.extractVoiceId(voiceName);
        if (extracted != null) return extracted;
        
        // If no parentheses, assume the whole string is an ID or name
        return voiceName;
    }
    
    /**
     * Extract voice label from voice name (handles "Name (ID)" format).
     */
    private String extractVoiceLabel(String voiceName) {
        if (voiceName == null) return null;
        
        int parenIndex = voiceName.indexOf('(');
        if (parenIndex > 0) {
            return voiceName.substring(0, parenIndex).trim();
        }
        
        return voiceName;
    }
    
    /**
     * Infer style from dialogue text (reuse existing logic).
     */
    private String inferStyle(String text) {
        if (text == null) return null;
        
        String lower = text.toLowerCase();
        if (lower.contains("!") && (lower.contains("arrgh") || lower.contains("attack") || lower.contains("die"))) {
            return "angry";
        } else if (lower.contains("whisper") || lower.contains("shh") || lower.contains("quiet")) {
            return "whisper";
        } else if (lower.contains("!") && lower.contains("great")) {
            return "excited";
        } else if (lower.contains("sigh") || lower.contains("sad") || lower.contains("weep")) {
            return "sad";
        }
        
        return null; // neutral/default
    }
    
    /**
     * Result of voice selection containing all necessary information for TTS.
     */
    public static record ProviderChoice(
        String provider,    // "ElevenLabs" | "Azure" | "Polly"  
        String voiceId,     // provider-specific voice identifier
        String voiceLabel,  // human-readable voice name
        String style        // optional style/emotion for synthesis
    ) {}
}