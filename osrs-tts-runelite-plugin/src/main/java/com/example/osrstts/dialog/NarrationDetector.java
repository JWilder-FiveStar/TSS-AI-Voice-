package com.example.osrstts.dialog;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.voice.VoiceRuntime;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.security.MessageDigest;

public class NarrationDetector {
    private String lastHash = null;

    public void maybeNarrateOpenText(Client client, OsrsTtsConfig cfg, VoiceRuntime runtime) {
        if (client == null || runtime == null || cfg == null || !cfg.isNarratorEnabled()) return;
        StringBuilder sb = new StringBuilder();
        // Conservative scan: check a common chatbox/text group (e.g., 162) if present
        // to avoid relying on getWidgets() which may not be available on this API version
        for (int groupId : new int[] {162, 548, 593, 12}) {
            Widget group = client.getWidget(groupId, 0);
            if (group == null) continue;
            collectVisibleText(group, sb);
        }
        String text = sb.toString().trim();
        if (text.length() < 200) return; // heuristic: ignore short UI labels
        String hash = sha1(text);
        if (hash.equals(lastHash)) return; // avoid repeats
        lastHash = hash;
        try {
            // Limit extremely long pages
            String toSpeak = text.length() > 2000 ? text.substring(0, 2000) : text;
            runtime.speakNarrator(toSpeak);
        } catch (Exception ignored) {}
    }

    private static String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }

    private static String sha1(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] dig = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(dig.length * 2);
            for (byte b : dig) {
                hex.append(String.format("%02x", b));
            }
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
            for (Widget c : children) {
                collectVisibleText(c, sb);
            }
        }
    }
}
