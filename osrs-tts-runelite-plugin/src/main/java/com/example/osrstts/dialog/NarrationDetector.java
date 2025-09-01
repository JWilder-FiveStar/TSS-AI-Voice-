package com.example.osrstts.dialog;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.voice.VoiceRuntime;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

import java.security.MessageDigest;
import java.util.HexFormat;

public class NarrationDetector {
    private String lastHash = null;

    public void maybeNarrateOpenText(Client client, OsrsTtsConfig cfg, VoiceRuntime runtime) {
        if (client == null || runtime == null || cfg == null || !cfg.isNarratorEnabled()) return;
        Widget[][] widgets = client.getWidgets();
        if (widgets == null) return;

        StringBuilder sb = new StringBuilder();
        for (Widget[] group : widgets) {
            if (group == null) continue;
            for (Widget w : group) {
                if (w == null) continue;
                if (w.isHidden()) continue;
                String t = w.getText();
                if (t != null && t.length() > 0) {
                    String s = stripTags(t).trim();
                    if (!s.isEmpty()) {
                        sb.append(s).append('\n');
                    }
                }
            }
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
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
