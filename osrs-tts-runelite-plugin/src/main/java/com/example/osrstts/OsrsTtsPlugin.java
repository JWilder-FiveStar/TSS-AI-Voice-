package com.example.osrstts;

import com.example.osrstts.dialog.NarrationDetector;
import com.example.osrstts.voice.VoiceRuntime;
import com.google.inject.Provides;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Locale;

@PluginDescriptor(
        name = "Old School RuneScape TTS",
        description = "Text-to-Speech for NPC dialogs and narration.",
        tags = {"tts", "text-to-speech", "osrs"}
)
public class OsrsTtsPlugin extends Plugin {
    private static final Logger log = LoggerFactory.getLogger(OsrsTtsPlugin.class);

    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private OsrsTtsRlConfig rlConfig;

    public OsrsTtsConfig config;            // panel access
    public VoiceRuntime voiceRuntime;       // panel access
    private NavigationButton navButton;
    private OsrsTtsConfigPanel panel;
    private final NarrationDetector narrationDetector = new NarrationDetector();
    private String activeProvider;
    // Overhead text tracking to avoid repeats
    private final java.util.Map<Integer, String> npcOverheadHash = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> playerOverheadHash = new java.util.HashMap<>();

    private static final String DEBUG_PROP = "osrs.tts.debug";

    @Provides
    OsrsTtsRlConfig provideConfig(ConfigManager cm) { return cm.getConfig(OsrsTtsRlConfig.class); }

    @Override
    protected void startUp() {
        log.info("OSRS TTS starting");
        config = new OsrsTtsConfig();
        syncConfigFromRuneLite();
        rebuildRuntime("startup");
        SwingUtilities.invokeLater(this::addSidebar);
    }

    private void addSidebar() {
        try {
            panel = new OsrsTtsConfigPanel(this, rlConfig, configManager);
            navButton = NavigationButton.builder()
                    .tooltip("OSRS TTS")
                    .icon(createIcon())
                    .priority(5)
                    .panel(panel)
                    .build();
            clientToolbar.addNavigation(navButton);
        } catch (Exception e) {
            log.error("Failed to add sidebar", e);
        }
    }

    @Override
    protected void shutDown() {
        log.info("OSRS TTS shutting down");
        if (clientToolbar != null && navButton != null) {
            try { clientToolbar.removeNavigation(navButton); } catch (Exception ignored) {}
        }
        navButton = null;
        panel = null;
        voiceRuntime = null;
    }

    private void syncConfigFromRuneLite() {
        if (rlConfig == null || config == null) return;
        config.setAzureKey(rlConfig.azureKey());
        config.setAzureRegion(getSelectedRegion());
        config.setNarratorVoice(rlConfig.narratorVoice());
        config.setNarratorEnabled(rlConfig.narratorEnabled());
        config.setProvider(rlConfig.provider().name());
        try { config.setPlayerVoice(rlConfig.playerVoice()); } catch (Throwable ignored) {}
        try { config.setElevenKey(rlConfig.elevenKey()); } catch (Throwable ignored) {}
        try { config.setElevenModel(rlConfig.elevenModel()); } catch (Throwable ignored) {}
    }

    private String getSelectedRegion() {
        try {
            String custom = rlConfig.azureRegion();
            if (custom != null && !custom.isBlank()) return custom.trim();
            return rlConfig.azureRegionSelect().name();
        } catch (Throwable t) { return "eastus"; }
    }

    private void rebuildRuntime(String reason) {
        try {
            long t0 = System.currentTimeMillis();
            voiceRuntime = new VoiceRuntime(config);
            activeProvider = config.getProvider();
            log.info("Rebuilt runtime provider={} reason={} in {}ms", activeProvider, reason, System.currentTimeMillis()-t0);
        } catch (Exception e) {
            log.error("Failed to rebuild runtime (reason={})", reason, e);
        }
    }

    private void ensureRuntimeProvider() {
        if (config == null) return;
        String desired = config.getProvider();
        if (voiceRuntime == null || activeProvider == null || !activeProvider.equalsIgnoreCase(desired)) {
            rebuildRuntime("provider-change");
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged evt) {
        if (!"osrs-tts".equals(evt.getGroup())) return;
        syncConfigFromRuneLite();
        ensureRuntimeProvider();
        if ("testVoice".equals(evt.getKey())) testVoice();
    }

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event) {
        if (voiceRuntime == null || config == null) return;
        boolean debug = isDebug();
        try {
            narrationDetector.setLastLoadedGroupId(event.getGroupId());
            narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
            int g = event.getGroupId();
            if (g == 160 || g == 193 || g == 229 ||
                g == net.runelite.api.widgets.WidgetID.COLLECTION_LOG_ID ||
                g == net.runelite.api.widgets.WidgetID.ADVENTURE_LOG_ID ||
                g == net.runelite.api.widgets.WidgetID.KILL_LOGS_GROUP_ID ||
                g == net.runelite.api.widgets.WidgetID.GENERIC_SCROLL_GROUP_ID) { // known quest / dialog / log groups
                scheduleDelayedScan(100, g, debug);
                scheduleDelayedScan(250, g, debug);
                scheduleDelayedScan(500, g, debug);
            }
        } catch (Exception e) {
            if (debug) log.debug("WidgetLoaded error: {}", e.getMessage());
        }
    }

    private void scheduleDelayedScan(int delayMs, int groupId, boolean debug) {
        final long start = System.currentTimeMillis();
        try {
            clientThread.invokeLater(() -> {
                if (System.currentTimeMillis() - start < delayMs) return false;
                try {
                    if (voiceRuntime == null) return true;
                    narrationDetector.forceNextScan();
                    narrationDetector.setLastLoadedGroupId(groupId);
                    narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
                    if (debug) log.info("Delayed narration scan {}ms groupId={}", delayMs, groupId);
                } catch (Exception ignored) {}
                return true;
            });
        } catch (Exception e) {
            if (debug) log.debug("scheduleDelayedScan failed {}ms: {}", delayMs, e.getMessage());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage evt) {
        if (voiceRuntime == null || config == null || evt == null) return;
        boolean debug = isDebug();
        try {
            ChatMessageType type = evt.getType();
            String typeName = type != null ? type.name() : "";
            String raw = evt.getMessage();
            String msg = stripTags(raw);
            if (msg.isBlank()) return;

            if ("DIALOG".equalsIgnoreCase(typeName)) {
                String[] parts = raw.split("\\|", 2);
                if (parts.length == 2) {
                    String spk = sanitizeName(parts[0].replace('_',' '));
                    String text = stripTags(parts[1]).trim();
                    if (!text.isEmpty()) {
                        String self = localPlayerName();
                        if (self != null && spk.equalsIgnoreCase(self)) voiceRuntime.speakPlayer(text);
                        else voiceRuntime.speakNpc(spk.isEmpty()?"NPC":spk, text, voiceRuntime.inferTags(spk));
                        return;
                    }
                }
            }

            String tU = typeName.toUpperCase(Locale.ROOT);
            if (tU.contains("GAME") || tU.contains("SPAM") || tU.contains("ENGINE") || tU.contains("BROADCAST")) return;
            String speaker = sanitizeName(evt.getName());
            String self = localPlayerName();
            boolean playerChan = tU.contains("PUBLIC") || tU.contains("FRIEND") || tU.contains("CLAN") || tU.contains("PRIVATE") || tU.contains("AUTOTYPER");
            boolean npcChan = tU.contains("NPC");

            if (self != null && speaker.equalsIgnoreCase(self) && playerChan) {
                voiceRuntime.speakPlayer(msg);
                return;
            }
            if (npcChan && !speaker.isBlank()) {
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
                return;
            }
            if (!speaker.isBlank() && !playerChan) {
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
            }
        } catch (Exception e) {
            if (debug) log.debug("Chat handling error: {}", e.getMessage());
        }
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (voiceRuntime == null || config == null || client == null) return;
    // Legacy master toggle still honored; then granular toggles decide which categories run
	if (!config.isOverheadEnabled()) return;
        try {
            if (config.isNpcOverheadEnabled()) {
                for (NPC npc : client.getNpcs()) {
                    if (npc == null) continue;
                    String txt = npc.getOverheadText();
                    if (txt == null || txt.isBlank()) continue;
                    String clean = stripTags(txt).trim();
                    if (clean.isEmpty()) continue;
                    int id = npc.getIndex();
                    String hash = Integer.toHexString((clean+"|"+id).hashCode());
                    String prev = npcOverheadHash.get(id);
                    if (hash.equals(prev)) continue; // same as last tick
                    npcOverheadHash.put(id, hash);
                    String name = sanitizeName(npc.getName());
                    if (name.isBlank()) name = "NPC";
                    voiceRuntime.speakNpc(name, clean, voiceRuntime.inferTags(name));
                }
            }
            if (config.isPlayerOverheadEnabled()) {
                for (Player p : client.getPlayers()) {
                    if (p == null) continue;
                    String txt = p.getOverheadText();
                    if (txt == null || txt.isBlank()) continue;
                    String clean = stripTags(txt).trim();
                    if (clean.isEmpty()) continue;
                    int id = System.identityHashCode(p); // stable for session
                    String hash = Integer.toHexString((clean+"|P|"+id).hashCode());
                    String prev = playerOverheadHash.get(id);
                    if (hash.equals(prev)) continue;
                    playerOverheadHash.put(id, hash);
                    String self = localPlayerName();
                    String name = sanitizeName(p.getName());
                    if (self != null && name.equalsIgnoreCase(self)) {
                        voiceRuntime.speakPlayer(clean);
                    } else {
                        voiceRuntime.speakNpc(name.isBlank()?"Player":name, clean, voiceRuntime.inferTags(name));
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    private String localPlayerName() {
        try { return client.getLocalPlayer() != null ? sanitizeName(client.getLocalPlayer().getName()) : null; } catch (Exception e) { return null; }
    }

    public void testVoice() {
        if (voiceRuntime == null) return;
        clientThread.invoke(() -> {
            try {
                ensureRuntimeProvider();
                voiceRuntime.speakNarrator("Hello! This is a narrator voice test for the OSRS TTS plugin.");
            } catch (Exception e) { log.warn("testVoice failed: {}", e.getMessage()); }
        });
    }

    public void testPlayerVoice() {
        if (voiceRuntime == null) return;
        clientThread.invoke(() -> {
            try {
                ensureRuntimeProvider();
                voiceRuntime.speakPlayer("This is a player voice test.");
            } catch (Exception e) { log.warn("testPlayerVoice failed: {}", e.getMessage()); }
        });
    }

    private boolean isDebug() { return "true".equalsIgnoreCase(System.getProperty(DEBUG_PROP, "false")); }
    private String stripTags(String in) { return in == null ? "" : in.replaceAll("<[^>]*>", ""); }
    private String sanitizeName(String in) { return in == null ? "" : stripTags(in).replaceAll("[^A-Za-z0-9 _-]"," ").trim(); }

    private static BufferedImage createIcon() {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(33,150,243));
            g.fillRoundRect(0,0,s,s,4,4);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Dialog", Font.BOLD, 11));
            g.drawString("T", 5,12);
        } finally { g.dispose(); }
        return img;
    }

    // Manual trigger from config panel for debugging narrator issues
    public void forceNarrationScanNow() {
        if (clientThread == null) return;
        clientThread.invoke(() -> {
            try {
                narrationDetector.forceNextScan();
                narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
            } catch (Exception e) {
                log.warn("Force narration scan failed: {}", e.getMessage());
            }
        });
    }
}

