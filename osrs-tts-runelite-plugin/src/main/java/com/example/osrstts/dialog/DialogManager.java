package com.example.osrstts.dialog;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.tts.TtsManager;
import com.example.osrstts.voice.VoiceManager;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public class DialogManager {
	private final OsrsTtsConfig config;
	private final TtsManager ttsManager;
	private final VoiceManager voiceManager;

	@Inject
	public DialogManager(OsrsTtsConfig config, TtsManager ttsManager, VoiceManager voiceManager) {
		this.config = config;
		this.ttsManager = ttsManager;
		this.voiceManager = voiceManager;
	}

	public void speakNpc(String npc, String text) {
		String voice = voiceManager.getVoiceForNpc(npc, null);
		ttsManager.speak(text, voice);
	}
}

