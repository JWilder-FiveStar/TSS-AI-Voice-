package com.example.osrstts;

import net.runelite.api.Client;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
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
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.api.ChatMessageType;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

@PluginDescriptor(
    name = "Old School RuneScape TTS",
    description = "Text-to-Speech plugin for Old School RuneScape",
    tags = {"tts", "text-to-speech", "osrs"}
)
public class OsrsTtsPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(OsrsTtsPlugin.class);
    private static final AtomicBoolean SIDEBAR_REGISTERED = new AtomicBoolean(false);

    @Inject private Client client;
    @Inject private OsrsTtsRlConfig rlConfig;
    @Inject private ConfigManager configManager;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;

    public OsrsTtsConfig config;  // Made public for config panel access
    private VoiceRuntime voiceRuntime;
    private final NarrationDetector narrationDetector = new NarrationDetector();

    private NavigationButton navButton;
    private OsrsTtsConfigPanel panel;

    private boolean thisInstanceRegistered = false;
    private String activeProvider = null;

    @Override
    protected void startUp() throws Exception {
        log.info("OSRS TTS plugin starting up...");
        try {
            // Initialize the TTS configuration and services
            config = new OsrsTtsConfig();
            // Sync RuneLite config to our internal config on startup
            syncConfigFromRuneLite();
            // Build runtime for the synced provider
            rebuildRuntime("startup");
            // Add sidebar panel on EDT and avoid duplicates across instances
            SwingUtilities.invokeLater(() -> {
                try {
                    if (!SIDEBAR_REGISTERED.compareAndSet(false, true)) {
                        log.warn("OSRS TTS sidebar already registered; skipping duplicate panel");
                        return;
                    }
                    if (navButton != null && clientToolbar != null) {
                        clientToolbar.removeNavigation(navButton);
                        navButton = null;
                    }
                    panel = new OsrsTtsConfigPanel(this, rlConfig, configManager);
                    navButton = NavigationButton.builder()
                            .tooltip("OSRS TTS")
                            .priority(5)
                            .icon(createIcon())
                            .panel(panel)
                            .build();
                    clientToolbar.addNavigation(navButton);
                    thisInstanceRegistered = true;
                    log.info("OSRS TTS navigation panel added to sidebar");
                } catch (Exception uiEx) {
                    // If we failed, allow another attempt in future
                    SIDEBAR_REGISTERED.set(false);
                    log.error("Failed to add OSRS TTS sidebar panel", uiEx);
                }
            });

            log.info("OSRS TTS plugin started successfully");
        } catch (Exception e) {
            log.error("Failed to start OSRS TTS plugin", e);
            throw e;
        }
    }

    private void rebuildRuntime(String reason) {
        try {
            voiceRuntime = new com.example.osrstts.voice.VoiceRuntime(config);
            activeProvider = config.getProvider();
            log.info("Rebuilt TTS runtime for provider={} (reason={})", activeProvider, reason);
        } catch (Exception e) {
            log.error("Failed to rebuild TTS runtime (reason={})", reason, e);
        }
    }

    private void ensureRuntimeProvider() {
        String desired = config != null ? config.getProvider() : null;
        if (desired == null) return;
        if (activeProvider == null || !activeProvider.equalsIgnoreCase(desired) || voiceRuntime == null) {
            rebuildRuntime("provider-change");
        }
    }

    private void syncConfigFromRuneLite() {
        if (config != null && rlConfig != null) {
            String region = getSelectedRegion();
            config.setAzureKey(rlConfig.azureKey());
            config.setAzureRegion(region);
            config.setNarratorVoice(rlConfig.narratorVoice());
            config.setNarratorEnabled(rlConfig.narratorEnabled());
            config.setProvider(rlConfig.provider().name());
            try { config.setPlayerVoice(rlConfig.playerVoice()); } catch (Throwable ignored) {}
            // ElevenLabs
            try { config.setElevenKey(rlConfig.elevenKey()); } catch (Throwable ignored) {}
            try { config.setElevenModel(rlConfig.elevenModel()); } catch (Throwable ignored) {}
            log.debug("Synced RuneLite config to internal config (provider={}, region={})", rlConfig.provider(), region);
        }
    }

    private String getSelectedRegion() {
        try {
            // Prefer enum dropdown if present; fallback to free-text field
            String selected = rlConfig.azureRegionSelect().name();
            String custom = rlConfig.azureRegion();
            return (custom != null && !custom.isBlank()) ? custom.trim() : selected;
        } catch (Throwable t) {
            // In case enum isn't available on older config, fallback safely
            String custom = rlConfig.azureRegion();
            return custom != null && !custom.isBlank() ? custom.trim() : "eastus";
        }
    }

    @Override
    protected void shutDown() throws Exception {
        log.info("OSRS TTS plugin stopped");
        
        if (thisInstanceRegistered && navButton != null && clientToolbar != null) {
            clientToolbar.removeNavigation(navButton);
            navButton = null;
            panel = null;
            SIDEBAR_REGISTERED.set(false);
            thisInstanceRegistered = false;
        }

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
        if (!rlConfig.enabled() || voiceRuntime == null || event == null) return;
        try {
            String debugProp = System.getProperty("osrs.tts.debug", "false");
            boolean debug = "true".equalsIgnoreCase(debugProp);

            ChatMessageType type = event.getType();
            String typeName = type != null ? type.name() : "";
            String msgRaw = event.getMessage();
            String msg = stripTags(msgRaw);
            String rawName = event.getName();
            String speaker = sanitizeName(rawName);

            if (debug) log.info("TTS Chat evt type={}, provider={}, speaker='{}'", typeName, config != null ? config.getProvider() : "?", speaker);
            if (msg == null || msg.isEmpty()) return;

            String typeUpper = typeName.toUpperCase(Locale.ROOT);
            // Fast path: RuneLite DIALOG type encodes as "Speaker|Text"
            if ("DIALOG".equals(typeUpper)) {
                String[] parts = msgRaw != null ? msgRaw.split("\\|", 2) : new String[0];
                if (parts.length == 2) {
                    String spk = sanitizeName(parts[0].replace('_', ' '));
                    String text = stripTags(parts[1]).trim();
                    if (!text.isEmpty()) {
                        String self = client.getLocalPlayer() != null ? sanitizeName(client.getLocalPlayer().getName()) : null;
                        if (spk != null && !spk.isEmpty() && self != null && spk.equalsIgnoreCase(self)) {
                            if (debug) log.info("TTS speakPlayer (DIALOG) '{}': {}", spk, text);
                            voiceRuntime.speakPlayer(text);
                        } else {
                            if (debug) log.info("TTS speakNpc (DIALOG) '{}': {}", spk, text);
                            voiceRuntime.speakNpc(spk != null && !spk.isEmpty() ? spk : "NPC", text, voiceRuntime.inferTags(spk));
                        }
                        return;
                    }
                }
                // If parsing failed, fall through to generic handling below
            }

            // Optionally narrate onboarding/tips
            if ("WELCOME".equals(typeUpper) || "DIDYOUKNOW".equals(typeUpper)) {
                if (debug) log.info("TTS narrator ({}): {}", typeUpper, msg);
                voiceRuntime.speakNarrator(msg);
                return;
            }

            // Skip engine/system spam-like categories
            if (typeUpper.contains("GAME") || typeUpper.contains("SPAM") || typeUpper.contains("ENGINE") || typeUpper.contains("BROADCAST")) {
                return;
            }

            // Determine self name sanitized
            String self = client.getLocalPlayer() != null ? sanitizeName(client.getLocalPlayer().getName()) : null;
            boolean isSelf = self != null && !self.isEmpty() && self.equalsIgnoreCase(speaker);
            boolean playerChannel = typeUpper.contains("PUBLIC") || typeUpper.contains("FRIEND") || typeUpper.contains("CLAN") || typeUpper.contains("PRIVATE") || typeUpper.contains("AUTOTYPER");
            boolean npcChannel = typeUpper.contains("NPC");

            if (isSelf && playerChannel) {
                if (debug) log.info("TTS speakPlayer '{}': {}", self, msg);
                voiceRuntime.speakPlayer(msg);
                return;
            }

            if (npcChannel && speaker != null && !speaker.isEmpty()) {
                if (debug) log.info("TTS speakNpc '{}': {}", speaker, msg);
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
                return;
            }

            // Fallback: if it looks like dialog (has a speaker and not a pure player channel), try NPC path
            if (speaker != null && !speaker.isEmpty() && !playerChannel) {
                if (debug) log.info("TTS speakNpc (fallback) '{}': {}", speaker, msg);
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
            }
        } catch (Exception e) {
            log.warn("TTS error processing chat: {}", e.getMessage(), e);
        }
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (voiceRuntime == null) return;
        boolean debug = "true".equalsIgnoreCase(System.getProperty("osrs.tts.debug", "false"));
        if (debug) {
            log.info("TTS WidgetLoaded groupId={}", event.getGroupId());
        }
        try {
            narrationDetector.setLastLoadedGroupId(event.getGroupId());
            narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
        } catch (Exception e) {
            if (debug) log.info("TTS WidgetLoaded scan error: {}", e.getMessage());
        }
    }

    private String sanitizeName(String in) {
        if (in == null) return "";
        String s = stripTags(in).trim();
        // Remove common special chars and icons; keep letters, digits, space, underscore, dash
        return s.replaceAll("[^A-Za-z0-9 _-]", "").trim();
    }

    public void testVoice() {
        if (voiceRuntime == null) {
            log.warn("Cannot test voice: plugin not started");
            return;
        }
        clientThread.invoke(() -> {
            try {
                log.info("=== TESTING TTS VOICE ===");
                String provider = rlConfig.provider().name();
                config.setProvider(provider);
                ensureRuntimeProvider();

                if ("ElevenLabs".equalsIgnoreCase(provider)) {
                    String key = rlConfig.elevenKey();
                    if (key == null || key.trim().isEmpty()) {
                        log.warn("ElevenLabs API key is not configured.");
                        return;
                    }
                    config.setElevenKey(key);
                    config.setElevenModel(rlConfig.elevenModel());
                    config.setNarratorVoice(rlConfig.narratorVoice());
                    String testText = "Hello! This is an ElevenLabs test for the OSRS TTS plugin.";
                    log.info("Testing with ElevenLabs model: {}, voice: {}", config.getElevenModel(), config.getNarratorVoice());
                    voiceRuntime.speakNarrator(testText);
                } else {
                    String azureKey = rlConfig.azureKey().trim();
                    String azureRegion = getSelectedRegion();
                    String narratorVoice = rlConfig.narratorVoice();
                    if (azureKey == null || azureKey.isEmpty()) {
                        log.warn("Azure Speech Key is not configured!");
                        return;
                    }
                    if (azureRegion == null || azureRegion.isEmpty()) {
                        log.warn("Azure region is not set!");
                        return;
                    }
                    config.setAzureKey(azureKey);
                    config.setAzureRegion(azureRegion);
                    config.setNarratorVoice(narratorVoice);
                    String testText = "Hello! This is a test of the Old School RuneScape TTS plugin.";
                    log.info("Testing with Azure region: {}, voice: {}", azureRegion, narratorVoice);
                    voiceRuntime.speakNarrator(testText);
                }
                log.info("=== VOICE TEST COMPLETED ===");
            } catch (Exception e) {
                log.error("Voice test FAILED: {}", e.getMessage(), e);
            }
        });
    }

    public void testPlayerVoice() {
        if (voiceRuntime == null) {
            log.warn("Cannot test player voice: plugin not started");
            return;
        }
        clientThread.invoke(() -> {
            try {
                String provider = rlConfig.provider().name();
                config.setProvider(provider);
                ensureRuntimeProvider();
                if ("ElevenLabs".equalsIgnoreCase(provider)) {
                    String key = rlConfig.elevenKey();
                    String playerVoice = rlConfig.playerVoice();
                    if (key == null || key.trim().isEmpty()) {
                        log.warn("ElevenLabs API key is not configured.");
                        return;
                    }
                    config.setElevenKey(key);
                    config.setElevenModel(rlConfig.elevenModel());
                    if (playerVoice != null && !playerVoice.isEmpty()) config.setPlayerVoice(playerVoice);
                    String test = "This is my player voice test.";
                    log.info("Testing Player Voice (11Labs): {}", config.getPlayerVoice());
                    voiceRuntime.speakPlayer(test);
                } else {
                    String azureKey = rlConfig.azureKey().trim();
                    String azureRegion = getSelectedRegion();
                    String playerVoice;
                    try { playerVoice = rlConfig.playerVoice(); } catch (Throwable t) { playerVoice = config.getPlayerVoice(); }
                    if (azureKey == null || azureKey.isEmpty()) {
                        log.warn("Azure Speech Key is not configured!");
                        return;
                    }
                    if (azureRegion == null || azureRegion.isEmpty()) {
                        log.warn("Azure region is not set!");
                        return;
                    }
                    config.setAzureKey(azureKey);
                    config.setAzureRegion(azureRegion);
                    if (playerVoice != null && !playerVoice.isEmpty()) config.setPlayerVoice(playerVoice);
                    String test = "This is my player voice test.";
                    log.info("Testing Player Voice: {} (region: {})", config.getPlayerVoice(), azureRegion);
                    voiceRuntime.speakPlayer(test);
                }
            } catch (Exception e) {
                log.error("Player voice test FAILED: {}", e.getMessage(), e);
            }
        });
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!"osrs-tts".equals(event.getGroup())) {
            return;
        }
        if (config != null) {
            String before = config.getProvider();
            config.setAzureKey(rlConfig.azureKey());
            config.setAzureRegion(getSelectedRegion());
            config.setNarratorVoice(rlConfig.narratorVoice());
            config.setNarratorEnabled(rlConfig.narratorEnabled());
            config.setProvider(rlConfig.provider().name());
            try { config.setPlayerVoice(rlConfig.playerVoice()); } catch (Throwable ignored) {}
            try { config.setElevenKey(rlConfig.elevenKey()); } catch (Throwable ignored) {}
            try { config.setElevenModel(rlConfig.elevenModel()); } catch (Throwable ignored) {}
            log.debug("Config synced: provider={}, region={}, narrator={} player={}", rlConfig.provider(), getSelectedRegion(), rlConfig.narratorVoice(), rlConfig.playerVoice());
            // Rebuild runtime if provider changed
            String after = config.getProvider();
            if (activeProvider == null || !after.equalsIgnoreCase(activeProvider)) {
                rebuildRuntime("config-change");
            }
        }
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

    private static BufferedImage createIcon() {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(33, 150, 243));
            g.fillRoundRect(0, 0, s, s, 4, 4);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Dialog", Font.BOLD, 11));
            g.drawString("T", 5, 12);
        } finally {
            g.dispose();
        }
        return img;
    }
}
