package com.example.osrstts.tts;

import com.example.osrstts.OsrsTtsConfig;
import com.example.osrstts.voice.VoiceSelection;
import java.util.concurrent.CompletableFuture;

/**
 * Simplified TTS client interface aligned with current runtime usage.
 * Implementations expose a synchronous synthesize; a default async wrapper is provided.
 */
public interface TtsClient {

    boolean isConfigured(OsrsTtsConfig config);

    /**
     * Synthesize speech for the given text and voice selection (voice + optional style).
     */
    byte[] synthesize(String text, VoiceSelection selection) throws Exception;

    /** Default asynchronous wrapper. */
    default CompletableFuture<byte[]> synthesizeAsync(String text, VoiceSelection selection) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return synthesize(text, selection);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    boolean testConnection(OsrsTtsConfig config);

    String getProviderName();

    void shutdown();
}