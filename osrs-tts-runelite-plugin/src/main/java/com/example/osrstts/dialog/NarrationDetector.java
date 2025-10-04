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

    // Add callback interface for dialog completion detection
    public interface DialogCompletionCallback {
        boolean onDialogFound(String speaker, String text);
    }

    private DialogCompletionCallback dialogCallback = null;

    // Add setter for the callback
    public void setDialogCompletionCallback(DialogCompletionCallback callback) {
        this.dialogCallback = callback;
    }

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
        WidgetID.LOGIN_CLICK_TO_PLAY_GROUP_ID    // login welcome / MOTD (optional)
        // Note: do NOT include broad UI container groups (12, 34-45, 162-170, 548, 593, 230-233)
        // Dialog groups are handled by maybeSpeakDialogue() and should not be swept here.
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
    public void forceNextScan() {
        // Reset throttle so next maybeNarrateOpenText() will scan immediately
        this.lastScanAt = 0L;
    }

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

        // If a dialog/cutscene group was the last to load, avoid narrating generic page text in this cycle
        if (lastLoadedGroupId != null && isDialogGroupId(lastLoadedGroupId)) {
            if (DEBUG) log.info("TTS NarrationDetector: dialog group active ({}), skipping narration fallback this cycle", lastLoadedGroupId);
            return;
        }

        // 2) Fallback: narrate long text pages (books, journals, scrolls)
        if (!cfg.isNarratorEnabled()) return;
        
        StringBuilder sb = new StringBuilder();
        int effectiveGroupUsed = -1;
        // Prefer the most recently loaded widget group only if it is a narrative group
        if (lastLoadedGroupId != null && isNarrativeGroup(lastLoadedGroupId)) {
            Widget last = client.getWidget(lastLoadedGroupId, 0);
            if (last != null) {
                collectVisibleText(last, sb);
                effectiveGroupUsed = lastLoadedGroupId;
            }
        }
        
        // Scan all narrative groups each time (prioritized order). Stop once enough content accumulated.
        for (int groupId : SCAN_GROUPS) {
            if (groupId == WidgetID.LOGIN_CLICK_TO_PLAY_GROUP_ID && !cfg.isLoginNarrationEnabled()) continue;
            // Skip chat window - group 270 contains chat history which should be handled by chat message events
            if (groupId == 270) continue;
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
            if (sb.length() > 10000) break; // increased to 10000 to handle very long books/scrolls
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
        if (!narrateAll && (isListLike(text) || isNoisePanel(text, effectiveGroupUsed) || isChatLike(text))) {
            if (DEBUG) log.info("TTS NarrationDetector: noise/list/chat content suppressed (group={} chars={} lines={})", effectiveGroupUsed, text.length(), text.split("\\n").length);
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
                // Start with a baseline toSpeak; diary preface may override below
                String toSpeak = text.length() > 4000 ? text.substring(0, 4000) : text;

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
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }

                if (DEBUG) log.info("TTS NarrationDetector: speaking {} chars", toSpeak.length());
                runtime.speakNarrator(toSpeak);
            } catch (Exception e) {
                if (DEBUG) log.error("NarrationDetector speaking error: {}", e.getMessage());
            }
        }
    }

    private static boolean isNarrativeGroup(int groupId) {
        for (int g : SCAN_GROUPS) if (g == groupId) return true;
        return false;
    }

    private static boolean isDialogGroupId(int groupId) {
        try {
            return groupId == net.runelite.api.widgets.WidgetID.DIALOG_NPC_GROUP_ID
                || groupId == net.runelite.api.widgets.WidgetID.DIALOG_PLAYER_GROUP_ID
                || groupId == net.runelite.api.widgets.WidgetID.DIALOG_OPTION_GROUP_ID
                || groupId == net.runelite.api.widgets.WidgetID.CHATBOX_GROUP_ID;
        } catch (Throwable t) {
            return groupId == 231 || groupId == 217 || groupId == 219 || groupId == 162;
        }
    }

    private static boolean looksLikeNarrationContent(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String clean = text.trim();
        // Basic heuristics for narrative content vs UI lists
        if (clean.length() < 20) return false;
        String[] lines = clean.split("\n+");
        if (lines.length == 1) return clean.length() > 30; // single long line is probably narrative
        // Multi-line: check for sentence structure vs list structure
        int sentences = 0;
        int shortLines = 0;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.length() < 15) shortLines++;
            if (l.matches(".*[.!?]\\s*$")) sentences++;
        }
        double sentenceRatio = sentences / (double) lines.length;
        double shortRatio = shortLines / (double) lines.length;
        return sentenceRatio > 0.3 && shortRatio < 0.7; // more sentences, fewer short lines
    }

    private static boolean isListLike(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        String[] lines = text.split("\n+");
        if (lines.length < 5) return false; // need multiple lines to be list-like
        int shortLines = 0;
        int singleWord = 0;
        int punct = 0;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            if (l.length() < 20) shortLines++;
            if (l.split("\\s+").length == 1) singleWord++;
            if (l.matches(".*[.!?]\\s*$")) punct++;
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

        // Explicit chat window group (should be handled by chat events, not narration)
        if (groupId == 270) return true;

        // Skip quest list panels or filter/chat control clusters
        if (lower.contains("quest list") && text.split("\n").length > 30) return true;

        // UI filter words cluster (chat channel toggles)
        String[] uiWords = {"game", "public", "private", "clan", "trade"};
        int hits = 0; for (String w : uiWords) if (lower.contains("\n"+w+"\n")) hits++;
        if (hits >= 3 && text.length() < 1200) return true;

        // Chat-like content patterns (repetitive game messages)
        String[] lines = text.split("\n+");
        if (lines.length > 10) {
            int catchMessages = 0;
            int broadcastMessages = 0;
            int attemptMessages = 0;
            for (String line : lines) {
                String l = line.trim().toLowerCase();
                if (l.startsWith("you catch") || l.startsWith("you attempt")) attemptMessages++;
                if (l.contains("broadcast:")) broadcastMessages++;
                if (l.matches("\\d{2}:\\d{2}:\\d{2}.*")) catchMessages++; // timestamp pattern
            }
            // If mostly repetitive game actions or chat timestamps, treat as noise
            if (attemptMessages > lines.length * 0.3 || catchMessages > 3) return true;
        }

        // Large numeric-dense block
        int numeric = 0; int total = 0;
        for (String l : lines) {
            String s = l.trim(); if (s.isEmpty()) continue; total++;
            if (s.matches("[0-9]+")) numeric++;
        }
        if (total > 20 && numeric > (total * 0.5)) return true;
        return false;
    }

    // Suppress regular chat/game logs: many second-person short lines like "You ..."
    private static boolean isChatLike(String text) {
        if (text == null || text.isEmpty()) return false;
        String[] lines = text.split("\n+");
        int effective = 0, youStarts = 0, shortLines = 0;
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            effective++;
            if (l.length() < 90) shortLines++;
            String lc = l.toLowerCase();
            if (lc.startsWith("you ") || lc.startsWith("you\u00A0") || lc.startsWith("you\t") || lc.startsWith("your ") || lc.startsWith("you'")) {
                youStarts++;
            }
        }
        if (effective < 4) return false;
        double youRatio = youStarts / (double) effective;
        double shortRatio = shortLines / (double) effective;
        // Treat as chat-like if most lines are short and many start with "You ..."
        return shortRatio > 0.7 && youRatio > 0.4;
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
                return Math.max(100, Math.min(5000, v)); // reasonable bounds
            }
        } catch (Exception ignored) {}
        return DEFAULT_INTERVAL;
    }

    private boolean maybeSpeakDialogue(Client client, VoiceRuntime runtime) {
        int[] groups;
        try {
            groups = new int[] {
                    net.runelite.api.widgets.WidgetID.DIALOG_NPC_GROUP_ID,
                    net.runelite.api.widgets.WidgetID.DIALOG_PLAYER_GROUP_ID,
                    net.runelite.api.widgets.WidgetID.DIALOG_OPTION_GROUP_ID,
                    net.runelite.api.widgets.WidgetID.CHATBOX_GROUP_ID
            };
        } catch (Throwable t) {
            groups = new int[] {231, 217, 219, 162};
        }

        for (int groupId : groups) {
            net.runelite.api.widgets.Widget root = client.getWidget(groupId, 0);
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
                    String speaker = "Player";
                    if (DEBUG) log.info("TTS NarrationDetector: player dialog {} chars", body.length());
                    // Ask plugin if we should speak
                    if (dialogCallback != null) {
                        try {
                            if (!dialogCallback.onDialogFound(speaker, body)) {
                                if (DEBUG) log.info("NarrationDetector: dialog suppressed by callback (player)");
                                lastHash = hash;
                                return true;
                            }
                        } catch (Exception ignored) {}
                    }
                    runtime.speakPlayer(body);
                } else if (groupId == safeDialogNpcGroup()) {
                    String speaker = extractSpeaker(lines);
                    String body = extractBody(lines);
                    if (speaker == null || speaker.isBlank()) speaker = "NPC";
                    if (DEBUG) log.info("TTS NarrationDetector: npc dialog speaker='{}' {} chars", speaker, body.length());
                    // Ask plugin if we should speak
                    if (dialogCallback != null) {
                        try {
                            if (!dialogCallback.onDialogFound(speaker, body)) {
                                if (DEBUG) log.info("NarrationDetector: dialog suppressed by callback (npc)");
                                lastHash = hash;
                                return true;
                            }
                        } catch (Exception ignored) {}
                    }
                    java.util.Set<String> tags = runtime.inferTags(speaker);
                    runtime.speakNpc(speaker, body, tags);
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
        try { return net.runelite.api.widgets.WidgetID.DIALOG_NPC_GROUP_ID; } catch (Throwable t) { return 231; }
    }

    private static int safeDialogPlayerGroup() {
        try { return net.runelite.api.widgets.WidgetID.DIALOG_PLAYER_GROUP_ID; } catch (Throwable t) { return 217; }
    }

    private static int safeDialogOptionGroup() {
        try { return net.runelite.api.widgets.WidgetID.DIALOG_OPTION_GROUP_ID; } catch (Throwable t) { return 219; }
    }

    private static String stripTags(String in) {
        if (in == null) return "";
        String s = in;
        try {
            s = s.replace('\u00A0', ' '); // nbsp to space
            s = s.replaceAll("(?i)<br\\s*/?>", "\n"); // preserve visual line breaks
            s = s.replaceAll("<[^>]*>", ""); // remove remaining tags
            // Normalize whitespace but keep newlines for structure
            s = s.replaceAll("[\\t\\x0B\\f\\r]", " ");
            // Collapse multiple spaces
            s = s.replaceAll(" {2,}", " ");
            // Trim each line but keep line boundaries
            String[] lines = s.split("\n");
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String ln = lines[i].trim();
                if (!ln.isEmpty()) {
                    out.append(ln);
                    if (i < lines.length - 1) out.append('\n');
                }
            }
            s = out.toString();
        } catch (Exception ignored) {}
        return s;
    }

    private static String sha1(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(dig.length * 2);
            for (byte b : dig) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private static void collectVisibleText(net.runelite.api.widgets.Widget w, StringBuilder sb) {
        if (w == null || w.isHidden()) return;
        String t = w.getText();
        if (t != null && !t.isEmpty()) {
            String s = stripTags(t).trim();
            if (!s.isEmpty()) {
                // Split multi-line into separate lines to preserve spacing when joined later
                String[] parts = s.split("\n+");
                for (String p : parts) {
                    String pp = p.trim();
                    if (!pp.isEmpty()) sb.append(pp).append('\n');
                }
            }
        }
        // Traverse all child arrays: standard, static, dynamic.
        net.runelite.api.widgets.Widget[] children = w.getChildren();
        if (children != null) {
            for (net.runelite.api.widgets.Widget c : children) collectVisibleText(c, sb);
        }
        net.runelite.api.widgets.Widget[] staticChildren = w.getStaticChildren();
        if (staticChildren != null) {
            for (net.runelite.api.widgets.Widget c : staticChildren) collectVisibleText(c, sb);
        }
        net.runelite.api.widgets.Widget[] dynamicChildren = w.getDynamicChildren();
        if (dynamicChildren != null) {
            for (net.runelite.api.widgets.Widget c : dynamicChildren) collectVisibleText(c, sb);
        }
    }

    private static void collectVisibleLines(net.runelite.api.widgets.Widget w, List<String> lines) {
        if (w == null || w.isHidden()) return;
        String t = w.getText();
        if (t != null && !t.isEmpty()) {
            String s = stripTags(t).trim();
            if (!s.isEmpty()) {
                String[] parts = s.split("\n+");
                for (String p : parts) {
                    String pp = p.trim();
                    if (!pp.isEmpty()) lines.add(pp);
                }
            }
        }
        net.runelite.api.widgets.Widget[] children = w.getChildren();
        if (children != null) {
            for (net.runelite.api.widgets.Widget c : children) collectVisibleLines(c, lines);
        }
        net.runelite.api.widgets.Widget[] staticChildren = w.getStaticChildren();
        if (staticChildren != null) {
            for (net.runelite.api.widgets.Widget c : staticChildren) collectVisibleLines(c, lines);
        }
        net.runelite.api.widgets.Widget[] dynamicChildren = w.getDynamicChildren();
        if (dynamicChildren != null) {
            for (net.runelite.api.widgets.Widget c : dynamicChildren) collectVisibleLines(c, lines);
        }
    }

    private static String extractSpeaker(List<String> lines) {
        if (lines == null || lines.isEmpty()) return null;
        String first = lines.get(0).trim();
        // Heuristic: first line is the speaker if it’s short and not a sentence
        if (!first.isEmpty()) {
            boolean endsWithPunct = first.endsWith(".") || first.endsWith("!") || first.endsWith("?");
            if (first.length() <= 32 && !endsWithPunct && !first.equalsIgnoreCase("click here to continue")) {
                return first;
            }
        }
        // Fallback: if second line exists and first line looks like a title, still use first
        if (lines.size() > 1 && first.length() <= 40) return first;
        return null;
    }

    private static String extractBody(List<String> lines) {
        if (lines.isEmpty()) return "";
        // Determine speaker using the same heuristic and skip that line from the body
        String candidateSpeaker = extractSpeaker(lines);
        int start = 0;
        if (candidateSpeaker != null) {
            // If the first line equals the detected speaker, skip it
            if (!lines.isEmpty() && lines.get(0).trim().equalsIgnoreCase(candidateSpeaker.trim())) {
                start = 1;
            }
        } else {
            // Fallback: if the first line is short and not a sentence, treat as speaker
            String first = lines.get(0).trim();
            boolean endsWithPunct = first.endsWith(".") || first.endsWith("!") || first.endsWith("?");
            if (!first.isEmpty() && first.length() <= 32 && !endsWithPunct && !first.equalsIgnoreCase("click here to continue")) {
                start = 1;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.isEmpty() && !line.equalsIgnoreCase("click here to continue")) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(line);
            }
        }
        return sb.toString().trim();
    }

    private static String extractPrompt(List<String> lines) {
        return extractBody(lines);
    }
}
