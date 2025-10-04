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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    // Dialog continuation tracking
    private String lastIncompleteDialog = null;
    private String lastDialogSpeaker = null;
    private long lastDialogTime = 0;
    private static final long DIALOG_CONTINUATION_TIMEOUT_MS = 4500; // was 2700ms, allow more time to finish

    // Track when complete dialog has been spoken to prevent duplicates
    private volatile boolean completeDialogSpoken = false;
    private String lastCompleteDialogKey = null;

    // Add timeout cancellation mechanism
    private volatile boolean cancelIncompleteTimeout = false;
    private String incompleteDialogKey = null;

    // Grace window to accept widget completion after timeout fired
    private String lastTimedOutDialogText = null;
    private String lastTimedOutSpeaker = null;
    private long lastTimedOutAtMs = 0L;
    private static final long TIMEOUT_COMPLETION_GRACE_MS = 6000; // was 3500ms

    // Background scheduler for non-blocking delays
    private ScheduledExecutorService ttsScheduler;

    // Track recent dialog widget activity to gate chat/narration during cutscenes
    private volatile long lastDialogWidgetAtMs = 0L;

    private static final String DEBUG_PROP = "osrs.tts.debug";

    @Provides
    OsrsTtsRlConfig provideConfig(ConfigManager cm) { return cm.getConfig(OsrsTtsRlConfig.class); }

    @Override
    protected void startUp() {
        log.info("OSRS TTS starting");
        config = new OsrsTtsConfig();
        syncConfigFromRuneLite();
        rebuildRuntime("startup");

        // Set up dialog completion/suppression callback for NarrationDetector
        narrationDetector.setDialogCompletionCallback(this::onNarrationDialogFoundCallback);

        // Single-threaded scheduler for delayed tasks (non-blocking)
        ttsScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "osrs-tts-scheduler");
            t.setDaemon(true);
            return t;
        });

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
        if (ttsScheduler != null) {
            try { ttsScheduler.shutdownNow(); } catch (Exception ignored) {}
            ttsScheduler = null;
        }
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

        // FULL DEBUG LOGGING for widget events
        if (debug) {
            log.info("=== WIDGET LOADED DEBUG ===");
            log.info("GroupId: {}", event.getGroupId());
            log.info("Widget group loaded - checking for quest dialogs");
        }

        try {
            narrationDetector.setLastLoadedGroupId(event.getGroupId());

            // Stamp dialog activity when dialog groups appear
            int gId = event.getGroupId();
            boolean isDialogGroup = false;
            try {
                isDialogGroup = gId == net.runelite.api.widgets.WidgetID.DIALOG_NPC_GROUP_ID
                        || gId == net.runelite.api.widgets.WidgetID.DIALOG_PLAYER_GROUP_ID
                        || gId == net.runelite.api.widgets.WidgetID.DIALOG_OPTION_GROUP_ID
                        || gId == net.runelite.api.widgets.WidgetID.CHATBOX_GROUP_ID;
            } catch (Throwable t) {
                isDialogGroup = gId == 231 || gId == 217 || gId == 219 || gId == 162;
            }
            if (isDialogGroup) {
                lastDialogWidgetAtMs = System.currentTimeMillis();
            }

            // Check if we have an incomplete dialog pending and this widget might contain the full text
            if (lastIncompleteDialog != null && debug) {
                log.info("CHECKING WIDGET for incomplete dialog completion - Speaker: '{}' , Incomplete: '{}'",
                    lastDialogSpeaker, lastIncompleteDialog);
            }

            narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
            int g = event.getGroupId();

            if (debug) {
                log.info("Checking widget group {} against known quest/dialog groups", g);
            }

            // Enhanced quest/dialog group detection - add more widget groups
            if (g == 160 || g == 193 || g == 229 || g == 217 || g == 231 ||  // Add the groups we're seeing
                g == net.runelite.api.widgets.WidgetID.COLLECTION_LOG_ID ||
                g == net.runelite.api.widgets.WidgetID.ADVENTURE_LOG_ID ||
                g == net.runelite.api.widgets.WidgetID.KILL_LOGS_GROUP_ID ||
                g == net.runelite.api.widgets.WidgetID.GENERIC_SCROLL_GROUP_ID) { // known quest / dialog / log groups

                if (debug) {
                    log.info("Widget group {} matches quest/dialog pattern - scheduling delayed scans", g);
                }

                scheduleDelayedScan(100, g, debug);
                scheduleDelayedScan(250, g, debug);
                scheduleDelayedScan(500, g, debug);
                scheduleDelayedScan(800, g, debug);   // extra scans to catch late-populating text
                scheduleDelayedScan(1200, g, debug);

                // If we have incomplete dialog, try to extract full text from widget immediately
                if (lastIncompleteDialog != null) {
                    scheduleDialogCompletionCheck(50, debug);
                    scheduleDialogCompletionCheck(400, debug);
                }
            } else if (debug) {
                log.info("Widget group {} does not match quest/dialog pattern - no delayed scans", g);
            }
        } catch (Exception e) {
            if (debug) log.error("WidgetLoaded error: {}", e.getMessage(), e);
        }

        if (debug) log.info("=== END WIDGET LOADED DEBUG ===");
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

    private void scheduleDialogCompletionCheck(int delayMs, boolean debug) {
        final long start = System.currentTimeMillis();
        try {
            clientThread.invokeLater(() -> {
                if (System.currentTimeMillis() - start < delayMs) return false;
                try {
                    if (lastIncompleteDialog != null && voiceRuntime != null) {
                        if (debug) {
                            log.info("DIALOG COMPLETION CHECK - Attempting to extract full text from widgets");
                        }

                        // Try to force narration detection to get full text from current widgets
                        narrationDetector.forceNextScan();

                        // The NarrationDetector might have the full text - let's check if it would speak something
                        // that contains our incomplete dialog as a substring
                        if (debug) {
                            log.info("DIALOG COMPLETION CHECK - Forced narration scan completed");
                        }
                    }
                } catch (Exception e) {
                    if (debug) log.error("Dialog completion check failed: {}", e.getMessage());
                }
                return true;
            });
        } catch (Exception e) {
            if (debug) log.debug("scheduleDialogCompletionCheck failed {}ms: {}", delayMs, e.getMessage());
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage evt) {
        if (voiceRuntime == null || config == null || evt == null) return;
        boolean debug = isDebug();

        // ENHANCED DEBUG LOGGING - Log all incoming chat messages with length info
        if (debug) {
            log.info("=== CHAT MESSAGE DEBUG ===");
            log.info("Type: {}", evt.getType());
            log.info("Name/Speaker: '{}'", evt.getName());
            log.info("Raw Message: '{}'", evt.getMessage());
            log.info("Raw Message Length: {}", evt.getMessage() != null ? evt.getMessage().length() : 0);
            log.info("Timestamp: {}", evt.getTimestamp());

            // Check for message fragmentation indicators
            String raw = evt.getMessage();
            if (raw != null) {
                boolean endsAbruptly = !raw.endsWith(".") && !raw.endsWith("!") && !raw.endsWith("?") && !raw.endsWith("...") && raw.length() > 20;
                log.info("Message appears incomplete (no proper ending): {}", endsAbruptly);
                if (raw.contains("|")) {
                    String[] parts = raw.split("\\|", -1); // -1 to include empty strings
                    log.info("Message parts count: {}", parts.length);
                    for (int i = 0; i < parts.length; i++) {
                        log.info("Part {}: '{}'", i, parts[i]);
                    }
                }
            }
        }

        try {
            ChatMessageType type = evt.getType();
            String typeName = type != null ? type.name() : "";
            String raw = evt.getMessage();
            String msg = stripTags(raw);

            if (debug) {
                log.info("Processed message after tag stripping: '{}'", msg);
                log.info("Processed message length: {}", msg.length());
            }

            if (msg.isBlank()) {
                if (debug) log.info("Message blank after stripping tags - skipping");
                return;
            }

            if ("DIALOG".equalsIgnoreCase(typeName)) {
                if (debug) log.info("Processing DIALOG message");
                String[] parts = raw.split("\\|", 2);
                if (parts.length == 2) {
                    String spk = sanitizeName(parts[0].replace('_',' '));
                    String text = stripTags(parts[1]).trim();
                    long currentTime = System.currentTimeMillis();

                    if (debug) {
                        log.info("DIALOG - Speaker: '{}', Text: '{}'", spk, text);
                        log.info("DIALOG - Text Length: {}, Ends with punctuation: {}",
                            text.length(),
                            text.endsWith(".") || text.endsWith("!") || text.endsWith("?") || text.endsWith("..."));

                        // Check for common truncation patterns
                        if (text.length() > 0 && !text.matches(".*[.!?]\\s*$") && text.length() > 50) {
                            log.warn("POTENTIAL TRUNCATION DETECTED - Dialog text may be incomplete!");
                        }
                    }

                    // Check if this might be a continuation of a previous incomplete dialog
                    boolean isIncomplete = text.length() > 50 && !text.matches(".*[.!?]\\s*$");
                    boolean isContinuation = lastIncompleteDialog != null &&
                                           spk.equalsIgnoreCase(lastDialogSpeaker) &&
                                           (currentTime - lastDialogTime) < DIALOG_CONTINUATION_TIMEOUT_MS;

                    if (debug) {
                        log.info("DIALOG ANALYSIS - IsIncomplete: {}, IsContinuation: {}", isIncomplete, isContinuation);
                        if (lastIncompleteDialog != null) {
                            log.info("PREVIOUS INCOMPLETE - Speaker: '{}', Time diff: {}ms",
                                lastDialogSpeaker, currentTime - lastDialogTime);
                        }
                    }

                    String finalText = text;

                    if (isContinuation) {
                        // This appears to be a continuation of the previous dialog
                        finalText = lastIncompleteDialog + " " + text;
                        if (debug) {
                            log.info("CONCATENATED DIALOG - Combined text: '{}'", finalText);
                            log.info("CONCATENATED DIALOG - Combined length: {}", finalText.length());
                        }
                        // Clear the incomplete dialog since we're processing it
                        lastIncompleteDialog = null;
                        lastDialogSpeaker = null;
                        lastDialogTime = 0;
                    }

                    if (isIncomplete && !isContinuation) {
                        // This dialog appears incomplete, store it for potential continuation
                        lastIncompleteDialog = text;
                        lastDialogSpeaker = spk;
                        lastDialogTime = currentTime;
                        incompleteDialogKey = spk + "|" + text.hashCode();
                        cancelIncompleteTimeout = false;

                        if (debug) {
                            log.info("STORING INCOMPLETE DIALOG for potential continuation - Key: {}", incompleteDialogKey);
                        }

                        // Don't speak yet - wait for potential continuation
                        // Schedule a delayed task (off the client thread) to speak it if no continuation comes
                        if (ttsScheduler != null) {
                            final String pendingSpeaker = spk;
                            final String pendingText = text;
                            ttsScheduler.schedule(() -> {
                                // Post back to the client thread for safe client/widget access
                                clientThread.invoke(() -> {
                                    try {
                                        // Try a final widget scan just before speaking
                                        try {
                                            narrationDetector.forceNextScan();
                                            narrationDetector.maybeNarrateOpenText(client, config, voiceRuntime);
                                        } catch (Exception ignored) {}

                                        // Check if still pending and not cancelled
                                        if (!cancelIncompleteTimeout &&
                                            lastIncompleteDialog != null &&
                                            lastIncompleteDialog.equals(pendingText) &&
                                            pendingSpeaker.equalsIgnoreCase(lastDialogSpeaker)) {

                                            if (debug) {
                                                log.info("TIMEOUT - Speaking incomplete dialog as-is: '{}'", pendingText);
                                            }

                                            String self = localPlayerName();
                                            if (self != null && pendingSpeaker.equalsIgnoreCase(self)) {
                                                voiceRuntime.speakPlayer(pendingText);
                                            } else {
                                                voiceRuntime.speakNpc(pendingSpeaker.isEmpty()?"NPC":pendingSpeaker, pendingText, voiceRuntime.inferTags(pendingSpeaker));
                                            }

                                            // Record timed-out dialog for grace completion by widget
                                            lastTimedOutDialogText = pendingText;
                                            lastTimedOutSpeaker = pendingSpeaker;
                                            lastTimedOutAtMs = System.currentTimeMillis();

                                            // Clear the stored dialog
                                            lastIncompleteDialog = null;
                                            lastDialogSpeaker = null;
                                            lastDialogTime = 0;
                                            incompleteDialogKey = null;
                                        } else if (debug) {
                                            log.info("TIMEOUT CANCELLED - Dialog completion found via widget scan");
                                        }
                                    } catch (Exception e) {
                                        if (debug) log.error("Error in delayed dialog processing: {}", e.getMessage());
                                    }
                                });
                            }, DIALOG_CONTINUATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        }

                        return; // Don't process this dialog immediately
                    }

                    if (!finalText.isEmpty()) {
                        // Create dialog key for duplicate prevention
                        String dialogKey = spk + "|" + finalText.hashCode();

                        // Check if we already spoke this complete dialog
                        if (dialogKey.equals(lastCompleteDialogKey)) {
                            if (debug) {
                                log.info("DUPLICATE DIALOG DETECTED - Skipping: '{}'", finalText);
                            }
                            return;
                        }

                        lastCompleteDialogKey = dialogKey;

                        String self = localPlayerName();
                        if (self != null && spk.equalsIgnoreCase(self)) {
                            if (debug) log.info("Speaking as PLAYER: '{}'", finalText);
                            voiceRuntime.speakPlayer(finalText);
                        } else {
                            if (debug) log.info("Speaking as NPC '{}': '{}'", spk, finalText);
                            voiceRuntime.speakNpc(spk.isEmpty()?"NPC":spk, finalText, voiceRuntime.inferTags(spk));
                        }
                        return;
                    }
                } else if (debug) {
                    log.info("DIALOG message does not contain expected '|' separator. Parts: {}", parts.length);
                    for (int i = 0; i < parts.length; i++) {
                        log.info("Dialog part {}: '{}'", i, parts[i]);
                    }
                }
            }

            String tU = typeName.toUpperCase(Locale.ROOT);
            if (tU.contains("GAME") || tU.contains("SPAM") || tU.contains("ENGINE") || tU.contains("BROADCAST")) {
                if (debug) log.info("Skipping message type: {}", tU);
                return;
            }

            String speaker = sanitizeName(evt.getName());
            String self = localPlayerName();
            boolean playerChan = tU.contains("PUBLIC") || tU.contains("FRIEND") || tU.contains("CLAN") || tU.contains("PRIVATE") || tU.contains("AUTOTYPER");
            boolean npcChan = tU.contains("NPC");

            if (debug) {
                log.info("Channel Analysis - Type: {}, PlayerChan: {}, NpcChan: {}, Speaker: '{}'", tU, playerChan, npcChan, speaker);
            }

            // Gate reading of self public chat behind property and avoid during active dialog widgets
            boolean readPlayerPublic = "true".equalsIgnoreCase(System.getProperty("osrs.tts.readPlayerPublicChat", "false"));
            long sinceDialogMs = System.currentTimeMillis() - lastDialogWidgetAtMs;
            boolean dialogActive = sinceDialogMs >= 0 && sinceDialogMs < 2500;

            if (self != null && speaker.equalsIgnoreCase(self) && playerChan) {
                boolean isPublic = tU.contains("PUBLIC");
                if (isPublic && (!readPlayerPublic || dialogActive)) {
                    if (debug) log.info("Suppressing self PUBLIC chat (readPlayerPublic={}, dialogActive={})", readPlayerPublic, dialogActive);
                    return;
                }
                if (debug) log.info("Speaking as SELF/PLAYER: '{}'", msg);
                voiceRuntime.speakPlayer(msg);
                return;
            }
            if (npcChan && !speaker.isBlank()) {
                if (debug) log.info("Speaking as NPC '{}' (NPC channel): '{}'", speaker, msg);
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
                return;
            }
            if (!speaker.isBlank() && !playerChan) {
                if (debug) log.info("Speaking as NPC '{}' (other channel): '{}'", speaker, msg);
                voiceRuntime.speakNpc(speaker, msg, voiceRuntime.inferTags(speaker));
            }
        } catch (Exception e) {
            if (isDebug()) log.error("Chat handling error: {}", e.getMessage(), e);
        }

        if (debug) log.info("=== END CHAT MESSAGE DEBUG ===");
    }

    @Subscribe
    public void onGameTick(GameTick tick) {
        if (voiceRuntime == null || config == null || client == null) return;
    // Legacy master toggle still honored; then granular toggles decide which categories run
	if (!config.isOverheadEnabled()) {
            if (isDebug()) log.info("Overhead processing skipped - master toggle disabled");
            return;
        }
        try {
            // NPC overhead TTS disabled as requested
            /*
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
            */

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

    // Decide if NarrationDetector should speak a dialog it found in widgets.
    // Return true to allow widget speech, false to suppress (chat already handled it).
    private boolean onNarrationDialogFoundCallback(String speaker, String text) {
        boolean debug = isDebug();
        String spk = sanitizeName(speaker);
        String dialogKey = spk + "|" + text.hashCode();

        // If chat already spoke this complete line, suppress widget speech
        if (dialogKey.equals(lastCompleteDialogKey)) {
            if (debug) log.info("Widget dialog duplicate - suppressed: '{}'", dialogKey);
            return false;
        }

        long now = System.currentTimeMillis();

        // If this widget text completes a previously stored incomplete chat dialog, allow widget to speak it
        if (lastIncompleteDialog != null && lastDialogSpeaker != null && spk.equalsIgnoreCase(lastDialogSpeaker)) {
            String normalizedNarration = normalizeForContain(text);
            String normalizedIncomplete = normalizeForContain(lastIncompleteDialog);
            boolean completes = containsEither(normalizedNarration, normalizedIncomplete);
            if (!completes && normalizedIncomplete.length() > 30) {
                int cut = Math.max(10, (int)(normalizedIncomplete.length() * 0.6));
                String partial = normalizedIncomplete.substring(0, cut);
                completes = containsEither(normalizedNarration, partial) || hasStrongCommonPrefix(normalizedNarration, normalizedIncomplete, 28);
            }
            if (completes) {
                if (debug) {
                    log.info("Widget dialog completes incomplete chat - allowing widget speech");
                    log.info("  Incomplete: '{}'", lastIncompleteDialog);
                    log.info("  Complete: '{}'", text);
                }
                cancelIncompleteTimeout = true;
                lastIncompleteDialog = null;
                lastDialogSpeaker = null;
                lastDialogTime = 0;
                incompleteDialogKey = null;
                lastCompleteDialogKey = dialogKey;
                return true;
            }
        }

        // Grace: if timeout just spoke incomplete, and widget contains/extends it, allow widget speech
        if (lastTimedOutDialogText != null && lastTimedOutSpeaker != null &&
            spk.equalsIgnoreCase(lastTimedOutSpeaker) &&
            (now - lastTimedOutAtMs) <= TIMEOUT_COMPLETION_GRACE_MS) {

            String normalizedNarration = normalizeForContain(text);
            String normalizedTimedOut = normalizeForContain(lastTimedOutDialogText);
            boolean extendsTimedOut = containsEither(normalizedNarration, normalizedTimedOut);
            if (!extendsTimedOut) {
                // Accept strong common prefix match or significant length extension
                extendsTimedOut = hasStrongCommonPrefix(normalizedNarration, normalizedTimedOut, 28);
                if (!extendsTimedOut && normalizedNarration.length() > normalizedTimedOut.length() + 15) {
                    int cut = Math.max(10, (int)(normalizedTimedOut.length() * 0.6));
                    String partial = normalizedTimedOut.substring(0, Math.min(cut, normalizedTimedOut.length()));
                    extendsTimedOut = containsEither(normalizedNarration, partial);
                }
            }
            if (extendsTimedOut) {
                if (debug) {
                    log.info("Widget dialog extends recently timed-out chat - allowing widget speech (grace)");
                    log.info("  TimedOut: '{}'", lastTimedOutDialogText);
                    log.info("  Complete: '{}'", text);
                }
                lastCompleteDialogKey = dialogKey;
                // Clear grace record
                lastTimedOutDialogText = null;
                lastTimedOutSpeaker = null;
                lastTimedOutAtMs = 0L;
                return true;
            }
        }

        // Default: suppress widget speech; chat is the source of truth for dialog
        if (debug) log.info("Widget dialog suppressed by default (no completion needed)");
        return false;
    }

    private static boolean containsEither(String a, String b) {
        if (a == null || b == null) return false;
        return a.contains(b) || b.contains(a);
    }

    private static boolean hasStrongCommonPrefix(String a, String b, int minChars) {
        if (a == null || b == null) return false;
        int n = Math.min(a.length(), b.length());
        int i = 0;
        while (i < n && a.charAt(i) == b.charAt(i)) i++;
        return i >= minChars;
    }

    private static String normalizeForContain(String s) {
        if (s == null) return "";
        String out = s.toLowerCase(Locale.ROOT);
        out = out.replaceAll("<[^>]*>", ""); // strip tags defensively
        out = out.replace('\u00A0', ' '); // nbsp to space
        out = out.replaceAll("[^a-z0-9 ]+", " "); // remove punctuation/specials
        out = out.replaceAll("\\s+", " ").trim();
        return out;
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

