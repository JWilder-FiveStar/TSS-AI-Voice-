package com.example.osrstts.voice;

import com.example.osrstts.npc.NpcMetadataService;

import java.util.Locale;
import java.util.Set;

public class VoiceSelectionPipeline {
    private final VoiceAssignmentStore store;
    private final VoiceSelector selector;
    private final NpcMetadataService npcService;
    private final String provider;

    public VoiceSelectionPipeline(String provider, VoiceSelector selector, VoiceAssignmentStore store, NpcMetadataService npcService) {
        this.provider = provider;
        this.selector = selector;
        this.store = store;
        this.npcService = npcService;
    }

    public VoiceSelection chooseForNpc(Integer npcId, String npcName, String lineText, Set<String> inferredTags) {
        NpcMetadataService.NpcMetadata meta = npcService.get(npcId, npcName);
        String key = meta.npcKey;
        VoiceAssignmentStore.VoiceAssignment locked = store.get(key).orElse(null);
        if (locked != null) {
            return VoiceSelection.of(locked.voiceId != null && !locked.voiceId.isBlank() ? locked.voiceId : locked.voiceLabel, inferStyle(lineText));
        }
        // Merge tags with metadata
        java.util.Set<String> tags = new java.util.HashSet<>(meta.tags);
        if (inferredTags != null) tags.addAll(inferredTags);
        // Apply selection
        VoiceSelection sel = selector.select(meta.name, lineText, tags);
        // Gender guardrail: if metadata gender is male/female and provider ElevenLabs, prefer matching voices
        if ("ElevenLabs".equalsIgnoreCase(provider)) {
            String g = meta.gender;
            if ("male".equalsIgnoreCase(g) && looksFemaleName(sel.voiceName)) {
                sel = VoiceSelection.of("Adam (pNInz6obpgDQGcFmaJgB)", sel.style);
            } else if ("female".equalsIgnoreCase(g) && looksMaleName(sel.voiceName)) {
                sel = VoiceSelection.of("Rachel (21m00Tcm4TlvDq8ikWAM)", sel.style);
            }
        }
        // Persist auto-assignment for stability
        store.put(key, VoiceAssignmentStore.VoiceAssignment.auto(provider, sel.voiceName, sel.voiceName));
        return sel;
    }

    private boolean looksFemaleName(String v) {
        if (v == null) return false;
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("rachel") || s.contains("bella") || s.contains("dorothy") || s.contains("jenny") || s.contains("sonia") || s.contains("libby");
    }

    private boolean looksMaleName(String v) {
        if (v == null) return false;
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("adam") || s.contains("josh") || s.contains("arnold") || s.contains("antoni") || s.contains("ryan") || s.contains("george");
    }

    private String inferStyle(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.endsWith("!")) return "excited";
        if (t.endsWith("?")) return "chat";
        if (t.contains("...")) return "sad";
        return "chat";
    }
}
