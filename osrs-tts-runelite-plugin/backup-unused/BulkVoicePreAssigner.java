package com.example.osrstts.voice;

import net.runelite.api.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bulk pass to assign a stable voice to every discoverable NPC definition.
 * Avoids first-encounter random jitter in large sessions and creates a complete immersive baseline.
 */
public class BulkVoicePreAssigner {
    private static final Logger log = LoggerFactory.getLogger(BulkVoicePreAssigner.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    public void preAssignAll(VoiceRuntime runtime, Client client, boolean overwriteExisting) {
        if (runtime == null || client == null) return;
        if (!running.compareAndSet(false, true)) {
            log.warn("Bulk voice pre-assignment already running");
            return;
        }
        new Thread(() -> {
            long start = System.currentTimeMillis();
            try {
                log.info("Starting bulk NPC voice pre-assignment (overwriteExisting={})", overwriteExisting);
                VoiceAssignmentStore store = runtime.getAssignmentStore();
                VoiceSelectionPipeline pipeline = runtime.getPipeline();
                Set<Integer> processed = new HashSet<>();
                int assigned = 0;
                int skipped = 0;
                int existing = 0;
                // Heuristic max id (RuneLite NPC IDs currently < 70k). We'll stop after long null streak.
                int nullStreak = 0;
                int maxId = 70000;
                for (int id = 0; id < maxId; id++) {
                    if (nullStreak > 500) break; // safety break when past last region of populated ids
                    Object defObj;
                    try { defObj = client.getClass().getMethod("getNpcDefinition", int.class).invoke(client, id); } catch (Throwable t) { defObj = null; }
                    if (defObj == null) { nullStreak++; continue; } else nullStreak = 0;
                    String name;
                    try { name = safeName((String) defObj.getClass().getMethod("getName").invoke(defObj)); } catch (Throwable t) { name = ""; }
                    if (name.isEmpty() || name.equalsIgnoreCase("null")) continue;
                    if (!processed.add(id)) continue;
                    String key = "id:" + id;
                    if (!overwriteExisting && store.get(key).isPresent()) { existing++; continue; }
                    try {
                        // Choose voice without actual dialog content; tags inferred by runtime helper
                        java.util.Set<String> tags = runtime.inferTags(name);
                        pipeline.chooseForNpc(id, name, "", tags);
                        assigned++;
                        if (assigned % 250 == 0) {
                            log.info("Pre-assigned voices: {} (skipped existing {} so far)", assigned, existing);
                        }
                    } catch (Exception ex) {
                        skipped++;
                        if (skipped < 10) log.debug("Failed to assign NPC id={} name='{}': {}", id, name, ex.toString());
                    }
                }
                long ms = System.currentTimeMillis() - start;
                log.info("Bulk NPC voice pre-assignment complete: new={} existingKept={} skipped={} in {} ms", assigned, existing, skipped, ms);
            } finally {
                running.set(false);
            }
        }, "tts-bulk-preassign").start();
    }

    private String safeName(String n) { return n == null ? "" : n.replace('\u00A0',' ').trim(); }
}
