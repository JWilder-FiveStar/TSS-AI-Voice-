package com.example.osrstts.voice;

import java.nio.file.*;
import java.util.*;

/**
 * Generates an auto quest NPC mapping JSON based on a simple name list.
 * Heuristics: infer gender + lore tag keywords -> pick ElevenLabs voice + style.
 * Activated when system property osrs.tts.autoQuestMap=true and a file quest-voices/quest-npc-master-list.txt exists.
 */
class QuestVoiceAutoGenerator {
    private static final String OUTPUT = "quest-voices/quest-npc-auto.json";

    static void generateIfRequested() {
        if (!Boolean.getBoolean("osrs.tts.autoQuestMap")) return;
        try {
            Path master = Path.of("quest-voices/quest-npc-master-list.txt");
            if (!Files.exists(master)) return;
            Path out = Path.of(OUTPUT);
            List<String> names = Files.readAllLines(master);
            Map<String,String> npcExact = new TreeMap<>();
            for (String raw : names) {
                String name = raw == null ? null : raw.trim();
                if (name == null || name.isEmpty() || name.startsWith("#")) continue;
                npcExact.put(name, assign(name));
            }
            StringBuilder sb = new StringBuilder();
            sb.append("{\n  \"npcExact\": {\n");
            boolean first = true;
            for (var e : npcExact.entrySet()) {
                if (!first) sb.append(",\n");
                first = false;
                sb.append("    \"").append(escape(e.getKey())).append("\": \"").append(escape(e.getValue())).append("\"");
            }
            sb.append("\n  }\n}\n");
            Files.createDirectories(out.getParent());
            Files.writeString(out, sb.toString(), java.nio.charset.StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {}
    }

    private static String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    private static String assign(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        String gender = inferGender(lower);
        // Tag precedence
        if (lower.contains("king") || lower.contains("duke") || lower.contains("sir") || lower.contains("lord")) {
            return voice(gender, "royal");
        }
        if (lower.contains("queen") || lower.contains("princess") || lower.contains("lady") || lower.contains("duchess")) {
            return voice("female", "regal");
        }
        if (lower.contains("wizard") || lower.contains("mage") || lower.contains("sorcer") || lower.contains("merlin") || lower.contains("archmage")) {
            return wizardVoice(gender);
        }
        if (lower.contains("vampire") || lower.contains("count") || lower.contains("draynor")) {
            return maleOrFemale(gender, "Arnold (VR6AewLTigWG4xSOukaG)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "sinister");
        }
        if (lower.contains("undead") || lower.contains("zombie") || lower.contains("ghost") || lower.contains("skeleton") || lower.contains("shade")) {
            return maleOrFemale(gender, "Arnold (VR6AewLTigWG4xSOukaG)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "haunting");
        }
        if (lower.contains("goblin")) {
            return "ElevenLabs:Sam (yoZ06aMxZJJ28mfd3POQ)|style=goblin";
        }
        if (lower.contains("gnome")) {
            return "ElevenLabs:Bella (EXAVITQu4vr4xnSDxMaL)|style=fae";
        }
        if (lower.contains("dwarf") || lower.contains("thurgo")) {
            return "ElevenLabs:Arnold (VR6AewLTigWG4xSOukaG)|style=gruff";
        }
        if (lower.contains("elf") || lower.contains("arianwyn") || lower.contains("seren")) {
            return femaleOrMale(gender, "Elli (MF3mGyEYCl7XYWbV9V6O)", "Antoni (ErXwobaYiN019PkySvjV)", "ethereal");
        }
        if (lower.contains("pirate") || lower.contains("sailor") || lower.contains("seaman")) {
            return "ElevenLabs:Arnold (VR6AewLTigWG4xSOukaG)|style=pirate";
        }
        if (lower.contains("bandit") || lower.contains("brigand") || lower.contains("highwayman")) {
            return maleOrFemale(gender, "Adam (pNInz6obpgDQGcFmaJgB)", "Rachel (21m00Tcm4TlvDq8ikWAM)", "rough");
        }
        if (lower.contains("barbarian") || lower.contains("fremennik")) {
            return "ElevenLabs:Arnold (VR6AewLTigWG4xSOukaG)|style=booming";
        }
        if (lower.contains("monk") || lower.contains("priest") || lower.contains("drezel") || lower.contains("father ")) {
            return "ElevenLabs:Antoni (ErXwobaYiN019PkySvjV)|style=devout";
        }
        if (lower.contains("nun")) {
            return "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)|style=serene";
        }
        if (lower.contains("witch") || lower.contains("enchanter")) {
            return "ElevenLabs:Dorothy (ThT5KcBeYPX3keUQqHPh)|style=arcane";
        }
        if (lower.contains("desert") || lower.contains("menaph") || lower.contains("al kharid") || lower.contains("ali ")) {
            return maleOrFemale(gender, "Josh (TxGEqnHWrfWFTfGW9XjX)", "Rachel (21m00Tcm4TlvDq8ikWAM)", "desert");
        }
        if (lower.contains("morytania") || lower.contains("canifis") || lower.contains("meyerditch")) {
            return maleOrFemale(gender, "Arnold (VR6AewLTigWG4xSOukaG)", "Dorothy (ThT5KcBeYPX3keUQqHPh)", "grim");
        }
        if (lower.contains("guard") || lower.contains("knight") || lower.contains("soldier") || lower.contains("paladin")) {
            return maleOrFemale(gender, "Josh (TxGEqnHWrfWFTfGW9XjX)", "Rachel (21m00Tcm4TlvDq8ikWAM)", "firm");
        }
        // Generic gendered fallbacks
        if ("female".equals(gender)) return "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)|style=story";
        return "ElevenLabs:Adam (pNInz6obpgDQGcFmaJgB)|style=story";
    }

    private static String voice(String gender, String style) {
        if ("female".equals(gender)) return "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)|style="+style;
        return "ElevenLabs:Adam (pNInz6obpgDQGcFmaJgB)|style="+style;
    }
    private static String wizardVoice(String gender) {
        if ("female".equals(gender)) return "ElevenLabs:Elli (MF3mGyEYCl7XYWbV9V6O)|style=arcane";
        return "ElevenLabs:Antoni (ErXwobaYiN019PkySvjV)|style=mystic";
    }
    private static String maleOrFemale(String gender, String male, String female, String style) {
        if ("female".equals(gender)) return "ElevenLabs:"+female+"|style="+style;
        return "ElevenLabs:"+male+"|style="+style;
    }
    private static String femaleOrMale(String gender, String female, String male, String style) {
        if ("female".equals(gender)) return "ElevenLabs:"+female+"|style="+style;
        return "ElevenLabs:"+male+"|style="+style;
    }
    private static String inferGender(String lower) {
        if (lower.contains("king") || lower.contains("sir ") || lower.contains("lord") || lower.contains("duke") || lower.contains("count")) return "male";
        if (lower.contains("queen") || lower.contains("lady ") || lower.contains("princess") || lower.contains("duchess")) return "female";
        // heuristic suffixes
        if (lower.endsWith("a") || lower.endsWith("ia") || lower.endsWith("essa") || lower.endsWith("ette") || lower.endsWith("ine") ) return "female";
        return "male"; // bias
    }
}
