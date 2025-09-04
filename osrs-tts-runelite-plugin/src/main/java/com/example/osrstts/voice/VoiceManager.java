package com.example.osrstts.voice;

import com.example.osrstts.OsrsTtsConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class VoiceManager {
	private final OsrsTtsConfig config;

	@Inject
	public VoiceManager(OsrsTtsConfig config) {
		this.config = config;
	}

	public String getNarratorVoice() { return config.getNarratorVoice(); }
	public String getPlayerVoice() { return config.getPlayerVoice(); }
	public String getVoiceForNpc(String name, String questCtx) {
		// Simplified: defer to default voice for now
		return config.getDefaultVoice();
	}
	public String getVoiceForTag(String tag) { return getVoiceForNpc(tag, null); }
}

