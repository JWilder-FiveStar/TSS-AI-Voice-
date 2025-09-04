package com.example.osrstts.npc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;

/**
 * One-shot scraper for Old School RuneScape NPC names using the public OSRS Wiki MediaWiki API.
 * Writes npc-names.json and (optionally) npc-gender-overrides.json with heuristic genders.
 * NOTE: Avoid hammering the API; we self-throttle and limit to safe rate.
 */
public class NpcWikiScraper {
    private static final Logger log = LoggerFactory.getLogger(NpcWikiScraper.class);
    private static final ObjectMapper M = new ObjectMapper();
    private static final String API = "https://oldschool.runescape.wiki/api.php";
    private static final int LIMIT = 500; // max per page
    private static final int MAX_PAGES = 400; // safety
    private static final long THROTTLE_MS = 350; // polite delay

    public Path run(boolean writeGenderFile) throws IOException, InterruptedException {
        List<String> names = fetchAll();
        Path dir = Paths.get("config","osrs-tts");
        Files.createDirectories(dir);
        Path out = dir.resolve("npc-names.json");
        ObjectNode root = M.createObjectNode();
        root.put("fetchedAtEpochMs", Instant.now().toEpochMilli());
        ArrayNode arr = root.putArray("names");
        names.forEach(arr::add);
        Files.writeString(out, M.writerWithDefaultPrettyPrinter().writeValueAsString(root), StandardCharsets.UTF_8);
        log.info("Saved {} NPC names -> {}", names.size(), out.toAbsolutePath());
        if (writeGenderFile) {
            Map<String,String> genders = inferGenders(names);
            Path g = dir.resolve("npc-gender-overrides.json");
            Files.writeString(g, M.writerWithDefaultPrettyPrinter().writeValueAsString(genders), StandardCharsets.UTF_8);
            log.info("Saved heuristic gender overrides ({} entries) -> {}", genders.size(), g.toAbsolutePath());
        }
        return out;
    }

    private List<String> fetchAll() throws IOException, InterruptedException {
        List<String> names = new ArrayList<>();
        String cmContinue = null;
        int page = 0;
        while (page < MAX_PAGES) {
            page++;
            String url = API + "?action=query&list=categorymembers&cmtitle=" + URLEncoder.encode("Category:NPCs", StandardCharsets.UTF_8) +
                    "&cmlimit=" + LIMIT + (cmContinue != null ? "&cmcontinue=" + URLEncoder.encode(cmContinue, StandardCharsets.UTF_8) : "") + "&format=json";
            JsonNode root = get(url);
            JsonNode members = root.path("query").path("categorymembers");
            if (members.isArray()) {
                for (JsonNode m : members) {
                    String title = m.path("title").asText("");
                    if (title.isEmpty()) continue;
                    // Filter out meta pages
                    if (title.startsWith("Category:")) continue;
                    if (title.startsWith("User:")) continue;
                    if (title.startsWith("Template:")) continue;
                    // Remove disambiguation parentheticals e.g. "Aubury (shopkeeper)"
                    String cleaned = title.replaceAll("\\s*\\(.*?\\)$", "").trim();
                    if (!cleaned.isEmpty() && !names.contains(cleaned)) names.add(cleaned);
                }
            }
            if (root.path("continue").has("cmcontinue")) {
                cmContinue = root.path("continue").path("cmcontinue").asText();
            } else {
                break; // done
            }
            Thread.sleep(THROTTLE_MS);
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private JsonNode get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", "OSRS-TTS-Plugin/1.0 (voice immersion)");
        conn.setConnectTimeout(10000); conn.setReadTimeout(15000);
        try (var in = conn.getInputStream()) {
            return M.readTree(in);
        }
    }

    private Map<String,String> inferGenders(List<String> names) {
        Map<String,String> out = new LinkedHashMap<>();
        Set<String> femaleTokens = Set.of("lady","sister","queen","duchess","princess","mother","nun","girl","madam","barmaid","waitress");
        Set<String> maleTokens = Set.of("sir","king","lord","duke","prince","father","brother","monk","boy","barman","bartender");
        String[] femaleSuffixes = {"a","ia","na","la","ra","ssa","elle","ette","ine","lyn","lynn","beth","anne","anna","eva","ina","ika"};
        String[] maleSuffixes = {"us","o","an","en","ar","ik","as","is","or","er","ath","son","ric","fred"};
        for (String n : names) {
            String lower = n.toLowerCase(Locale.ROOT);
            String gender = null;
            for (String t : femaleTokens) if (lower.contains(t)) { gender = "female"; break; }
            if (gender == null) for (String t : maleTokens) if (lower.contains(t)) { gender = "male"; break; }
            if (gender == null) {
                for (String s : femaleSuffixes) { if (lower.endsWith(s)) { gender = "female"; break; } }
            }
            if (gender == null) {
                for (String s : maleSuffixes) { if (lower.endsWith(s)) { gender = "male"; break; } }
            }
            if (gender != null) out.put(n, gender);
        }
        return out;
    }
}
