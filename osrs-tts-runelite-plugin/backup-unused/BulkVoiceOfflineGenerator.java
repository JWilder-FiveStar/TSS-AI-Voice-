package com.example.osrstts.voice;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.npc.NpcMetadataService;
import com.example.osrstts.tts.ElevenLabsVoiceCatalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Offline generator to pre-populate voice-assignments.json for all NPC names gathered from npc-names.json.
 * Does NOT synthesize audio; only performs selection logic via VoiceSelectionPipeline.
 * Usage (example):
 *   java -Dosrs.tts.randomPerTag=true -cp build/libs/... BulkVoiceOfflineGenerator
 * Ensure you have run NpcWikiScraper or placed npc-names.json under config/osrs-tts/ first.
 */
public class BulkVoiceOfflineGenerator {
    private static final ObjectMapper M = new ObjectMapper();

    public static void main(String[] args) throws Exception {
        new BulkVoiceOfflineGenerator().run();
    }

    public void run() throws Exception {
        Path dir = Paths.get("config","osrs-tts");
        Files.createDirectories(dir);
        Path npcNamesFile = dir.resolve("npc-names.json");
        if (!Files.exists(npcNamesFile)) {
            System.err.println("npc-names.json not found. Run the Wiki fetch first from the panel.");
            return;
        }
        List<String> names = loadNames(npcNamesFile);
        System.out.println("Loaded NPC names: " + names.size());

        // Config + selection scaffolding
        OsrsTtsConfig cfg = new OsrsTtsConfig();
        String provider = cfg.getProvider();
        if (!"ElevenLabs".equalsIgnoreCase(provider) && !"Azure".equalsIgnoreCase(provider)) {
            provider = "ElevenLabs"; // default immersive
        }
        // Setup selector and metadata service
        VoiceSelector selector = new VoiceSelector(
                provider,
                cfg.getDefaultVoice(),
                cfg.getVoiceMappingFile(),
                cfg.getNpcMaleVoice(),
                cfg.getNpcFemaleVoice(),
                cfg.getNpcKidVoice()
        );
        VoiceAssignmentStore store = new VoiceAssignmentStore();
        NpcMetadataService metaService = new NpcMetadataService();
        VoiceSelectionPipeline pipeline = new VoiceSelectionPipeline(provider, selector, store, metaService);

        int assigned = 0;
        long start = System.currentTimeMillis();
        for (String name : names) {
            if (name == null || name.isBlank()) continue;
            String key = name.toLowerCase(Locale.ROOT);
            if (store.get(key).isPresent()) continue; // skip if already exists from previous run
            try {
                // Merge inferred tags from metadata
                NpcMetadataService.NpcMetadata md = metaService.analyzeNpc(name);
                pipeline.chooseForNpc(null, name, "", new HashSet<>(md.allTags));
                assigned++;
                if (assigned % 500 == 0) System.out.println("Assigned voices: " + assigned);
            } catch (Exception ex) {
                System.err.println("Skip name '" + name + "' due to: " + ex.getMessage());
            }
        }
        long ms = System.currentTimeMillis() - start;
        System.out.println("Offline generation complete: new assignments=" + assigned + " in " + ms + " ms");

        // Augment file with metadata (rewrite voice-assignments.json by re-saving store indirectly) and summary
        Path vaFile = dir.resolve("voice-assignments.json");
        if (Files.exists(vaFile)) {
            // Embed summary into a sidecar file
            Path summary = dir.resolve("voice-assignments-summary.json");
            Map<String,Object> stats = new LinkedHashMap<>();
            stats.put("generatedAtEpochMs", Instant.now().toEpochMilli());
            stats.put("totalAssignments", store.all().size());
            long male = store.all().values().stream().filter(a -> a.primaryTag != null && a.primaryTag.equalsIgnoreCase("male")).count();
            long female = store.all().values().stream().filter(a -> a.primaryTag != null && a.primaryTag.equalsIgnoreCase("female")).count();
            stats.put("maleTagAssignments", male);
            stats.put("femaleTagAssignments", female);
            Files.writeString(summary, M.writerWithDefaultPrettyPrinter().writeValueAsString(stats), StandardCharsets.UTF_8);
            System.out.println("Wrote summary -> " + summary.toAbsolutePath());
        }
    }

    private List<String> loadNames(Path file) throws Exception {
        String json = Files.readString(file, StandardCharsets.UTF_8);
        JsonNode root = M.readTree(json);
        List<String> list = new ArrayList<>();
        JsonNode arr = root.path("names");
        if (arr.isArray()) {
            for (JsonNode n : arr) list.add(n.asText());
        }
        return list;
    }
}
