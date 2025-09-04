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
    private final Map<String,String> genderOverrides = new HashMap<>();
    private final Set<String> knownMale = new HashSet<>(Arrays.asList(
            "man","sir","lord","king","duke","prince","father","brother","boy",
            // Common OSRS male NPC names including critical ones like Aubury
            "hans","bob","ned","reldo","thurgo","oziach","aubury","cook","martin","wizard",
            "adam","antoni","josh","arnold","sam","alex","ethan","brian","charlie","dan","finn",
            "george","henry","isaac","jack","kyle","luke","mike","nathan","owen","peter",
            "quinn","ryan","steve","tyler","victor","will","xavier","zach","aaron","blake"
    ));
    private final Set<String> knownFemale = new HashSet<>(Arrays.asList(
            "woman","sister","lady","queen","duchess","princess","mother","nun","girl","madam",
            // Common OSRS female NPC names
            "betty","alice","aggie","aria","biddy","catherine","dorothea","ellie","fiona","grace","hetty","jill","kaylee","lisa","melanie","olivia","philia","sarah","sophia","thessalia","valerie","yvonne",
            "rachel","bella","elli","dorothy","natasha","serena","glinda","freya","mimi","nova"
    ));

    public static class NpcMetadata {
        public final String primaryTag;
        public final String gender;
        public final List<String> allTags;

        public NpcMetadata(String primaryTag, String gender, List<String> allTags) {
            this.primaryTag = primaryTag;
            this.gender = gender;
            this.allTags = allTags;
        }
    }

    public NpcMetadataService() {
        loadGenderOverrides();
        initializeTagGenderMappings();
        
        // Add some critical hardcoded overrides as fallback
        if (!genderOverrides.containsKey("dr fenkenstrain")) {
            genderOverrides.put("dr fenkenstrain", "male");
            genderOverrides.put("fenkenstrain", "male");
        }
        if (!genderOverrides.containsKey("aubury")) {
            genderOverrides.put("aubury", "male");
        }
    }

    private void loadGenderOverrides() {
        // Try multiple possible locations for the gender override file
        String[] paths = {
            "npc-gender-overrides.json",
            "../npc-gender-overrides.json", 
            "../../npc-gender-overrides.json",
            "../../../npc-gender-overrides.json",
            System.getProperty("user.dir") + "/npc-gender-overrides.json"
        };
        
        for (String pathStr : paths) {
            Path overridePath = Path.of(pathStr);
            if (Files.exists(overridePath)) {
                try {
                    String content = Files.readString(overridePath);
                    JsonNode root = M.readTree(content);
                    root.fieldNames().forEachRemaining(name -> {
                        genderOverrides.put(name.toLowerCase(), root.get(name).asText());
                    });
                    System.out.println("Loaded " + genderOverrides.size() + " gender overrides from " + pathStr);
                    return; // Found and loaded successfully
                } catch (Exception e) {
                    System.err.println("Failed to load gender overrides from " + pathStr + ": " + e.getMessage());
                }
            }
        }
        
        // If no file found, log the search paths for debugging
        System.err.println("Gender override file not found. Searched paths: " + String.join(", ", paths));
    }

    private void initializeTagGenderMappings() {
        // Tag-based gender associations
        tagGender.put("nun", "female");
        tagGender.put("sister", "female");
        tagGender.put("lady", "female");
        tagGender.put("queen", "female");
        tagGender.put("duchess", "female");
        tagGender.put("princess", "female");
        tagGender.put("mother", "female");
        tagGender.put("barmaid", "female");
        tagGender.put("seamstress", "female");
        tagGender.put("witch", "female");

        tagGender.put("sir", "male");
        tagGender.put("lord", "male");
        tagGender.put("king", "male");
        tagGender.put("duke", "male");
        tagGender.put("prince", "male");
        tagGender.put("father", "male");
        tagGender.put("brother", "male");
        tagGender.put("monk", "male");
        tagGender.put("knight", "male");
        tagGender.put("priest", "male");
        tagGender.put("wizard", "male");
        tagGender.put("smith", "male");
        tagGender.put("barbarian", "male");
        tagGender.put("warlord", "male");
        tagGender.put("chieftain", "male");
    }

    public NpcMetadata analyzeNpc(String npcName) {
        String cleanName = npcName.toLowerCase().trim();
        String primaryTag = inferPrimaryTag(cleanName);
        String gender = inferGender(cleanName, primaryTag);
        List<String> allTags = inferAllTags(cleanName);

        return new NpcMetadata(primaryTag, gender, allTags);
    }

    private String inferGender(String cleanName, String primaryTag) {
        // Priority 1: Check override file first
        if (genderOverrides.containsKey(cleanName)) {
            return genderOverrides.get(cleanName);
        }

        // Priority 2: Check exact name matches
        if (knownMale.contains(cleanName)) return "male";
        if (knownFemale.contains(cleanName)) return "female";

        // Priority 3: Check tag-based gender
        if (tagGender.containsKey(primaryTag)) {
            return tagGender.get(primaryTag);
        }

        // Priority 4: Check for gendered words in the name
        for (String male : knownMale) {
            if (cleanName.contains(male)) return "male";
        }
        for (String female : knownFemale) {
            if (cleanName.contains(female)) return "female";
        }

        // Priority 5: Check common name endings
        if (cleanName.endsWith("a") || cleanName.endsWith("ia") || cleanName.endsWith("ella")) {
            return "female";
        }

        // Default: neutral
        return "neutral";
    }

    private String inferPrimaryTag(String cleanName) {
        // Region/location patterns
        if (cleanName.contains("fremennik")) return "fremennik";
        if (cleanName.contains("tzhaar")) return "tzhaar";
        if (cleanName.contains("barbarian")) return "barbarian";
        if (cleanName.contains("dwarf") || cleanName.contains("dwarv")) return "dwarf";
        if (cleanName.contains("elf") || cleanName.contains("elv")) return "elf";
        if (cleanName.contains("gnome")) return "gnome";
        if (cleanName.contains("goblin")) return "goblin";
        if (cleanName.contains("troll")) return "troll";
        if (cleanName.contains("ogre")) return "ogre";
        if (cleanName.contains("giant")) return "giant";

        // Profession patterns
        if (cleanName.contains("guard")) return "guard";
        if (cleanName.contains("knight")) return "knight";
        if (cleanName.contains("wizard")) return "wizard";
        if (cleanName.contains("monk")) return "monk";
        if (cleanName.contains("priest")) return "priest";
        if (cleanName.contains("nun")) return "nun";
        if (cleanName.contains("smith")) return "smith";
        if (cleanName.contains("miner")) return "miner";
        if (cleanName.contains("farmer")) return "farmer";
        if (cleanName.contains("fisher")) return "fisherman";
        if (cleanName.contains("sailor")) return "sailor";
        if (cleanName.contains("pirate")) return "pirate";
        if (cleanName.contains("bandit")) return "bandit";
        if (cleanName.contains("merchant")) return "merchant";
        if (cleanName.contains("shopkeeper")) return "shopkeeper";
        if (cleanName.contains("banker")) return "banker";

        // Title patterns
        if (cleanName.contains("king")) return "royalty";
        if (cleanName.contains("queen")) return "royalty";
        if (cleanName.contains("prince")) return "royalty";
        if (cleanName.contains("princess")) return "royalty";
        if (cleanName.contains("duke")) return "nobility";
        if (cleanName.contains("duchess")) return "nobility";
        if (cleanName.contains("lord")) return "nobility";
        if (cleanName.contains("lady")) return "nobility";
        if (cleanName.contains("sir")) return "nobility";

        // Creature types
        if (cleanName.contains("vampire")) return "vampire";
        if (cleanName.contains("werewolf")) return "werewolf";
        if (cleanName.contains("ghost")) return "ghost";
        if (cleanName.contains("skeleton")) return "skeleton";
        if (cleanName.contains("zombie")) return "zombie";
        if (cleanName.contains("demon")) return "demon";
        if (cleanName.contains("dragon")) return "dragon";

        // Age/social patterns
        if (cleanName.contains("child") || cleanName.contains("kid") || cleanName.contains("boy") || cleanName.contains("girl")) {
            return "kid";
        }
        if (cleanName.contains("elder") || cleanName.contains("wise") || cleanName.contains("old")) {
            return "elder";
        }

        // Default to citizen for unidentified NPCs
        return "citizen";
    }

    private List<String> inferAllTags(String cleanName) {
        List<String> tags = new ArrayList<>();
        tags.add(inferPrimaryTag(cleanName));

        String gender = inferGender(cleanName, tags.get(0));
        if (!gender.equals("neutral")) {
            tags.add(gender);
        }

        return tags;
    }
}
