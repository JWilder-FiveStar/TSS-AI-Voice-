package com.example.osrstts.tts;

import com.example.osrstts.voice.VoiceSelection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import com.example.osrstts.OsrsTtsConfig;

public class AzureSpeechTtsClient implements TtsClient {
    private final String key;
    private final String region;
    private final String outputFormat; // e.g., "riff-24khz-16bit-mono-pcm" or "audio-16khz-128kbitrate-mono-mp3"

    // We will explicitly create an HTTP/1.1 client for parity with curl
    private final HttpClient http11 = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public AzureSpeechTtsClient(String key, String region, String outputFormat) {
        this.key = key;
        this.region = region;
        // Ensure we request a WAV/PCM format for JavaSound playback
        String fmt = outputFormat == null ? "" : outputFormat.toLowerCase();
        if (!fmt.contains("riff") && !fmt.contains("pcm")) {
            this.outputFormat = "riff-24khz-16bit-mono-pcm";
        } else {
            this.outputFormat = outputFormat;
        }
    }

    @Override
    public byte[] synthesize(String text, VoiceSelection sel) throws Exception {
        final String ssml = buildSsml(sel.voiceName, sel.style, text);
        final String trimmedKey = key.trim();

        byte[] bodyUtf8 = ssml.getBytes(StandardCharsets.UTF_8);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", outputFormat)
                .header("Ocp-Apim-Subscription-Key", trimmedKey)
                .header("User-Agent", "osrs-tts-plugin")
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyUtf8))
                .build();

        HttpResponse<byte[]> resp = http11.send(req, HttpResponse.BodyHandlers.ofByteArray());

        if (resp.statusCode() / 100 == 2) {
            return resp.body();
        }

        String errorBody = safeToString(resp.body());
        System.out.println("Azure TTS Error " + resp.statusCode() + ": " + errorBody);
        System.out.println("Response headers: " + resp.headers().map());
        throw new RuntimeException("Azure TTS error " + resp.statusCode() + ": " + errorBody);
    }

    /** Optional: convenience to list voices for quick region/key sanity check. */
    public String listVoicesSample() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/voices/list"))
                .timeout(Duration.ofSeconds(15))
                .header("Ocp-Apim-Subscription-Key", key.trim())
                .header("User-Agent", "osrs-tts-plugin")
                .GET()
                .build();

        HttpResponse<String> resp = http11.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 == 2) {
            return resp.body();
        }
        return "Voices list failed " + resp.statusCode() + ": " + resp.body();
    }

    // ---- Helpers ----

    private String buildSsml(String voice, String style, String text) {
        // Escape minimal XML entities
        String escaped = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");

        // Derive locale from voice short name (e.g., en-GB-RyanNeural -> en-GB)
        String locale = "en-US";
        if (voice != null) {
            String[] parts = voice.split("-");
            if (parts.length >= 2) {
                locale = parts[0] + "-" + parts[1];
            }
        }

        // Keep SSML simple and let Azure choose gender based on voice name
        return "<speak version='1.0' xml:lang='" + locale + "'>"
                + "<voice xml:lang='" + locale + "' name='" + voice + "'>"
                + escaped
                + "</voice>"
                + "</speak>";
    }

    private static String safeToString(byte[] bytes) {
        try {
            return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    @Override public boolean isConfigured(OsrsTtsConfig config) { return key != null && !key.isBlank() && region != null && !region.isBlank(); }
    @Override public boolean testConnection(OsrsTtsConfig config) { try { listVoicesSample(); return true; } catch (Exception e) { return false; } }
    @Override public String getProviderName() { return "Azure"; }
    @Override public void shutdown() { }
}
