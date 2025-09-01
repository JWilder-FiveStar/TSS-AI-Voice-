package com.example.osrstts.tts;

import com.example.osrstts.voice.VoiceSelection;

public interface TtsClient {
    /**
     * Synthesize the given text with the provided selection and return audio bytes.
     */
    byte[] synthesize(String text, VoiceSelection selection) throws Exception;
}