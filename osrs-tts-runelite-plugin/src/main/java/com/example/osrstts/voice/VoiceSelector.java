package com.example.osrstts.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

public class VoiceSelector {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String provider;          // "Azure" | "Polly" | "ElevenLabs"
    private final String defaultVoice;      // "auto" or provider voice name
    private final Map<String, String> exactNpcMap = new HashMap<>();
    private final Map<String, String> tagMap = new HashMap<>();
    private final List<Map.Entry<Pattern, String>> regexNpcMap = new ArrayList<>();

    private final String npcMaleVoice;
    private final String npcFemaleVoice;
    private final String npcKidVoice;

    // Built-in lore defaults for tags when mapping file lacks an entry (Azure-oriented)
    private static final Map<String, String> DEFAULT_TAG_VOICE_AZURE;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("royalty", "en-GB-OwenNeural");
        m.put("bandit", "en-IE-ConnorNeural");
        m.put("pirate", "en-AU-WilliamNeural");
        m.put("dwarf", "en-GB-GeorgeNeural");
        m.put("goblin", "en-GB-ArchieNeural");
        m.put("gnome", "en-GB-AlfieNeural");
        m.put("elf", "en-GB-OliverNeural");
        m.put("troll", "en-GB-ThomasNeural");
        m.put("ogre", "en-IE-ConnorNeural");
        m.put("giant", "en-GB-OwenNeural");
        m.put("wizard", "en-GB-AlfieNeural");
        m.put("guard", "en-GB-RyanNeural");
        m.put("monk", "en-GB-ElliotNeural");
        m.put("nun", "en-GB-SoniaNeural");
        m.put("druid", "en-GB-OwenNeural");
        m.put("ranger", "en-GB-ThomasNeural");
        m.put("hunter", "en-GB-ThomasNeural");
        m.put("archer", "en-GB-ThomasNeural");
        m.put("barbarian", "en-IE-ConnorNeural");
        m.put("sailor", "en-AU-WilliamNeural");
        m.put("seaman", "en-AU-WilliamNeural");
        m.put("fisherman", "en-IE-ConnorNeural");
        m.put("miner", "en-GB-GeorgeNeural");
        m.put("smith", "en-GB-ThomasNeural");
        m.put("vampire", "en-GB-NoahNeural");
        m.put("werewolf", "en-GB-NoahNeural");
        m.put("ghost", "en-GB-BrianNeural");
        m.put("skeleton", "en-GB-BrianNeural");
        m.put("zombie", "en-GB-BrianNeural");
        m.put("shade", "en-GB-BrianNeural");
        m.put("undead", "en-GB-BrianNeural");
        // Regions/factions
        m.put("fremennik", "en-IE-ConnorNeural");
        m.put("morytania", "en-GB-NoahNeural");
        m.put("desert", "en-AU-NatashaNeural");
        m.put("menaphite", "en-AU-NatashaNeural");
        m.put("al kharid", "en-AU-NatashaNeural");
        m.put("kandarin", "en-GB-RyanNeural");
        m.put("asgarnia", "en-GB-RyanNeural");
        m.put("misthalin", "en-GB-RyanNeural");
        m.put("varrock", "en-GB-RyanNeural");
        m.put("lumbridge", "en-GB-RyanNeural");
        m.put("falador", "en-GB-RyanNeural");
        m.put("ardougne", "en-GB-ThomasNeural");
        m.put("kourend", "en-GB-RyanNeural");
        m.put("shayzien", "en-GB-RyanNeural");
        m.put("arceuus", "en-GB-NoahNeural");
        m.put("hosidius", "en-GB-GeorgeNeural");
        m.put("lovakengj", "en-GB-ThomasNeural");
        m.put("piscarilius", "en-IE-ConnorNeural");
        m.put("karamja", "en-AU-WilliamNeural");
        m.put("tzhaar", "en-GB-OliverNeural");
        m.put("tirannwn", "en-GB-OliverNeural");
        m.put("prifddinas", "en-GB-OliverNeural");
        m.put("wilderness", "en-GB-NoahNeural");
        m.put("khazard", "en-GB-ThomasNeural");
        m.put("zamorak", "en-GB-NoahNeural");
        m.put("saradomin", "en-GB-ElliotNeural");
        m.put("guthix", "en-GB-OwenNeural");
        m.put("bandos", "en-IE-ConnorNeural");
        m.put("armadyl", "en-GB-OliverNeural");
        // Gender fallbacks kept generic
        m.put("male", "en-GB-RyanNeural");
        m.put("female", "en-GB-LibbyNeural");
        m.put("kid", "en-GB-MaisieNeural");
        DEFAULT_TAG_VOICE_AZURE = Collections.unmodifiableMap(m);
    }

    // ElevenLabs defaults (Name (voice_id)) for core tags
    private static final Map<String, String> DEFAULT_TAG_VOICE_ELEVEN;
    static {
        Map<String, String> e = new HashMap<>();
        e.put("royalty", "Adam (pNInz6obpgDQGcFmaJgB)");
        e.put("wizard", "Antoni (ErXwobaYiN019PkySvjV)");
        e.put("guard", "Josh (TxGEqnHWrfWFTfGW9XjX)");
        e.put("pirate", "Arnold (VR6AewLTigWG4xSOukaG)");
        e.put("vampire", "Dorothy (ThT5KcBeYPX3keUQqHPh)");
        e.put("undead", "Dorothy (ThT5KcBeYPX3keUQqHPh)");
        e.put("ghost", "Dorothy (ThT5KcBeYPX3keUQqHPh)");
        e.put("barbarian", "Adam (pNInz6obpgDQGcFmaJgB)");
        e.put("elf", "Elli (MF3mGyEYCl7XYWbV9V6O)");
        e.put("dwarf", "Rachel (21m00Tcm4TlvDq8ikWAM)");
        e.put("goblin", "Sam (yoZ06aMxZJJ28mfd3POQ)");
        e.put("gnome", "Bella (EXAVITQu4vr4xnSDxMaL)");
        e.put("ranger", "Adam (pNInz6obpgDQGcFmaJgB)");
        e.put("male", "Adam (pNInz6obpgDQGcFmaJgB)");
        e.put("female", "Rachel (21m00Tcm4TlvDq8ikWAM)");
        e.put("kid", "Bella (EXAVITQu4vr4xnSDxMaL)");
        DEFAULT_TAG_VOICE_ELEVEN = Collections.unmodifiableMap(e);
    }

    private static final String[] MALE_POOL = new String[]{
            "en-GB-RyanNeural", "en-GB-ThomasNeural", "en-GB-NoahNeural", "en-GB-OwenNeural"
    };
    private static final String[] FEMALE_POOL = new String[]{
            "en-GB-LibbyNeural", "en-GB-SoniaNeural", "en-GB-MaisieNeural"
    };
    private static final String[] KID_POOL = new String[]{
        "en-GB-MaisieNeural", "en-US-JennyNeural"
    };
    private static final String[] AZURE_FALLBACK_POOL = new String[]{
            "en-US-JennyNeural", "en-US-GuyNeural", "en-US-DavisNeural",
            "en-GB-RyanNeural", "en-GB-LibbyNeural", "en-GB-OwenNeural", "en-GB-SoniaNeural"
    };
    private static final String[] POLLY_FALLBACK_POOL = new String[]{
            "Matthew", "Joanna", "Joey", "Salli", "Ivy"
    };
    private static final java.util.Random RNG = new java.util.Random();

    public VoiceSelector(String provider, String defaultVoice, String mappingFilePath,
                         String npcMaleVoice, String npcFemaleVoice, String npcKidVoice) {
        this.provider = provider;
        this.defaultVoice = defaultVoice;
        this.npcMaleVoice = npcMaleVoice;
        this.npcFemaleVoice = npcFemaleVoice;
        this.npcKidVoice = npcKidVoice;
        loadMapping(mappingFilePath);
    }

    private void loadMapping(String path) {
        try {
            if (path != null) {
                File f = new File(path);
                if (f.exists()) {
                    String json = Files.readString(f.toPath());
                    parseMappingJson(json);
                    return;
                }
            }
            // Fallback to classpath resource
            try (java.io.InputStream is = VoiceSelector.class.getClassLoader().getResourceAsStream("osrs-voices.json")) {
                if (is != null) {
                    String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    parseMappingJson(json);
                }
            }
        } catch (Exception ignored) {}
    }

    private void parseMappingJson(String json) {
        try {
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
            // Mapping file tag->voice first
            for (String tag : inferredTags) {
                String mapped = tagMap.get(tag.toLowerCase(Locale.ROOT));
                if (mapped != null) { voice = mapped; break; }
            }
            // Built-in defaults for lore tags by provider
            if (voice == null) {
                Map<String,String> defaults = "ElevenLabs".equalsIgnoreCase(provider) ? DEFAULT_TAG_VOICE_ELEVEN : DEFAULT_TAG_VOICE_AZURE;
                for (String tag : inferredTags) {
                    String mapped = defaults.get(tag.toLowerCase(Locale.ROOT));
                    if (mapped != null) { voice = mapped; break; }
                }
            }
            // Category pools (deterministic by name) only for Azure/Polly
            if (voice == null && !"ElevenLabs".equalsIgnoreCase(provider)) {
                int seed = Math.abs((npcName != null ? npcName : "").hashCode());
                if (hasAny(inferredTags, "kid")) {
                    voice = chooseFrom(seed, npcKidVoice, KID_POOL);
                } else if (hasAny(inferredTags, "female")) {
                    voice = chooseFrom(seed, npcFemaleVoice, FEMALE_POOL);
                } else if (hasAny(inferredTags, "male")) {
                    voice = chooseFrom(seed, npcMaleVoice, MALE_POOL);
                }
            }
        }

        // If still no voice from mappings/tags
        if (voice == null) {
            if ("ElevenLabs".equalsIgnoreCase(provider)) {
                voice = "Rachel (21m00Tcm4TlvDq8ikWAM)"; // safe default id
            } else {
                String gender = guessGender(npcName);
                int seed = Math.abs((npcName != null ? npcName : "").hashCode());
                if ("female".equals(gender)) {
                    voice = chooseFrom(seed, npcFemaleVoice, FEMALE_POOL);
                } else if ("male".equals(gender)) {
                    voice = chooseFrom(seed, npcMaleVoice, MALE_POOL);
                }
            }
        }

        if (voice == null || "auto".equalsIgnoreCase(voice)) {
            voice = autoDefaultVoice(npcName);
        }

        String style = inferStyle(lineText);
        return VoiceSelection.of(voice, style);
    }

    // Simple gender guesser by name; biased to male if ambiguous
    private String guessGender(String npcName) {
        if (npcName == null || npcName.isBlank()) return null;
        String n = npcName.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        // Known exceptions and obvious names
        Set<String> maleNames = Set.of("noah","adam","arthur","merlin","lancelot","galahad","hans","ryan","owen","george");
        Set<String> femaleNames = Set.of("alina","anna","anne","ella","jenny","libby","sonia","maisie");
        if (maleNames.contains(n)) return "male";
        if (femaleNames.contains(n)) return "female";
        // Heuristic suffixes
        String[] femaleSuffixes = {"a","ia","na","la","ra","ssa","ella","ette","ine","lyn","lynn","beth","anne","anna","eva","ina","ika"};
        String[] maleSuffixes = {"us","o","an","en","ar","ik","as","is","or","er","ath","son"};
        for (String suf : femaleSuffixes) { if (n.endsWith(suf)) return "female"; }
        for (String suf : maleSuffixes) { if (n.endsWith(suf)) return "male"; }
        return "male"; // default bias
    }

    private static boolean hasAny(Set<String> tags, String... keys) {
        if (tags == null || tags.isEmpty()) return false;
        for (String k : keys) {
            if (tags.contains(k)) return true;
        }
        return false;
    }

    private static String chooseFrom(int seed, String preferred, String[] pool) {
        if (preferred != null && !preferred.isBlank()) return preferred;
        if (pool == null || pool.length == 0) return null;
        int idx = seed % pool.length;
        return pool[idx];
    }

    private String autoDefaultVoice(String npcName) {
        if (defaultVoice != null && !defaultVoice.isBlank() && !"auto".equalsIgnoreCase(defaultVoice)) {
            return defaultVoice;
        }
        String[] pool = "Polly".equalsIgnoreCase(provider) ? POLLY_FALLBACK_POOL : AZURE_FALLBACK_POOL;
        if (pool.length == 0) {
            return "Polly".equalsIgnoreCase(provider) ? "Matthew" : "en-US-JennyNeural";
        }
        boolean stable = Boolean.getBoolean("osrs.tts.stableFallback");
        if (stable && npcName != null && !npcName.isBlank()) {
            int idx = Math.abs(npcName.hashCode()) % pool.length;
            return pool[idx];
        }
        // Random by default
        return pool[RNG.nextInt(pool.length)];
    }

    /**
     * Get the current provider for this selector.
     */
    public String getProvider() {
        return provider;
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
