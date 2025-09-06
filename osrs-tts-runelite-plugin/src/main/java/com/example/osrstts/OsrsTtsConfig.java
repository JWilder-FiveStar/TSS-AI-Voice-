package com.example.osrstts;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.builder.FileBasedConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;

public class OsrsTtsConfig {
    private static final String CONFIG_FILE = "osrs-tts-config.properties";

    private final FileBasedConfigurationBuilder<PropertiesConfiguration> builder;
    private final PropertiesConfiguration config;

    public OsrsTtsConfig() {
        Parameters params = new Parameters();
        File configFile = new File(CONFIG_FILE);
        
        // Create config file if it doesn't exist
        if (!configFile.exists()) {
            try {
                configFile.createNewFile();
            } catch (Exception e) {
                // Continue - Commons Config will handle this
            }
        }
        
        builder = new FileBasedConfigurationBuilder<>(PropertiesConfiguration.class)
                .configure(params.properties().setFile(configFile));
        
        try {
            config = builder.getConfiguration();
            ensureDefaults();
        } catch (ConfigurationException e) {
            throw new RuntimeException("Failed to load config", e);
        }
    }

    private void ensureDefaults() {
        // Provider and strategy
    setIfMissing("tts.provider", "ElevenLabs");           // ElevenLabs | Azure | Polly
        setIfMissing("tts.api", getProvider());                // backward-compat shadow
        setIfMissing("tts.voice.strategy", "intelligent");    // intelligent | single | npc-mapped
        setIfMissing("tts.voice.default", "auto");            // provider voice or "auto"
        setIfMissing("tts.voice.mappingFile", "osrs-voices.json");
    setIfMissing("tts.selection.randomPerTag", "true");

        // Narrator mode for books/journals/scrolls
        setIfMissing("tts.narrator.enabled", "true");
        setIfMissing("tts.narrator.voice", "en-US-JennyNeural");
        setIfMissing("tts.narrator.style", "narration-professional");

        // Player (self) voice
        setIfMissing("tts.player.voice", "en-US-DavisNeural");
        // NPC category defaults
        setIfMissing("tts.npc.male.voice", "en-US-GuyNeural");
        setIfMissing("tts.npc.female.voice", "en-US-JennyNeural");
        setIfMissing("tts.npc.kid.voice", "en-US-JennyNeural");

        // Playback
        setIfMissing("tts.autoPlay", "true");
        setIfMissing("tts.audio.format", "riff-24000hz-16bit-mono-pcm"); // WAV easy playback

        // Caching
    setIfMissing("tts.cache.enabled", "true");
    setIfMissing("tts.cache.dir", "cache/osrs-tts");
    setIfMissing("tts.playback.volume", "80"); // percent 0-100

        // Azure defaults (key via env preferred)
        setIfMissing("azure.region", "eastus");
        // ElevenLabs defaults
        setIfMissing("eleven.key", "");
        setIfMissing("eleven.model", "eleven_turbo_v2_5");

    // Feature toggles
    // Legacy combined overhead toggle (kept for backward compat) – new granular toggles below
    setIfMissing("tts.feature.overhead.enabled", "true");
    // New granular overhead toggles
    setIfMissing("tts.feature.overhead.npc.enabled", "true");
    setIfMissing("tts.feature.overhead.player.enabled", "false");
    setIfMissing("tts.feature.diary.preface", "true");
    setIfMissing("tts.feature.login.narration", "false");

        // AWS defaults (optional)
        setIfMissing("aws.region", "us-east-1");

        saveQuietly();
    }

    private void setIfMissing(String key, String value) {
        if (!config.containsKey(key)) {
            config.setProperty(key, value);
        }
    }

    private void saveQuietly() {
        try {
            builder.save();
        } catch (ConfigurationException ignored) {
        }
    }

    // Provider
    public String getProvider() {
        return config.getString("tts.provider", config.getString("tts.api", "Azure"));
    }

    public void setProvider(String provider) {
        config.setProperty("tts.provider", provider);
        config.setProperty("tts.api", provider); // keep legacy in sync
        saveQuietly();
    }

    // Backward-compat API
    public String getTtsApi() {
        return getProvider();
    }

    public void setTtsApi(String api) {
        setProvider(api);
    }

    // Strategy and voice selection
    public String getVoiceStrategy() {
        return config.getString("tts.voice.strategy", "intelligent");
    }

    public void setVoiceStrategy(String strategy) {
        config.setProperty("tts.voice.strategy", strategy);
        saveQuietly();
    }

    public String getDefaultVoice() {
        return config.getString("tts.voice.default", "auto");
    }

    public void setDefaultVoice(String voice) {
        config.setProperty("tts.voice.default", voice);
        saveQuietly();
    }

    public String getVoiceMappingFile() {
        return config.getString("tts.voice.mappingFile", "osrs-voices.json");
    }

    public void setVoiceMappingFile(String path) {
        config.setProperty("tts.voice.mappingFile", path);
        saveQuietly();
    }

    // Narrator settings (books/journals/scrolls)
    public boolean isNarratorEnabled() {
        return config.getBoolean("tts.narrator.enabled", true);
    }

    public void setNarratorEnabled(boolean enabled) {
        config.setProperty("tts.narrator.enabled", enabled);
        saveQuietly();
    }

    public String getNarratorVoice() {
        return config.getString("tts.narrator.voice", "en-US-JennyNeural");
    }

    public void setNarratorVoice(String voice) {
        config.setProperty("tts.narrator.voice", voice);
        saveQuietly();
    }

    public String getNarratorStyle() {
        return config.getString("tts.narrator.style", "narration-professional");
    }

    public void setNarratorStyle(String style) {
        config.setProperty("tts.narrator.style", style);
        saveQuietly();
    }

    // Player voice
    public String getPlayerVoice() {
        return config.getString("tts.player.voice", "en-US-DavisNeural");
    }

    public void setPlayerVoice(String voice) {
        config.setProperty("tts.player.voice", voice);
        saveQuietly();
    }

    // NPC category voices
    public String getNpcMaleVoice() { return config.getString("tts.npc.male.voice", "en-US-GuyNeural"); }
    public void setNpcMaleVoice(String v) { config.setProperty("tts.npc.male.voice", v); saveQuietly(); }
    public String getNpcFemaleVoice() { return config.getString("tts.npc.female.voice", "en-US-JennyNeural"); }
    public void setNpcFemaleVoice(String v) { config.setProperty("tts.npc.female.voice", v); saveQuietly(); }
    public String getNpcKidVoice() { return config.getString("tts.npc.kid.voice", "en-US-JennyNeural"); }
    public void setNpcKidVoice(String v) { config.setProperty("tts.npc.kid.voice", v); saveQuietly(); }

    // Playback and audio format
    public boolean isAutoPlay() {
        return config.getBoolean("tts.autoPlay", true);
    }

    public void setAutoPlay(boolean autoPlay) {
        config.setProperty("tts.autoPlay", autoPlay);
        saveQuietly();
    }

    public String getAudioOutputFormat() {
        return config.getString("tts.audio.format", "riff-24000hz-16bit-mono-pcm");
    }

    public void setAudioOutputFormat(String format) {
        config.setProperty("tts.audio.format", format);
        saveQuietly();
    }

    // Caching
    public boolean isCacheEnabled() {
        return config.getBoolean("tts.cache.enabled", true);
    }

    public void setCacheEnabled(boolean enabled) {
        config.setProperty("tts.cache.enabled", enabled);
        saveQuietly();
    }

    public String getCacheDir() {
    return config.getString("tts.cache.dir", "cache/osrs-tts");
    }

    public void setCacheDir(String dir) {
        config.setProperty("tts.cache.dir", dir);
        saveQuietly();
    }

    // Random per-tag initial selection
    public boolean isRandomPerTag() { return config.getBoolean("tts.selection.randomPerTag", true); }
    public void setRandomPerTag(boolean v) { config.setProperty("tts.selection.randomPerTag", v); saveQuietly(); }

    // Volume
    public int getVolumePercent() {
        return config.getInt("tts.playback.volume", 80);
    }
    public void setVolumePercent(int v) {
        int vv = Math.max(0, Math.min(100, v));
        config.setProperty("tts.playback.volume", vv);
        saveQuietly();
    }

    // Azure credentials (prefer environment variables)
    public String getAzureKey() {
        String env = System.getenv("AZURE_SPEECH_KEY");
        return env != null && !env.isBlank() ? env : config.getString("azure.key", "");
    }

    public void setAzureKey(String key) {
        config.setProperty("azure.key", key);
        saveQuietly();
    }

    public String getAzureRegion() {
        String env = System.getenv("AZURE_SPEECH_REGION");
        return env != null && !env.isBlank() ? env : config.getString("azure.region", "eastus");
    }

    public void setAzureRegion(String region) {
        config.setProperty("azure.region", region);
        saveQuietly();
    }

    // Feature toggles
    public boolean isOverheadEnabled() { return config.getBoolean("tts.feature.overhead.enabled", true); }
    public void setOverheadEnabled(boolean v) { config.setProperty("tts.feature.overhead.enabled", v); saveQuietly(); }
    // New granular overhead toggles (npc & player)
    public boolean isNpcOverheadEnabled() { return config.getBoolean("tts.feature.overhead.npc.enabled", true); }
    public void setNpcOverheadEnabled(boolean v) { config.setProperty("tts.feature.overhead.npc.enabled", v); saveQuietly(); }
    public boolean isPlayerOverheadEnabled() { return config.getBoolean("tts.feature.overhead.player.enabled", false); }
    public void setPlayerOverheadEnabled(boolean v) { config.setProperty("tts.feature.overhead.player.enabled", v); saveQuietly(); }
    public boolean isDiaryPrefaceEnabled() { return config.getBoolean("tts.feature.diary.preface", true); }
    public void setDiaryPrefaceEnabled(boolean v) { config.setProperty("tts.feature.diary.preface", v); saveQuietly(); }
    public boolean isLoginNarrationEnabled() { return config.getBoolean("tts.feature.login.narration", false); }
    public void setLoginNarrationEnabled(boolean v) { config.setProperty("tts.feature.login.narration", v); saveQuietly(); }

    // ElevenLabs
    public String getElevenKey() {
        String env = System.getenv("ELEVEN_API_KEY");
        return env != null && !env.isBlank() ? env : config.getString("eleven.key", "");
    }
    public void setElevenKey(String key) { config.setProperty("eleven.key", key); saveQuietly(); }
    public String getElevenModel() { return config.getString("eleven.model", "eleven_turbo_v2_5"); }
    public void setElevenModel(String model) { config.setProperty("eleven.model", model); saveQuietly(); }

    // AWS (optional fallback)
    public String getAwsRegion() {
        String env = System.getenv("AWS_REGION");
        return env != null && !env.isBlank() ? env : config.getString("aws.region", "us-east-1");
    }
}