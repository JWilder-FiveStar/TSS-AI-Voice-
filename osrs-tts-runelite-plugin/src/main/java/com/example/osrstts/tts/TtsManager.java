package com.example.osrstts.tts;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.audio.AudioManager; // optional future use
import com.example.osrstts.voice.VoiceSelection;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal restored TtsManager used by DialogManager / future advanced pipeline.
 * Provides provider auto‑selection (Azure > ElevenLabs > Polly) and async speak().
 */
@Slf4j
@Singleton
public class TtsManager {
	private final OsrsTtsConfig config;
	private final AudioManager audioManager; // Not strictly required yet (playback elsewhere)

	private final ExecutorService exec = Executors.newFixedThreadPool(2, r -> {
		Thread t = new Thread(r, "OSRS-TTS-Work");
		t.setDaemon(true);
		return t;
	});

	private final ConcurrentMap<String, TtsClient> clients = new ConcurrentHashMap<>();
	private volatile TtsClient active;
	private final AtomicBoolean init = new AtomicBoolean(false);

	@Inject
	public TtsManager(OsrsTtsConfig config, AudioManager audioManager) {
		this.config = config;
		this.audioManager = audioManager;
		initialize();
	}

	private void initialize() {
		if (!init.compareAndSet(false, true)) return;
		try {
			registerProviders();
			selectActive();
			log.info("TtsManager initialized (active={})", activeName());
		} catch (Exception e) {
			log.error("TtsManager init failed", e);
		}
	}

	private void registerProviders() {
		// Azure
		try {
			if (notBlank(config.getAzureKey())) {
				TtsClient azure = new AzureSpeechTtsClient(
						config.getAzureKey(),
						config.getAzureRegion(),
						config.getAudioOutputFormat());
				clients.put("azure", azure);
			}
		} catch (Throwable t) { log.debug("Skip Azure: {}", t.toString()); }

		// ElevenLabs
		try {
			if (notBlank(config.getElevenKey())) {
				TtsClient el = new ElevenLabsTtsClient(
						config.getElevenKey(),
						config.getElevenModel(),
						"wav");
				clients.put("elevenlabs", el);
			}
		} catch (Throwable t) { log.debug("Skip ElevenLabs: {}", t.toString()); }

		// Polly (will use default credentials chain if present)
		try {
			TtsClient polly = new PollyTtsClient();
			clients.put("polly", polly);
		} catch (Throwable t) { log.debug("Skip Polly: {}", t.toString()); }
	}

	private void selectActive() {
		String desired = safe(config.getProvider()).toLowerCase();
		if (clients.containsKey(desired)) {
			active = clients.get(desired);
			return;
		}
		// Fallback priority
		for (String k : new String[]{"azure", "elevenlabs", "polly"}) {
			if (clients.containsKey(k)) { active = clients.get(k); return; }
		}
		active = null;
	}

	private String activeName() {
		if (active == null) return "none";
		return clients.entrySet().stream().filter(e -> e.getValue()==active).map(Map.Entry::getKey).findFirst().orElse("?");
	}

	public CompletableFuture<Void> speak(String text, String voiceId) {
		if (text == null || text.isBlank()) return CompletableFuture.completedFuture(null);
		if (active == null) {
			log.warn("No active TTS provider; skipping speech");
			return CompletableFuture.completedFuture(null);
		}
		String voice = voiceId != null && !voiceId.isBlank() ? voiceId : config.getDefaultVoice();
		return CompletableFuture.runAsync(() -> {
			try {
				VoiceSelection sel = VoiceSelection.of(voice, null);
				byte[] audio = active.synthesize(text, sel);
				// Playback currently handled elsewhere (VoiceRuntime). We keep method for future integration.
				log.debug("Generated audio ({} bytes) via {} for '{}'", audio == null ? 0 : audio.length, activeName(), abbreviate(text));
			} catch (Exception e) {
				log.warn("TTS synthesis failed: {}", e.getMessage());
			}
		}, exec);
	}

	public String getStatus() { return active == null ? "No provider active" : ("Active: " + activeName()); }

	public void refresh() {
		clients.clear();
		registerProviders();
		selectActive();
	}

	public void shutdown() {
		exec.shutdownNow();
		clients.clear();
		active = null;
		init.set(false);
	}

	private static String abbreviate(String s) { return s.length() <= 48 ? s : s.substring(0,45)+"..."; }
	private static String safe(String s) { return s == null ? "" : s; }
	private static boolean notBlank(String s) { return s != null && !s.trim().isEmpty(); }
}

