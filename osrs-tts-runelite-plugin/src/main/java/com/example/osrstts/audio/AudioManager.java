package com.example.osrstts.audio;

import com.example.osrstts.OsrsTtsConfig;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class AudioManager {
	private final OsrsTtsConfig config;

	@Inject
	public AudioManager(OsrsTtsConfig config) {
		this.config = config;
	}

	public float getEffectiveVolume() {
		// Map stored percent to 0..1
		return Math.max(0f, Math.min(1f, config.getVolumePercent() / 100f));
	}
}

