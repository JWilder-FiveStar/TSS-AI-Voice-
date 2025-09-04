package com.example.osrstts;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

// Temporary replacement for the broken OsrsTtsPlugin class to get the project compiling again.
// Once stable, original advanced logic can be reintroduced here (see backup file for reference).
@PluginDescriptor(
        name = "OSRS TTS (Restored)",
        description = "Restored minimal TTS plugin after corruption; pending full feature reintegration.",
        tags = {"tts", "voice"}
)
public class OsrsTtsMain extends Plugin {
    public OsrsTtsConfig config;
    public com.example.osrstts.voice.VoiceRuntime voiceRuntime;

    @Override
    protected void startUp() {
        // Initialize minimal config + runtime
        this.config = new OsrsTtsConfig();
        this.voiceRuntime = new com.example.osrstts.voice.VoiceRuntime(config);
    }

    @Override
    protected void shutDown() {
        // Minimal shutdown
    }

    // Placeholder test hooks (were formerly in OsrsTtsPlugin)
    public void testVoice() { /* NOOP until full logic restored */ }
    public void testPlayerVoice() { /* NOOP until full logic restored */ }
}
