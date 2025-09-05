package com.example.osrstts.dialog;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.voice.VoiceRuntime;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

public class NarrationDetector {
    private static final Logger log = LoggerFactory.getLogger(NarrationDetector.class);
    private static final boolean DEBUG = "true".equalsIgnoreCase(System.getProperty("osrs.tts.debug", "false"));
    private String lastHash = null;
    private Integer lastLoadedGroupId = null;
    // Lightweight throttle (reintroduced) to prevent excessive log spam; adjustable via system property 'osrs.tts.narrationIntervalMs'
    private long lastScanAt = 0L;
    private static final long DEFAULT_INTERVAL = 350; // ms
    private String lastScannedContent = null;
    // Incremental book/diary tracking
    private String lastBookTitle = null;
    private String lastBookBody = null; // full accumulated body already narrated
    private long lastBookUpdateAt = 0L;
    // Certain parchment style interfaces (reduced; removed 160 to avoid mass quest list narration)
    private static final int[] SPECIAL_QUEST_GROUPS = new int[] {193, 229};
    // Previously broad; now ordered with high-priority narration-rich groups first (drawn from RuneLite WidgetID)
    // Extended with additional log / journal style groups (collection log, adventure log, kill logs)
    private static final int[] SCAN_GROUPS = new int[] {
        // High priority explicit narration contexts
        WidgetID.GENERIC_SCROLL_GROUP_ID,        // books / long scrolls
        WidgetID.DIARY_QUEST_GROUP_ID,           // quest journal panel
        WidgetID.ACHIEVEMENT_DIARY_SCROLL_GROUP_ID, // achievement diary scroll detail
        WidgetID.CLUE_SCROLL_GROUP_ID,           // clue text
        WidgetID.QUEST_COMPLETED_GROUP_ID,       // quest complete scroll
        WidgetID.COLLECTION_LOG_ID,              // collection log entries (narratable flavor text)
        WidgetID.ADVENTURE_LOG_ID,               // adventure log / account summary style text
        WidgetID.KILL_LOGS_GROUP_ID,             // boss / kill logs descriptive text
        // (Quest list & diary summary intentionally excluded to avoid reading large lists)
    WidgetID.LOGIN_CLICK_TO_PLAY_GROUP_ID,   // login welcome / MOTD
        // Standard dialog / parchment groups we already cared about
        160, 193, 229,
        WidgetID.DIALOG_NPC_GROUP_ID,
        WidgetID.DIALOG_PLAYER_GROUP_ID,
        WidgetID.DIALOG_OPTION_GROUP_ID,
        // Remaining broad sweep (subset trimmed to reduce overhead)
        12, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45,
        162, 163, 164, 165, 166, 167, 168, 169, 170,
        548, 593, 230, 231, 232, 233
    };
    // cursor removed

    public void setLastLoadedGroupId(int groupId) {
        this.lastLoadedGroupId = groupId;
    }

    /**
     * Force cooldown reset so a follow-up scan (e.g., delayed after widget open)
     * can run immediately without waiting SCAN_COOLDOWN_MS. Safe because
     * duplicate suppression still handled by hash/content checks.
     */
    public void forceNextScan() { /* no-op now */ }

    public void maybeNarrateOpenText(Client client, OsrsTtsConfig cfg, VoiceRuntime runtime) {
        if (client == null || runtime == null || cfg == null) return;

    long currentTime = System.currentTimeMillis();
    long minInterval = getIntervalMs();
    if (currentTime - lastScanAt < minInterval) return; // simple throttle
    lastScanAt = currentTime;
    if (DEBUG) log.info("TTS Narration scan start (interval={}ms)", minInterval);

    // 1) Try NPC/Player dialogue widgets first (quest/dialogue boxes)
        if (maybeSpeakDialogue(client, runtime)) {
            return;
        }

        // 2) Fallback: narrate long text pages (books, journals, scrolls)
        if (!cfg.isNarratorEnabled()) return;
        
        StringBuilder sb = new StringBuilder();
        int effectiveGroupUsed = -1;
        // Prefer the most recently loaded widget group if any
        if (lastLoadedGroupId != null) {
            Widget last = client.getWidget(lastLoadedGroupId, 0);
            if (last != null) {
                collectVisibleText(last, sb);
                effectiveGroupUsed = lastLoadedGroupId;
            }
        }
        
        // Scan all groups each time (prioritized order). Stop once enough content accumulated.
        for (int groupId : SCAN_GROUPS) {
            if (groupId == WidgetID.LOGIN_CLICK_TO_PLAY_GROUP_ID && !cfg.isLoginNarrationEnabled()) continue;
            Widget group = client.getWidget(groupId, 0);
            if (group == null) continue;
            int before = sb.length();
            collectVisibleText(group, sb);
            // Fallback: if still nothing added for this group, brute-force a subset of component ids (early exit if we find something)
            if (sb.length() == before) {
                for (int comp = 1; comp < 80; comp++) { // reasonable bound; books typically small component space
                    Widget w = client.getWidget(groupId, comp);
                    if (w == null) continue;
                    collectVisibleText(w, sb);
                    if (sb.length() > before + 2) break; // got some lines
                }
            }
            if (sb.length() > before && effectiveGroupUsed == -1) effectiveGroupUsed = groupId;
            if (sb.length() > 220) break; // slightly higher cap to allow bigger pages
        }
        
        String text = sb.toString().trim();

        if (DEBUG) {
            log.info("TTS NarrationDetector: aggregate text length={} groupUsed={}", text.length(), effectiveGroupUsed);
        }
        
        // Duplicate suppression (allow very short new content within cooldown if changed)
    if (!text.isEmpty() && text.equals(lastScannedContent)) {
            if (DEBUG && text.length() > 0) log.info("TTS NarrationDetector: identical content skipped ({} chars)", text.length());
            return;
        }
        lastScannedContent = text;
        
        // Determine if this content belongs to a special quest group (allow shorter text)
        boolean isSpecialQuestGroup = false;
        for (int g : SPECIAL_QUEST_GROUPS) {
            if (g == effectiveGroupUsed) { isSpecialQuestGroup = true; break; }
        }

    // Relax min length more: parchment / clue pages can be very short
    int minLength = isSpecialQuestGroup ? 6 : 18;
    if (text.length() < minLength) {
            if (DEBUG && text.length() > 0) {
                log.info("TTS NarrationDetector: skipping short text ({} chars) group={} specialQuest={} content='{}'", text.length(), effectiveGroupUsed, isSpecialQuestGroup, text);
            }
            return;
        }
        
    boolean narrateAll = "true".equalsIgnoreCase(System.getProperty("osrs.tts.narrateAll", "false"));
        // Treat high-priority groups as narration automatically (even if heuristic fails)
        boolean forceByGroup = false;
    if (!narrateAll && !isSpecialQuestGroup && effectiveGroupUsed != -1) {
        for (int gid : new int[]{
            WidgetID.GENERIC_SCROLL_GROUP_ID,
            WidgetID.DIARY_QUEST_GROUP_ID,
            WidgetID.ACHIEVEMENT_DIARY_SCROLL_GROUP_ID,
            WidgetID.CLUE_SCROLL_GROUP_ID,
            WidgetID.QUEST_COMPLETED_GROUP_ID,
            WidgetID.COLLECTION_LOG_ID,
            WidgetID.ADVENTURE_LOG_ID,
            WidgetID.KILL_LOGS_GROUP_ID
        }) { if (gid == effectiveGroupUsed) { forceByGroup = true; break; } }
    }
        // Heuristic skip: detect list-like or obvious UI panels to avoid mass reading
    if (!narrateAll && (isListLike(text) || isNoisePanel(text, effectiveGroupUsed))) {
            if (DEBUG) log.info("TTS NarrationDetector: noise/list content suppressed (group={} chars={} lines={})", effectiveGroupUsed, text.length(), text.split("\\n").length);
            return;
        }

        // Enhanced filtering for narration content (or forced overrides / group forcing)
        if (narrateAll || forceByGroup || looksLikeNarrationContent(text) || isSpecialQuestGroup) {
            String hash = sha1(text);
            if (hash.equals(lastHash)) {
                if (DEBUG) log.info("TTS NarrationDetector: hash repeat suppressed");
                return; // avoid repeats
            }
            lastHash = hash;
            try {
                String toSpeak = text.length() > 2400 ? text.substring(0, 2400) : text;

                // Optional preface: "<Player> opens the <Title>. It reads: ..." for book / diary style content
                if (cfg.isDiaryPrefaceEnabled()) {
                    try {
                        String[] lines = toSpeak.split("\n+");
                        if (lines.length > 1) {
                            String title = lines[0].trim();
                            if (looksLikeTitle(title)) {
                                // Skip duplicate title if appears again later
                                java.util.List<String> bodyLines = new java.util.ArrayList<>();
                                for (int i = 1; i < lines.length; i++) {
                                    String ln = lines[i].trim();
                                    if (ln.isEmpty()) continue;
                                    bodyLines.add(ln);
                                }
                                if (Boolean.parseBoolean(System.getProperty("osrs.tts.filterPageNumbers", "true"))) {
                                    bodyLines = removePageNumberNoise(bodyLines);
                                }
                                String body = String.join("\n", bodyLines).trim();
                                if (!body.isEmpty()) {
                                    // Incremental logic: if same title and body extends last body, only speak delta
                                    boolean sameTitle = title.equalsIgnoreCase(lastBookTitle != null ? lastBookTitle : "");
                                    boolean extended = false;
                                    String delta = null;
                                    if (sameTitle && lastBookBody != null && body.length() > lastBookBody.length() && body.startsWith(lastBookBody)) {
                                        delta = body.substring(lastBookBody.length()).trim();
                                        // Avoid trivial delta (e.g., leftover newline)
                                        if (delta.length() < 4) delta = null; else extended = true;
                                    }
                                    if (extended && delta != null) {
                                        // Speak only the newly revealed page content (no preface repetition)
                                        toSpeak = delta;
                                        lastBookBody = body; // update accumulated body
                                        lastBookUpdateAt = System.currentTimeMillis();
                                    } else {
                                        // New book or non-extension content
                                        String playerName = safePlayerName(client);
                                        String article = title.toLowerCase().matches("^(a |an |the ).*") ? "" : (needsAn(title) ? "an " : "the ");
                                        String prefix = playerName + " opens " + article + title + ". It reads: ";
                                        toSpeak = prefix + body;
                                        lastBookTitle = title;
                                        lastBookBody = body;
                                        lastBookUpdateAt = System.currentTimeMillis();
                                    }
                                    // Book context expiry: if no update for 90s, reset to allow fresh preface later
                                    if (lastBookUpdateAt > 0 && System.currentTimeMillis() - lastBookUpdateAt > 90_000) {
                                        lastBookTitle = null; lastBookBody = null; lastBookUpdateAt = 0L;
                                    }
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (DEBUG) {
                    log.info("TTS NarrationDetector: NARRATION DETECTED group={} specialQuest={} forceByGroup={} - len={} lines={} preview='{}'",
                        effectiveGroupUsed, isSpecialQuestGroup, forceByGroup, toSpeak.length(), toSpeak.split("\\n").length, toSpeak.substring(0, Math.min(100, toSpeak.length())));
                    if (Boolean.parseBoolean(System.getProperty("osrs.tts.logFullNarration", "true"))) {
                        // Log full content in chunks of 800 chars to avoid truncation
                        String full = toSpeak;
                        int idx = 0; int chunk = 800; int part = 1;
                        while (idx < full.length()) {
                            int end = Math.min(full.length(), idx + chunk);
                            log.info("TTS NarrationDetector: FULL[{}] {}..{} -> {}", part++, idx, end, full.substring(idx, end));
                            idx = end;
                        }
                    }
                }
                runtime.speakNarrator(toSpeak);
            } catch (Exception ignored) {}
    } else if (DEBUG && text.length() >= 20) {
            log.info("TTS NarrationDetector: text found but not narration-worthy: '{}' (length: {})", 
                text.substring(0, Math.min(50, text.length())), text.length());
        }
    }

    /**
     * Enhanced detection for narration-worthy content like books, journals, notes, clue scrolls
     */
    private static boolean looksLikeNarrationContent(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        
        String lower = text.toLowerCase();
        
        // Positive indicators for narration content
        boolean hasNarrationKeywords = lower.contains("journal") || lower.contains("diary") || 
                                      lower.contains("book") || lower.contains("note") || 
                                      lower.contains("scroll") || lower.contains("clue") ||
                                      lower.contains("letter") || lower.contains("document") ||
                                      lower.contains("manuscript") || lower.contains("tome") ||
                                      lower.contains("record") || lower.contains("log") ||
                                      lower.contains("page") || lower.contains("chapter");
        
        // Characteristic patterns of readable content
        boolean hasReadableContent = text.contains(".") || text.contains("!") || text.contains("?") ||
                                   text.contains(":") || text.contains(";") ||
                                   text.split("\\s+").length >= 5; // at least 5 words
        
        // Story-like content patterns
        boolean hasStoryContent = lower.contains("the ") || lower.contains("and ") || 
                                lower.contains("you ") || lower.contains("i ") ||
                                lower.contains("he ") || lower.contains("she ") ||
                                lower.contains("it ") || lower.contains("they ");
        
        // Avoid UI elements and short labels
        boolean avoidUIElements = !lower.matches("^(ok|cancel|yes|no|close|back|next|continue|accept|decline)$") &&
                                !lower.matches("^[0-9]+$") && // pure numbers
                                !lower.matches("^[0-9]+\\s*(gp|coins?)$") && // money amounts
                                text.length() >= 10; // minimum reasonable content length
        
        return avoidUIElements && (hasNarrationKeywords || (hasReadableContent && hasStoryContent));
    }

    private static boolean isListLike(String text) {
    if (text == null || text.isEmpty()) return false;
        String[] lines = text.split("\n+");
        if (lines.length < 8) return false; // need enough lines to consider list
        int shortLines = 0;
        int punct = 0;
        int singleWord = 0;
        for (String l : lines) {
            String s = l.trim();
            if (s.isEmpty()) continue;
            if (s.length() <= 24) shortLines++;
            if (!s.contains(" ")) singleWord++;
            if (s.matches(".*[.!?].*")) punct++;
        }
        int effective = 0; for (String l : lines) if (!l.trim().isEmpty()) effective++;
        if (effective == 0) return false;
        double shortRatio = shortLines / (double) effective;
        double singleWordRatio = singleWord / (double) effective;
        double punctRatio = punct / (double) effective;
        // list-like: mostly short, low punctuation, many single-word or short titles
        return shortRatio > 0.7 && punctRatio < 0.15 && singleWordRatio > 0.3;
    }

    private static boolean isNoisePanel(String text, int groupId) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase();
        // Skip quest list panels or filter/chat control clusters
        if (lower.contains("quest list") && text.split("\n").length > 30) return true;
        // UI filter words cluster (chat channel toggles)
        String[] uiWords = {"game", "public", "private", "clan", "trade"};
        int hits = 0; for (String w : uiWords) if (lower.contains("\n"+w+"\n")) hits++;
        if (hits >= 3 && text.length() < 1200) return true;
        // Large numeric-dense block
        String[] lines = text.split("\n+");
        int numeric = 0; int total = 0;
        for (String l : lines) {
            String s = l.trim(); if (s.isEmpty()) continue; total++;
            if (s.matches("[0-9]+")) numeric++;
        }
        if (total > 20 && numeric > (total * 0.5)) return true;
        return false;
    }

    private static java.util.List<String> removePageNumberNoise(java.util.List<String> lines) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String l : lines) {
            String s = l.trim();
            if (s.isEmpty()) continue;
            // Standalone integer 1-400 assumed page or index
            if (s.matches("[0-9]{1,3}") && Integer.parseInt(s) <= 400) continue;
            // Roman numerals up to XX (page markers)
            if (s.matches("(?i)^(i|ii|iii|iv|v|vi|vii|viii|ix|x|xi|xii|xiii|xiv|xv|xvi|xvii|xviii|xix|xx)$")) continue;
            // Contents / index style short headings may be noise only if followed by a number next; we'll keep simple for now
            out.add(s);
        }
        // If stripping left very few lines, fall back to original to avoid losing content
        if (out.size() < 2 && lines.size() > 2) return lines;
        return out;
    }

    private static boolean looksLikeTitle(String s) {
        if (s == null) return false;
        String t = s.trim();
        if (t.isEmpty()) return false;
        if (t.length() > 40) return false;
        // Title case heuristic: many capital starts or contains apostrophe (e.g., Witches' Diary)
        int caps = 0; for (String p : t.split(" ")) if (!p.isEmpty() && Character.isUpperCase(p.charAt(0))) caps++;
        boolean apostrophe = t.contains("'");
        return caps >= 1 && (apostrophe || caps >= Math.max(1, t.split(" ").length / 2));
    }

    private static boolean needsAn(String title) {
        if (title == null || title.isEmpty()) return false;
        char c = Character.toLowerCase(title.charAt(0));
        return "aeiou".indexOf(c) >= 0;
    }

    private static String safePlayerName(Client client) {
        try {
            if (client == null || client.getLocalPlayer() == null) return "The player";
            String n = client.getLocalPlayer().getName();
            if (n == null || n.isBlank()) return "The player";
            return n.replaceAll("<[^>]*>", "");
        } catch (Exception e) {
            return "The player";
        }
    }

    private long getIntervalMs() {
        try {
            String prop = System.getProperty("osrs.tts.narrationIntervalMs");
            if (prop != null && !prop.isBlank()) {
                long v = Long.parseLong(prop.trim());
                if (v < 50) return 50; // safety floor
                if (v > 2000) return 2000; // safety cap
                return v;
            }
        } catch (Exception ignored) {}
        return DEFAULT_INTERVAL;
    }

    private boolean maybeSpeakDialogue(Client client, VoiceRuntime runtime) {
        int[] groups;
        try {
            groups = new int[] {
                    WidgetID.DIALOG_NPC_GROUP_ID,
                    WidgetID.DIALOG_PLAYER_GROUP_ID,
                    WidgetID.DIALOG_OPTION_GROUP_ID,
                    WidgetID.CHATBOX_GROUP_ID
            };
        } catch (Throwable t) {
            groups = new int[] {231, 217, 219, 162};
        }

        for (int groupId : groups) {
            Widget root = client.getWidget(groupId, 0);
            if (root == null || root.isHidden()) continue;
            List<String> lines = new ArrayList<>();
            collectVisibleLines(root, lines);
            if (lines.isEmpty()) continue;
            String joined = String.join("\n", lines).trim();
            if (joined.isEmpty()) continue;

            String hash = sha1(groupId + "|" + joined);
            if (hash.equals(lastHash)) return true;

            try {
                if (groupId == safeDialogPlayerGroup()) {
                    String body = extractBody(lines);
                    if (DEBUG) log.info("TTS NarrationDetector: player dialog {} chars", body.length());
                    runtime.speakPlayer(body);
                } else if (groupId == safeDialogNpcGroup()) {
                    String speaker = extractSpeaker(lines);
                    String body = extractBody(lines);
                    if (DEBUG) log.info("TTS NarrationDetector: npc dialog speaker='{}' {} chars", speaker, body.length());
                    java.util.Set<String> tags = runtime.inferTags(speaker != null ? speaker : "NPC");
                    runtime.speakNpc(speaker != null ? speaker : "NPC", body, tags);
                } else if (groupId == safeDialogOptionGroup()) {
                    String prompt = extractPrompt(lines);
                    if (prompt != null && !prompt.isEmpty()) {
                        if (DEBUG) log.info("TTS NarrationDetector: option prompt {} chars", prompt.length());
                        runtime.speakNarrator(prompt);
                    }
                } else {
                    continue;
                }
                lastHash = hash;
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static int safeDialogNpcGroup() {
        try { return WidgetID.DIALOG_NPC_GROUP_ID; } catch (Throwable t) { return 231; }
    }

    private static int safeDialogPlayerGroup() {
        try { return WidgetID.DIALOG_PLAYER_GROUP_ID; } catch (Throwable t) { return 217; }
    }

    private static int safeDialogOptionGroup() {
        try { return WidgetID.DIALOG_OPTION_GROUP_ID; } catch (Throwable t) { return 219; }
    }

    private static String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(dig.length * 2);
            for (byte b : dig) { hex.append(String.format("%02x", b)); }
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static void collectVisibleText(Widget w, StringBuilder sb) {
        if (w == null || w.isHidden()) return;
        String t = w.getText();
        if (t != null && !t.isEmpty()) {
            String s = stripTags(t).trim();
            if (!s.isEmpty()) sb.append(s).append('\n');
        }
        // Traverse all child arrays: standard, static, dynamic.
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget c : children) collectVisibleText(c, sb);
        }
        try {
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) for (Widget c : dyn) collectVisibleText(c, sb);
        } catch (Throwable ignored) {}
        try {
            Widget[] stat = w.getStaticChildren();
            if (stat != null) for (Widget c : stat) collectVisibleText(c, sb);
        } catch (Throwable ignored) {}
    }

    private static void collectVisibleLines(Widget w, List<String> out) {
        if (w == null || w.isHidden()) return;
        String t = w.getText();
        if (t != null && !t.isEmpty()) {
            String s = stripTags(t).trim();
            if (!s.isEmpty()) out.add(s);
        }
        Widget[] children = w.getChildren();
        if (children != null) for (Widget c : children) collectVisibleLines(c, out);
        try {
            Widget[] dyn = w.getDynamicChildren();
            if (dyn != null) for (Widget c : dyn) collectVisibleLines(c, out);
        } catch (Throwable ignored) {}
        try {
            Widget[] stat = w.getStaticChildren();
            if (stat != null) for (Widget c : stat) collectVisibleLines(c, out);
        } catch (Throwable ignored) {}
    }

    private static String extractSpeaker(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        String first = lines.get(0).trim();
        if (first.length() <= 24 && !first.matches(".*[.!?:].*")) return first;
        return null;
    }

    private static String extractBody(List<String> lines) {
        if (lines == null || lines.isEmpty()) return "";
        String speaker = extractSpeaker(lines);
        List<String> slice = lines;
        if (speaker != null && lines.size() >= 2) slice = lines.subList(1, lines.size());
        return String.join("\n", slice).trim();
    }

    private static String extractPrompt(List<String> lines) {
        // Find the first non-empty line that does not look like an enumerated option (e.g., "1.", "2.")
        for (String l : lines) {
            String s = l.trim();
            if (s.isEmpty()) continue;
            if (s.matches("^[0-9]+[).].*") || s.matches("^[→>-].*") || s.matches("^Option \\d+:.*")) {
                continue;
            }
            // Avoid pure speaker line
            if (s.length() <= 24 && !s.matches(".*[.!?:].*")) continue;
            return s;
        }
        return null;
    }
}
