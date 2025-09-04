package com.example.osrstts.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * Caches ElevenLabs voice metadata and provides deterministic selection per tag.
 */
public class ElevenLabsVoiceCatalog {
    private static final ObjectMapper M = new ObjectMapper();
    private static final long REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L; // 6h
    private final Path cacheFile;
    private final String apiKey;
    private final String modelId;

    private long loadedAt;
    private final List<Voice> all = new ArrayList<>();
    private final Map<String,List<Voice>> tagIndex = new HashMap<>();

    public static class Voice {
        public final String name;
        public final String id;
        public final String category;
        public Voice(String name, String id, String category) { this.name=name; this.id=id; this.category=category; }
        @Override public String toString() { return name + " (" + id + ")"; }
    }

    public ElevenLabsVoiceCatalog(String apiKey, String modelId) {
        this.apiKey = apiKey;
        this.modelId = modelId;
        Path dir = Paths.get("config", "osrs-tts");
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
        this.cacheFile = dir.resolve("eleven-voices-cache.json");
        loadCacheFile();
    }

    public synchronized void ensureLoaded() {
        long now = System.currentTimeMillis();
        if (now - loadedAt < REFRESH_INTERVAL_MS && !all.isEmpty()) return;
        if (apiKey == null || apiKey.isBlank()) return; // cannot refresh
        try {
            ElevenLabsTtsClient tmp = new ElevenLabsTtsClient(apiKey, modelId, "wav");
            String json = tmp.listVoicesSample();
            if (json != null && !json.startsWith("Voices list failed")) {
                parseAndIndex(json);
                saveCacheFile(json);
            }
        } catch (Exception ignored) {}
    }

    public synchronized String deterministicForTag(String tag, String seedKey) {
        if (tag == null) return null;
        ensureLoaded();
        List<Voice> pool = tagIndex.get(tag.toLowerCase(Locale.ROOT));
        if (pool == null || pool.isEmpty()) return null;
        int idx = Math.abs(hash(seedKey + "|" + tag)) % pool.size();
        return pool.get(idx).toString();
    }

    public synchronized int poolSize(String tag) {
        ensureLoaded();
        List<Voice> pool = tagIndex.get(tag.toLowerCase(Locale.ROOT));
        return pool == null ? 0 : pool.size();
    }

    public synchronized Set<String> availableTags() {
        ensureLoaded();
        return new HashSet<>(tagIndex.keySet());
    }

    public synchronized List<String> getAllVoicesFormatted() {
        ensureLoaded();
        List<String> result = new ArrayList<>();
        for (Voice v : all) {
            result.add(v.toString()); // "Name (voice_id)" format
        }
        return result.stream().distinct().sorted(String.CASE_INSENSITIVE_ORDER).collect(java.util.stream.Collectors.toList());
    }

    public synchronized int getTotalVoiceCount() {
        ensureLoaded();
        return all.size();
    }

    public synchronized String anyVoiceIdLikeGender(String gender, String seedKey) {
        if (gender == null) return null;
        ensureLoaded();
        List<Voice> pool = tagIndex.get(gender.toLowerCase(Locale.ROOT));
        if (pool == null || pool.isEmpty()) return null;
        int idx = Math.abs(hash(seedKey + "|g:" + gender)) % pool.size();
        return pool.get(idx).toString();
    }

    private void parseAndIndex(String json) throws IOException {
        JsonNode root = M.readTree(json).path("voices");
        all.clear();
        tagIndex.clear();
        if (root.isArray()) {
            for (JsonNode v : root) {
                String name = v.path("name").asText("");
                String id = v.path("voice_id").asText("");
                String category = v.path("category").asText("");
                if (name.isEmpty() || id.isEmpty()) continue;
                Voice voice = new Voice(name, id, category);
                all.add(voice);
                // heuristic gender guess
                String lower = name.toLowerCase(Locale.ROOT);
                if (looksFemaleName(lower)) addTag("female", voice);
                else addTag("male", voice);
                if (looksKidName(lower)) addTag("kid", voice);
                // tone tags (heuristic by category or name hints)
                if (category.toLowerCase(Locale.ROOT).contains("narration")) addTag("narrator", voice);
                if (lower.contains("pirate")) addTag("pirate", voice);
                if (lower.contains("wizard") || lower.contains("mage") || lower.contains("arcane")) addTag("wizard", voice);
                if (lower.contains("goblin")) addTag("goblin", voice);
                if (lower.contains("dwarf")) addTag("dwarf", voice);
                if (lower.contains("elf")) addTag("elf", voice);
            }
        }
        loadedAt = System.currentTimeMillis();
        // If some core tags missing, seed with curated voices
        seedIfEmpty("wizard", new Voice("Antoni","ErXwobaYiN019PkySvjV",""));
        seedIfEmpty("pirate", new Voice("Arnold","VR6AewLTigWG4xSOukaG",""));
        seedIfEmpty("goblin", new Voice("Sam","yoZ06aMxZJJ28mfd3POQ",""));
        seedIfEmpty("elf", new Voice("Elli","MF3mGyEYCl7XYWbV9V6O",""));
        seedIfEmpty("female", new Voice("Rachel","21m00Tcm4TlvDq8ikWAM",""));
        seedIfEmpty("male", new Voice("Adam","pNInz6obpgDQGcFmaJgB",""));
        seedIfEmpty("kid", new Voice("Bella","EXAVITQu4vr4xnSDxMaL",""));
    }

    private void addTag(String tag, Voice v) {
        tagIndex.computeIfAbsent(tag, k -> new ArrayList<>()).add(v);
    }

    private void seedIfEmpty(String tag, Voice v) {
        tagIndex.computeIfAbsent(tag, k -> new ArrayList<>());
        if (tagIndex.get(tag).isEmpty()) tagIndex.get(tag).add(v);
    }

    private void loadCacheFile() {
        try {
            if (Files.exists(cacheFile)) {
                String json = Files.readString(cacheFile, StandardCharsets.UTF_8);
                parseAndIndex(json);
            }
        } catch (Exception ignored) {}
    }

    private void saveCacheFile(String json) {
        try { Files.writeString(cacheFile, json, StandardCharsets.UTF_8); } catch (Exception ignored) {}
    }

    private static boolean looksFemaleName(String n) {
        return n.endsWith("a") || n.endsWith("ah") || n.endsWith("ie") || n.endsWith("y") || n.contains("bella") || n.contains("rachel") || n.contains("dorothy");
    }
    private static boolean looksKidName(String n) {
        return n.contains("kid") || n.contains("child") || n.contains("boy") || n.contains("girl") || n.contains("bella") || n.contains("sam");
    }
    private static int hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            int v = 0; for (int i=0;i<4 && i<d.length;i++) v = (v<<8) | (d[i] & 0xFF); return v;
        } catch (Exception e) { return s.hashCode(); }
    }
}
