package com.example.osrstts.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class VoiceSelector {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String provider;          // "Azure" | "Polly"
    private final String defaultVoice;      // "auto" or provider voice name
    private final Map<String, String> exactNpcMap = new HashMap<>();
    private final Map<String, String> tagMap = new HashMap<>();
    private final List<Map.Entry<Pattern, String>> regexNpcMap = new ArrayList<>();

    public VoiceSelector(String provider, String defaultVoice, String mappingFilePath) {
        this.provider = provider;
        this.defaultVoice = defaultVoice;
        loadMapping(mappingFilePath);
    }

    private void loadMapping(String path) {
        try {
            if (path == null) return;
            File f = new File(path);
            if (!f.exists()) return;
            String json = Files.readString(f.toPath());
            JsonNode root = MAPPER.readTree(json);

            JsonNode exact = root.path("npcExact");
            exact.fieldNames().forEachRemaining(name -> exactNpcMap.put(name, exact.get(name).asText()));

            JsonNode tags = root.path("tags");
            tags.fieldNames().forEachRemaining(tag -> tagMap.put(tag.toLowerCase(Locale.ROOT), tags.get(tag).asText()));

            JsonNode regex = root.path("npcRegex");
            regex.fieldNames().forEachRemaining(expr -> {
                Pattern p = Pattern.compile(expr, Pattern.CASE_INSENSITIVE);
                regexNpcMap.add(Map.entry(p, regex.get(expr).asText()));
            });
        } catch (Exception ignored) {}
    }

    public VoiceSelection select(String npcName, String lineText, Set<String> inferredTags) {
        String voice = null;

        if (npcName != null) {
            String exact = exactNpcMap.get(npcName);
            if (exact != null) voice = exact;

            if (voice == null) {
                for (var entry : regexNpcMap) {
                    if (entry.getKey().matcher(npcName).find()) {
                        voice = entry.getValue();
                        break;
                    }
                }
            }
        }

        if (voice == null && inferredTags != null) {
            for (String tag : inferredTags) {
                String mapped = tagMap.get(tag.toLowerCase(Locale.ROOT));
                if (mapped != null) { voice = mapped; break; }
            }
        }

        if (voice == null || "auto".equalsIgnoreCase(voice)) {
            voice = autoDefaultVoice();
        }

        String style = inferStyle(lineText);
        return VoiceSelection.of(voice, style);
    }

    private String autoDefaultVoice() {
        if ("Polly".equalsIgnoreCase(provider)) {
            return "Matthew"; // sensible Polly default
        }
        return "en-GB-RyanNeural"; // Azure default
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
