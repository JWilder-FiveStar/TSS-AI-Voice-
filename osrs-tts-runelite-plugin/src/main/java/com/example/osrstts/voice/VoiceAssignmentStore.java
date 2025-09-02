package com.example.osrstts.voice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Persistent store of NPC voice assignments. JSON file lives under config/osrs-tts/voice-assignments.json
 */
public class VoiceAssignmentStore {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path file;
    private final Map<String, VoiceAssignment> map = new LinkedHashMap<>();

    public static class VoiceAssignment {
        public String provider;   // "ElevenLabs" | "Azure" | "Polly"
        public String voiceId;    // provider-specific id or name; for ElevenLabs use voice UUID
        public String voiceLabel; // human-readable label
        public long assignedAtEpochMs;
        public String assignedBy; // "auto" | "user"

        public VoiceAssignment() {}
        public VoiceAssignment(String provider, String voiceId, String voiceLabel, long assignedAtEpochMs, String assignedBy) {
            this.provider = provider; this.voiceId = voiceId; this.voiceLabel = voiceLabel; this.assignedAtEpochMs = assignedAtEpochMs; this.assignedBy = assignedBy;
        }
        public static VoiceAssignment auto(String provider, String voiceId, String voiceLabel) {
            return new VoiceAssignment(provider, voiceId, voiceLabel, Instant.now().toEpochMilli(), "auto");
        }
    }

    public VoiceAssignmentStore() {
        Path dir = Paths.get("config", "osrs-tts");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        this.file = dir.resolve("voice-assignments.json");
        load();
    }

    public synchronized Optional<VoiceAssignment> get(String npcKey) {
        if (npcKey == null) return Optional.empty();
        return Optional.ofNullable(map.get(npcKey));
    }

    public synchronized void put(String npcKey, VoiceAssignment a) {
        if (npcKey == null || a == null) return;
        map.put(npcKey, a);
        save();
    }

    public synchronized void remove(String npcKey) {
        if (npcKey == null) return;
        map.remove(npcKey);
        save();
    }

    public synchronized Map<String, VoiceAssignment> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(map));
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Map<String, VoiceAssignment> m = MAPPER.readValue(json, new TypeReference<Map<String, VoiceAssignment>>(){});
                map.clear();
                if (m != null) map.putAll(m);
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(map);
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException ignored) {}
    }
}
