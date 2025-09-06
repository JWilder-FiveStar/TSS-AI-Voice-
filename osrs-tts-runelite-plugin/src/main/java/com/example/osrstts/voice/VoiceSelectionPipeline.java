package com.example.osrstts.voice;

import com.example.osrstts.npc.NpcMetadataService;
import com.example.osrstts.tts.ElevenLabsVoiceCatalog;

import java.util.Locale;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

public class VoiceSelectionPipeline {
    private final VoiceAssignmentStore store;
    private final VoiceSelector selector;
    private final NpcMetadataService npcService;
    private final String provider;
    private final ElevenLabsVoiceCatalog elevenCatalog;
    // Rotation state: tag -> next index (persist in memory only; stable enough per session)
    private final java.util.Map<String,Integer> tagRotationIndex = new java.util.concurrent.ConcurrentHashMap<>();
    // Cache of tag -> ordered pool (hundreds) built lazily
    private final java.util.Map<String,java.util.List<String>> tagVoicePools = new java.util.concurrent.ConcurrentHashMap<>();

    public VoiceSelectionPipeline(String provider, VoiceSelector selector, VoiceAssignmentStore store, NpcMetadataService npcService) {
        this.provider = provider;
        this.selector = selector;
        this.store = store;
        this.npcService = npcService;
        this.elevenCatalog = "ElevenLabs".equalsIgnoreCase(provider) ? new ElevenLabsVoiceCatalog(System.getProperty("osrs.tts.eleven.key",""), System.getProperty("osrs.tts.eleven.model","eleven_turbo_v2_5")) : null;
    }

    public VoiceSelection chooseForNpc(Integer npcId, String npcName, String lineText, Set<String> inferredTags) {
        NpcMetadataService.NpcMetadata meta = npcService.analyzeNpc(npcName);
        String key = (npcId != null && npcId > 0) ? ("id:" + npcId) : npcName.toLowerCase();
        VoiceAssignmentStore.VoiceAssignment locked = store.get(key).orElse(null);
        if (locked != null) {
            return VoiceSelection.of(locked.voiceId != null && !locked.voiceId.isBlank() ? locked.voiceId : locked.voiceLabel, inferStyle(lineText));
        }
        // Merge tags with metadata
        java.util.Set<String> tags = new java.util.HashSet<>(meta.allTags);
        if (inferredTags != null) tags.addAll(inferredTags);
    // Apply selection (base mapping)
    VoiceSelection sel = selector.select(npcName, lineText, tags);
        // ElevenLabs dynamic enhancement: if selection lacks an id pattern, try catalog tag-driven pick
    if ("ElevenLabs".equalsIgnoreCase(provider) && (sel.voiceName == null || !looksElevenId(sel.voiceName)) && elevenCatalog != null) {
            // Build pooled candidates for each tag, prefer gender-matched subset when possible
            for (String t : tags) {
                String pooled = pooledPickForTag(t, meta.gender, key);
                if (pooled != null) { sel = VoiceSelection.of(pooled, sel.style); break; }
            }
            // Gender fallback only if still missing
            if ((sel.voiceName == null || !looksElevenId(sel.voiceName)) && meta.gender != null) {
                String gPick = elevenCatalog.anyVoiceIdLikeGender(meta.gender, key);
                if (gPick != null && looksElevenId(gPick)) sel = VoiceSelection.of(gPick, sel.style);
            }
        }
        // Gender guardrail: if metadata gender is male/female and provider ElevenLabs, prefer matching voices
    if ("ElevenLabs".equalsIgnoreCase(provider)) {
            String g = meta.gender;
            if ("male".equalsIgnoreCase(g) && looksFemaleName(sel.voiceName)) {
                String forced = rotatingRandomFromTag("male", "male", key);
                if (forced == null) {
                    String[] pool = new String[] {
                        "Adam (pNInz6obpgDQGcFmaJgB)", "Antoni (ErXwobaYiN019PkySvjV)", "Josh (TxGEqnHWrfWFTfGW9XjX)", "Arnold (VR6AewLTigWG4xSOukaG)",
                        "Rachel (21m00Tcm4TlvDq8ikWAM)", "Bella (EXAVITQu4vr4xnSDxMaL)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "Elli (MF3mGyEYCl7XYWbV9V6O)",
                        "Domi (AZnzlk1XvdvUeBnXmlld)", "Sam (yoZ06aMxZJJ28mfd3POQ)"
                    };
                    java.util.Random rng = new java.util.Random(System.nanoTime() ^ key.hashCode());
                    forced = pool[rng.nextInt(pool.length)];
                }
                sel = VoiceSelection.of(forced, sel.style);
            } else if ("female".equalsIgnoreCase(g) && looksMaleName(sel.voiceName)) {
                String forced = rotatingRandomFromTag("female", "female", key);
                if (forced == null) {
                    String[] pool = new String[] {
                        "Adam (pNInz6obpgDQGcFmaJgB)", "Antoni (ErXwobaYiN019PkySvjV)", "Josh (TxGEqnHWrfWFTfGW9XjX)", "Arnold (VR6AewLTigWG4xSOukaG)",
                        "Rachel (21m00Tcm4TlvDq8ikWAM)", "Bella (EXAVITQu4vr4xnSDxMaL)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "Elli (MF3mGyEYCl7XYWbV9V6O)",
                        "Domi (AZnzlk1XvdvUeBnXmlld)", "Sam (yoZ06aMxZJJ28mfd3POQ)"
                    };
                    java.util.Random rng = new java.util.Random(System.nanoTime() ^ key.hashCode());
                    forced = pool[rng.nextInt(pool.length)];
                }
                sel = VoiceSelection.of(forced, sel.style);
            }
        }
        // Optionally randomize within tag pool for more variety prior to persistence
        String primaryTag = null;
    if ("ElevenLabs".equalsIgnoreCase(provider) && elevenCatalog != null && Boolean.parseBoolean(System.getProperty("osrs.tts.randomPerTag","true"))) {
            for (String t : tags) {
                String randomized = rotatingRandomFromTag(t, meta.gender, key);
                if (randomized != null && looksElevenId(randomized)) { sel = VoiceSelection.of(randomized, sel.style); primaryTag = t; break; }
            }
        }
        if (primaryTag == null && !tags.isEmpty()) {
            // choose a stable representative tag for bookkeeping
            primaryTag = tags.iterator().next();
        }
        // Final enforcement: if gender known and mismatch persists, hard swap
    if ("ElevenLabs".equalsIgnoreCase(provider) && meta.gender != null && !"neutral".equalsIgnoreCase(meta.gender)) {
            boolean mismatch = ("male".equalsIgnoreCase(meta.gender) && looksFemaleName(sel.voiceName)) || ("female".equalsIgnoreCase(meta.gender) && looksMaleName(sel.voiceName));
            if (mismatch) {
                String forced = rotatingRandomFromTag(meta.gender.toLowerCase(Locale.ROOT), meta.gender, key);
                if (forced == null) {
                    String[] pool = new String[] {
                        "Adam (pNInz6obpgDQGcFmaJgB)", "Antoni (ErXwobaYiN019PkySvjV)", "Josh (TxGEqnHWrfWFTfGW9XjX)", "Arnold (VR6AewLTigWG4xSOukaG)",
                        "Rachel (21m00Tcm4TlvDq8ikWAM)", "Bella (EXAVITQu4vr4xnSDxMaL)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "Elli (MF3mGyEYCl7XYWbV9V6O)",
                        "Domi (AZnzlk1XvdvUeBnXmlld)", "Sam (yoZ06aMxZJJ28mfd3POQ)"
                    };
                    java.util.Random rng = new java.util.Random(System.nanoTime() ^ key.hashCode());
                    forced = pool[rng.nextInt(pool.length)];
                }
                sel = VoiceSelection.of(forced, sel.style);
            }
        }
        
        // Final fallback: if we still have no valid voice for ElevenLabs, use a randomized mixed pool (not just Adam/Rachel)
        if ("ElevenLabs".equalsIgnoreCase(provider) && (sel.voiceName == null || !looksElevenId(sel.voiceName))) {
            String[] pool = new String[] {
                "Adam (pNInz6obpgDQGcFmaJgB)", "Antoni (ErXwobaYiN019PkySvjV)", "Josh (TxGEqnHWrfWFTfGW9XjX)", "Arnold (VR6AewLTigWG4xSOukaG)",
                "Rachel (21m00Tcm4TlvDq8ikWAM)", "Bella (EXAVITQu4vr4xnSDxMaL)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "Elli (MF3mGyEYCl7XYWbV9V6O)",
                "Domi (AZnzlk1XvdvUeBnXmlld)", "Sam (yoZ06aMxZJJ28mfd3POQ)"
            };
            java.util.Random rng = new java.util.Random(System.nanoTime() ^ key.hashCode());
            String fallback = pool[rng.nextInt(pool.length)];
            sel = VoiceSelection.of(fallback, sel.style);
        }
        
        // Persist auto-assignment for stability after initial pick (store primary tag for future analytics)
        // Only persist after ensuring we have a diversified voice (avoid locking into Adam/Rachel immediately for generic NPCs)
        if (!looksDefaultPair(sel.voiceName)) {
            store.put(key, VoiceAssignmentStore.VoiceAssignment.auto(provider, sel.voiceName, sel.voiceName, primaryTag));
        }
        return sel;
    }

    private boolean looksElevenId(String v) {
        if (v == null) return false;
        int i = v.lastIndexOf('(');
        int j = v.lastIndexOf(')');
        if (i >= 0 && j > i) {
            String id = v.substring(i+1, j).trim();
            return id.matches("[A-Za-z0-9]{20,}");
        }
        return v.matches("[A-Za-z0-9]{20,}");
    }

    private String randomFromTag(String tag, String seed) {
        // Use a time component to allow variability before persistence
        if (tag == null || elevenCatalog == null) return null;
        elevenCatalog.ensureLoaded();
        // Reuse deterministicForTag but add a mild salt for first-time selection
        String salted = seed + "|rand|" + System.nanoTime();
        String candidate = elevenCatalog.deterministicForTag(tag, salted);
        return candidate;
    }

    private String rotatingRandomFromTag(String tag, String gender, String seed) {
        if (tag == null || elevenCatalog == null) return null;
        elevenCatalog.ensureLoaded();
        List<String> pool = tagVoicePools.computeIfAbsent(tag.toLowerCase(Locale.ROOT), t -> buildPoolForTag(t));
        if (pool == null || pool.isEmpty()) return null;
        // Filter by gender if available and at least 5 candidates after filter; else use full pool
        List<String> genderFiltered = null;
        if (gender != null) {
            String g = gender.toLowerCase(Locale.ROOT);
            genderFiltered = pool.stream().filter(v -> (g.equals("female") && looksFemaleName(v)) || (g.equals("male") && looksMaleName(v)) || (g.equals("kid") && v.toLowerCase(Locale.ROOT).contains("bella"))).collect(Collectors.toList());
            if (genderFiltered.size() < 5) genderFiltered = null; // fallback to full pool if too small
        }
        List<String> usePool = genderFiltered != null ? genderFiltered : pool;
        // Rotation index increments each time we assign a new voice for a tag
        int idx = tagRotationIndex.merge(tag.toLowerCase(Locale.ROOT), 1, Integer::sum) - 1;
        if (idx < 0) idx = 0;
        int picked = Math.abs(idx) % usePool.size();
        return usePool.get(picked);
    }

    private List<String> buildPoolForTag(String tag) {
        // Build a large, stable pseudo-random ordering for tag voices using hash of id+tag
        java.util.List<String> pool = new java.util.ArrayList<>();
        // Start with deterministic base voice
        String base = elevenCatalog.deterministicForTag(tag, tag+":base");
        if (base != null) pool.add(base);
        // Expand by mixing gender + tag voice lists if available
        for (String g : List.of("male","female","kid")) {
            String gPick = elevenCatalog.anyVoiceIdLikeGender(g, tag+":"+g);
            if (gPick != null && !pool.contains(gPick)) pool.add(gPick);
        }
        // As we lack a direct listing API by tag, synthesize extra candidates via salted deterministicForTag
        for (int i=0;i<400;i++) { // attempt hundreds of unique
            String v = elevenCatalog.deterministicForTag(tag, tag+"|extra|"+i);
            if (v != null && looksElevenId(v) && !pool.contains(v)) pool.add(v);
            if (pool.size() >= 500) break; // cap
        }
        // Shuffle with stable seed for variety but reproducibility across session
        java.util.Random r = new java.util.Random(tag.hashCode());
        for (int i=pool.size()-1;i>0;i--) { int j = r.nextInt(i+1); var tmp = pool.get(i); pool.set(i,pool.get(j)); pool.set(j,tmp); }
        return pool;
    }

    private String pooledPickForTag(String tag, String gender, String seedKey) {
        if (tag == null) return null;
        // Primary attempt: rotating pool (does not advance rotation yet)
        List<String> pool = tagVoicePools.computeIfAbsent(tag.toLowerCase(Locale.ROOT), t -> buildPoolForTag(t));
        if (pool == null || pool.isEmpty()) return null;
        // deterministic pick without rotation side effects
        int idx = Math.abs((seedKey + "|pre|" + tag).hashCode()) % pool.size();
        String candidate = pool.get(idx);
        if (gender != null) {
            String g = gender.toLowerCase(Locale.ROOT);
            if (g.equals("female") && looksMaleName(candidate)) return null; // force later gender aware path
            if (g.equals("male") && looksFemaleName(candidate)) return null;
        }
        return candidate;
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

    private boolean looksDefaultPair(String v) {
        if (v == null) return false;
        String s = v.toLowerCase(Locale.ROOT);
        return s.contains("adam") || s.contains("rachel");
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
