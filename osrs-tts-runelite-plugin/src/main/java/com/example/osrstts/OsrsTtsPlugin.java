package com.example.osrstts;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.config.ConfigManager;
import com.example.osrstts.voice.VoiceRuntime;
import com.example.osrstts.dialog.NarrationDetector;

@PluginDescriptor(
    name = "Old School RuneScape TTS",
    description = "Text-to-Speech plugin for Old School RuneScape",
    tags = {"tts", "text-to-speech", "osrs"}
)
public class OsrsTtsPlugin extends Plugin {
    private OsrsTtsConfig config;
    private Client client;
    private VoiceRuntime voiceRuntime;
    private final NarrationDetector narrationDetector = new NarrationDetector();

    @Override
    protected void startUp() throws Exception {
        // Initialize the TTS configuration and services
    config = getConfig(OsrsTtsConfig.class);
    voiceRuntime = new VoiceRuntime(config);
    }

    @Override
    protected void shutDown() throws Exception {
        // Clean up resources and listeners
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        // Try to narrate when long text content is open (books/journals/scrolls)
        // client may be injected by RuneLite; if available, use it here
        try {
            narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
        } catch (Exception ignored) {}
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() == ChatMessageType.NPC_CHAT) {
            try {
                String npc = event.getName();
                String msg = stripTags(event.getMessage());
                voiceRuntime.speakNpc(npc, msg, java.util.Collections.emptySet());
            } catch (Exception ignored) {}
        }
        // TODO: detect book/journal/scroll widget open and call voiceRuntime.speakNarrator(text)
    }

    private String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }
}