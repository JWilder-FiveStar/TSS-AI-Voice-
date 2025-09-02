package com.example.osrstts.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class NpcMetadataService {
    private static final ObjectMapper M = new ObjectMapper();
    private final Map<String,String> tagGender = new HashMap<>();
    private final Set<String> knownMale = new HashSet<>(Arrays.asList("man","brother","sir","king","lord","duke","prince","father","monk","boy"));
    private final Set<String> knownFemale = new HashSet<>(Arrays.asList("woman","sister","lady","queen","duchess","princess","mother","nun","girl","madam"));

    public static class NpcMetadata {
        public final String npcKey; // prefer id if available else canonicalized name
        public final String name;
        public final Integer id;
        public final String gender; // male | female | unknown
        public final Set<String> tags;
        public NpcMetadata(String npcKey, String name, Integer id, String gender, Set<String> tags) {
            this.npcKey = npcKey; this.name = name; this.id = id; this.gender = gender; this.tags = tags;
        }
    }

    public NpcMetadataService() {
        loadFromVoicesJson();
    }

    private void loadFromVoicesJson() {
        try {
            Path p = Path.of("osrs-voices.json");
            InputStream is = Files.exists(p) ? Files.newInputStream(p) : NpcMetadataService.class.getClassLoader().getResourceAsStream("osrs-voices.json");
            if (is == null) return;
            JsonNode root = M.readTree(is);
            JsonNode tags = root.path("tags");
            if (tags.isObject()) {
                Iterator<String> it = tags.fieldNames();
                while (it.hasNext()) {
                    String t = it.next();
                    if ("male".equalsIgnoreCase(t) || "female".equalsIgnoreCase(t)) tagGender.put(t.toLowerCase(Locale.ROOT), t.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
    }

    public NpcMetadata get(Integer npcId, String npcName) {
        String name = (npcName == null) ? "" : sanitize(npcName);
        String gender = inferGender(name);
        Set<String> tags = inferTags(name);
        String key = (npcId != null && npcId > 0) ? ("id:" + npcId) : name.toLowerCase(Locale.ROOT);
        return new NpcMetadata(key, name, npcId, gender, tags);
    }

    private String sanitize(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "").replace('_', ' ').trim();
    }

    private String inferGender(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String w : knownMale) if (n.contains(w)) return "male";
        for (String w : knownFemale) if (n.contains(w)) return "female";
        // Name suffix heuristics
        String[] femaleSuffixes = {"a","ia","na","la","ra","ssa","ella","ette","ine","lyn","lynn","beth","anne","anna","eva","ina","ika"};
        String[] maleSuffixes = {"us","o","an","en","ar","ik","as","is","or","er","ath","son"};
        for (String s : femaleSuffixes) if (n.endsWith(s)) return "female";
        for (String s : maleSuffixes) if (n.endsWith(s)) return "male";
        return "unknown";
    }

    private Set<String> inferTags(String name) {
        Set<String> tags = new HashSet<>();
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("guard")) tags.add("guard");
        if (n.contains("wizard") || n.contains("mage") || n.contains("sorcer")) tags.add("wizard");
        if (n.contains("king") || n.contains("duke") || n.contains("sir") || n.contains("lord") || n.contains("prince")) { tags.add("royalty"); tags.add("male"); }
        if (n.contains("queen") || n.contains("lady") || n.contains("princess") || n.contains("dame") || n.contains("madam") || n.contains("mrs") || n.contains("miss") || n.contains("priestess")) { tags.add("royalty"); tags.add("female"); }
        if (n.contains("vamp")) tags.add("vampire");
        if (n.contains("werewolf")) tags.add("werewolf");
        if (n.contains("ghost") || n.contains("spirit")) tags.add("ghost");
        if (n.contains("skeleton")) tags.add("skeleton");
        if (n.contains("zombie")) tags.add("zombie");
        if (n.contains("shade")) tags.add("shade");
        if (n.contains("undead")) tags.add("undead");
        if (n.contains("monk")) { tags.add("monk"); tags.add("male"); }
        if (n.contains("nun")) { tags.add("nun"); tags.add("female"); }
        if (n.contains("pirate")) tags.add("pirate");
        if (n.contains("dwarf")) tags.add("dwarf");
        if (n.contains("goblin")) tags.add("goblin");
        if (n.contains("gnome")) tags.add("gnome");
        if (n.contains("elf") || n.contains("elven")) tags.add("elf");
        if (n.contains("troll")) tags.add("troll");
        if (n.contains("ogre")) tags.add("ogre");
        if (n.contains("giant")) tags.add("giant");
        if (n.contains("druid")) tags.add("druid");
        if (n.contains("ranger") || n.contains("archer") || n.contains("bowman")) tags.add("ranger");
        if (n.contains("barbarian")) tags.add("barbarian");
        if (n.contains("sailor") || n.contains("seaman")) tags.add("sailor");
        if (n.contains("fisherman")) tags.add("fisherman");
        if (n.contains("miner")) tags.add("miner");
        if (n.contains("smith")) tags.add("smith");
        if (n.contains("boy") || n.contains("girl") || n.contains("child") || n.contains("kid") || n.contains("young")) tags.add("kid");
        if (n.contains("man") || n.endsWith("son")) tags.add("male");
        if (n.contains("woman") || n.endsWith("daughter")) tags.add("female");
        return tags;
    }
}
