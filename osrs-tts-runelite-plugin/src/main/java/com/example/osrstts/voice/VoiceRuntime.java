package com.example.osrstts.voice;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.tts.AzureSpeechTtsClient;
import com.example.osrstts.tts.PollyTtsClient;
import com.example.osrstts.tts.TtsClient;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.util.Set;

public class VoiceRuntime {
    private final OsrsTtsConfig cfg;
    private final VoiceSelector selector;
    private final TtsClient tts;
    private final AudioCache cache;

    public VoiceRuntime(OsrsTtsConfig cfg) {
        this.cfg = cfg;
        this.selector = new VoiceSelector(cfg.getProvider(), cfg.getDefaultVoice(), cfg.getVoiceMappingFile());
        if ("Azure".equalsIgnoreCase(cfg.getProvider())) {
            this.tts = new AzureSpeechTtsClient(cfg.getAzureKey(), cfg.getAzureRegion(), cfg.getAudioOutputFormat());
        } else {
            // Prefer WAV for unified playback by switching Polly to PCM if implemented.
            this.tts = new PollyTtsClient();
        }
        this.cache = cfg.isCacheEnabled() ? new AudioCache(cfg.getCacheDir()) : null;
    }

    public void speakNpc(String npcName, String text, Set<String> tags) throws Exception {
    VoiceSelection sel = selector.select(npcName, text, tags);
    String cacheKey = cacheKey("npc", sel, text);
    byte[] audio = getOrSynthesize(cacheKey, sel, text);
    playWav(audio);
    }

    public void speakNarrator(String text) throws Exception {
        VoiceSelection sel;
        if ("Azure".equalsIgnoreCase(cfg.getProvider())) {
            sel = VoiceSelection.of(cfg.getNarratorVoice(), cfg.getNarratorStyle());
        } else {
            sel = VoiceSelection.of("Joanna", null); // Polly narrator default
        }
        String cacheKey = cacheKey("narrator", sel, text);
        byte[] audio = getOrSynthesize(cacheKey, sel, text);
        playWav(audio);
    }

    private byte[] getOrSynthesize(String key, VoiceSelection sel, String text) throws Exception {
        String ext = isWavOutput() ? "wav" : "mp3";
        if (cache != null) {
            byte[] hit = cache.get(key, ext);
            if (hit != null) return hit;
        }
        byte[] data = tts.synthesize(text, sel);
        if (cache != null) cache.put(key, ext, data);
        return data;
    }

    private boolean isWavOutput() {
        return cfg.getAudioOutputFormat().toLowerCase().contains("riff") || cfg.getAudioOutputFormat().toLowerCase().contains("pcm");
    }

    private String cacheKey(String kind, VoiceSelection sel, String text) {
        String base = cfg.getProvider() + "|" + kind + "|" + (sel.voiceName == null ? "auto" : sel.voiceName) + "|" + (sel.style == null ? "-" : sel.style);
        int hash = text.hashCode();
        return base + "|" + Integer.toHexString(hash);
    }

    // Optional tag inference helper for future use
    public java.util.Set<String> inferTags(String npcName) {
        java.util.Set<String> tags = new java.util.HashSet<>();
        if (npcName == null) return tags;
        String n = npcName.toLowerCase();
        if (n.contains("guard")) tags.add("guard");
        if (n.contains("wizard")) tags.add("wizard");
        if (n.contains("king") || n.contains("queen") || n.contains("duke")) tags.add("royalty");
        if (n.contains("vamp")) tags.add("vampire");
        if (n.contains("monk")) tags.add("monk");
        if (n.contains("pirate")) tags.add("pirate");
        if (n.contains("dwarf")) tags.add("dwarf");
        if (n.contains("goblin")) tags.add("goblin");
        return tags;
    }

    private void playWav(byte[] data) throws Exception {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data); AudioInputStream ais = AudioSystem.getAudioInputStream(bais)) {
            Clip clip = AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        }
    }
}
