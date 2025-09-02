package com.example.osrstts.voice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores and persists voice assignments to JSON file.
 * Thread-safe and handles persistence automatically.
 */
public class VoiceAssignmentStore {
    private static final String DEFAULT_FILENAME = "voice-assignments.json";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final Path configFile;
    private final Map<String, VoiceAssignment> assignments = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;
    
    public VoiceAssignmentStore(String configDir) {
        Path dir = Paths.get(configDir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            System.err.println("Warning: Could not create config directory: " + dir);
        }
        this.configFile = dir.resolve(DEFAULT_FILENAME);
        loadFromDisk();
    }
    
    /**
     * Get voice assignment for the given NPC key.
     */
    public Optional<VoiceAssignment> get(String npcKey) {
        ensureLoaded();
        return Optional.ofNullable(assignments.get(npcKey));
    }
    
    /**
     * Store or update a voice assignment for the given NPC key.
     */
    public void put(String npcKey, VoiceAssignment assignment) {
        ensureLoaded();
        assignments.put(npcKey, assignment);
        saveToDisk();
    }
    
    /**
     * Remove voice assignment for the given NPC key.
     */
    public void remove(String npcKey) {
        ensureLoaded();
        if (assignments.remove(npcKey) != null) {
            saveToDisk();
        }
    }
    
    /**
     * Get all current voice assignments.
     */
    public Map<String, VoiceAssignment> all() {
        ensureLoaded();
        return new HashMap<>(assignments);
    }
    
    /**
     * Clear all assignments.
     */
    public void clear() {
        assignments.clear();
        saveToDisk();
    }
    
    /**
     * Get the number of stored assignments.
     */
    public int size() {
        ensureLoaded();
        return assignments.size();
    }
    
    private void ensureLoaded() {
        if (!loaded) {
            loadFromDisk();
        }
    }
    
    private void loadFromDisk() {
        try {
            if (Files.exists(configFile)) {
                String json = Files.readString(configFile);
                TypeReference<Map<String, VoiceAssignment>> typeRef = new TypeReference<>() {};
                Map<String, VoiceAssignment> loaded = MAPPER.readValue(json, typeRef);
                assignments.clear();
                assignments.putAll(loaded);
            }
        } catch (IOException e) {
            System.err.println("Warning: Could not load voice assignments from " + configFile + ": " + e.getMessage());
        } finally {
            loaded = true;
        }
    }
    
    private void saveToDisk() {
        try {
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(assignments);
            Files.writeString(configFile, json);
        } catch (IOException e) {
            System.err.println("Warning: Could not save voice assignments to " + configFile + ": " + e.getMessage());
        }
    }
}