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

    public void setLastLoadedGroupId(int groupId) {
        this.lastLoadedGroupId = groupId;
    }

    public void maybeNarrateOpenText(Client client, OsrsTtsConfig cfg, VoiceRuntime runtime) {
        if (client == null || runtime == null || cfg == null) return;

        // 1) Try NPC/Player dialogue widgets first (quest/dialogue boxes)
        if (maybeSpeakDialogue(client, runtime)) {
            return;
        }

        // 2) Fallback: narrate long text pages (books, journals, scrolls)
        if (!cfg.isNarratorEnabled()) return;
        StringBuilder sb = new StringBuilder();
        // Prefer the most recently loaded widget group if any
        if (lastLoadedGroupId != null) {
            Widget last = client.getWidget(lastLoadedGroupId, 0);
            if (last != null) collectVisibleText(last, sb);
        }
        // Scan common root groups (avoid CHATBOX)
        for (int groupId : new int[] {12, 548, 593}) {
            Widget group = client.getWidget(groupId, 0);
            if (group == null) continue;
            collectVisibleText(group, sb);
        }
        String text = sb.toString().trim();
        if (text.length() < 80) return; // heuristic: journals/notes can be shorter than 200
        String hash = sha1(text);
        if (hash.equals(lastHash)) return; // avoid repeats
        lastHash = hash;
        try {
            String toSpeak = text.length() > 2400 ? text.substring(0, 2400) : text;
            if (DEBUG) log.info("TTS NarrationDetector: long-text {} chars -> narrator", toSpeak.length());
            runtime.speakNarrator(toSpeak);
        } catch (Exception ignored) {}
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
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget c : children) collectVisibleText(c, sb);
        }
    }

    private static void collectVisibleLines(Widget w, List<String> out) {
        if (w == null || w.isHidden()) return;
        String t = w.getText();
        if (t != null && !t.isEmpty()) {
            String s = stripTags(t).trim();
            if (!s.isEmpty()) out.add(s);
        }
        Widget[] children = w.getChildren();
        if (children != null) {
            for (Widget c : children) collectVisibleLines(c, out);
        }
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
