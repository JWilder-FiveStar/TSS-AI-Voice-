package com.example.osrstts;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.events.ConfigChanged;
import com.example.osrstts.voice.VoiceRuntime;
import com.example.osrstts.dialog.NarrationDetector;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.inject.Provides;

@PluginDescriptor(
    name = "Old School RuneScape TTS",
    description = "Text-to-Speech plugin for Old School RuneScape",
    tags = {"tts", "text-to-speech", "osrs"}
)
public class OsrsTtsPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(OsrsTtsPlugin.class);
    
    @Inject private Client client;
    @Inject private OsrsTtsRlConfig rlConfig;
    @Inject private ConfigManager configManager;
    @Inject private ClientThread clientThread;
    
    public OsrsTtsConfig config;  // Made public for config panel access
    private VoiceRuntime voiceRuntime;
    private final NarrationDetector narrationDetector = new NarrationDetector();

    @Override
    protected void startUp() throws Exception {
        log.info("OSRS TTS plugin starting up...");
        try {
            // Initialize the TTS configuration and services
            config = new OsrsTtsConfig();
            voiceRuntime = new VoiceRuntime(config);
            
            // Sync RuneLite config to our internal config on startup
            syncConfigFromRuneLite();
            
            log.info("OSRS TTS plugin started successfully");
        } catch (Exception e) {
            log.error("Failed to start OSRS TTS plugin", e);
            throw e;
        }
    }

    private void syncConfigFromRuneLite() {
        if (config != null && rlConfig != null) {
            config.setAzureKey(rlConfig.azureKey());
            config.setAzureRegion(rlConfig.azureRegion());
            config.setNarratorVoice(rlConfig.narratorVoice());
            config.setNarratorEnabled(rlConfig.narratorEnabled());
            config.setProvider(rlConfig.provider().name());
            log.debug("Synced RuneLite config to internal config");
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("OSRS TTS plugin stopped");
        
        if (voiceRuntime != null) {
            // Clean up resources
            voiceRuntime = null;
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (!rlConfig.enabled() || voiceRuntime == null) {
            return;
        }
        
        // Try to narrate when long text content is open (books/journals/scrolls)
        try {
            if (rlConfig.narratorEnabled()) {
                narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
            }
        } catch (Exception e) {
            log.debug("Error in narration detection", e);
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (!rlConfig.enabled() || voiceRuntime == null) {
            return;
        }
        
        try {
            String npc = event.getName();
            String msg = stripTags(event.getMessage());
            if (npc != null && !npc.isEmpty() && msg != null && !msg.isEmpty()) {
                voiceRuntime.speakNpc(npc, msg, java.util.Collections.emptySet());
            }
        } catch (Exception e) {
            log.debug("Error processing chat message", e);
        }
    }

    public void testVoice() {
        if (voiceRuntime == null) {
            log.warn("Cannot test voice: plugin not started");
            return;
        }
        
        clientThread.invoke(() -> {
            try {
                log.info("=== TESTING TTS VOICE ===");
                
                // Sync settings from RuneLite config before testing
                String azureKey = rlConfig.azureKey();
                String azureRegion = rlConfig.azureRegion();
                String narratorVoice = rlConfig.narratorVoice();
                
                if (azureKey == null || azureKey.trim().isEmpty()) {
                    log.warn("Azure Speech Key is not configured! Please enter your key in the Azure Speech Settings section.");
                    return;
                }
                
                if (azureKey.length() < 30) {
                    log.warn("Azure Speech Key looks too short ({}). Please check your key.", azureKey.length());
                    return;
                }
                
                // Update our internal config with current UI values
                config.setAzureKey(azureKey);
                config.setAzureRegion(azureRegion);
                config.setNarratorVoice(narratorVoice);
                config.setProvider(rlConfig.provider().name());
                
                String testText = "Hello! This is a test of the Old School RuneScape TTS plugin.";
                
                log.info("Testing with Azure region: {}, voice: {}", azureRegion, narratorVoice);
                log.info("Azure key length: {} chars", azureKey.length());
                
                voiceRuntime.speakNarrator(testText);
                log.info("=== VOICE TEST COMPLETED ===");
            } catch (Exception e) {
                log.error("Voice test FAILED: {}", e.getMessage(), e);
                if (e.getMessage() != null && e.getMessage().contains("400")) {
                    log.error("Azure 400 error usually means: invalid voice name, wrong region, or bad API key");
                    log.error("Try using voice: 'en-US-JennyNeural' or 'en-US-DavisNeural'");
                }
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"osrs-tts".equals(event.getGroup())) {
            return;
        }
        
        // Sync RuneLite config to our internal config
        if (config != null) {
            config.setAzureKey(rlConfig.azureKey());
            config.setAzureRegion(rlConfig.azureRegion());
            config.setNarratorVoice(rlConfig.narratorVoice());
            config.setNarratorEnabled(rlConfig.narratorEnabled());
            config.setProvider(rlConfig.provider().name());
            log.debug("Config synced: key={}..., region={}, voice={}", 
                rlConfig.azureKey().length() > 0 ? rlConfig.azureKey().substring(0, Math.min(8, rlConfig.azureKey().length())) : "empty",
                rlConfig.azureRegion(), rlConfig.narratorVoice());
        }
        
        // Handle button click
        if ("testVoice".equals(event.getKey())) {
            log.info("Test Voice button clicked");
            testVoice();
        }
    }

    private String stripTags(String in) {
        return in == null ? "" : in.replaceAll("<[^>]*>", "");
    }

    @Provides
    OsrsTtsRlConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(OsrsTtsRlConfig.class);
    }
}