package com.example.osrstts.tts;

import com.example.osrstts.voice.VoiceSelection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import com.example.osrstts.OsrsTtsConfig;

public class ElevenLabsTtsClient implements TtsClient {
    private final String apiKey;
    private final String modelId;
    private final String outputFormat; // preferred format

    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public ElevenLabsTtsClient(String apiKey, String modelId, String outputFormat) {
        this.apiKey = apiKey;
        this.modelId = modelId == null || modelId.isBlank() ? "eleven_turbo_v2_5" : modelId;
        // Prefer plain 'wav'; we'll fallback to other variants if server ignores
        this.outputFormat = (outputFormat == null || outputFormat.isBlank()) ? "wav" : outputFormat;
    }

    @Override
    public byte[] synthesize(String text, VoiceSelection sel) throws Exception {
        // ElevenLabs API limit is 2048 chars per request (as of 2025); split if needed
        if (text.length() > 2048) {
            throw new IllegalArgumentException("Text too long for ElevenLabs (max 2048 chars per request). Split into smaller chunks.");
        }
        String voiceId = extractVoiceId(sel.voiceName);
        if (voiceId == null || voiceId.isBlank()) {
            throw new IllegalArgumentException("ElevenLabs voice_id not set. Select a voice using the 'Load 11Labs Voices' button and pick one (Name (id)).");
        }
        List<String> tried = new ArrayList<>();
        // Try preferred first, then fallbacks commonly supported by ElevenLabs
        String[] candidates = new String[] { this.outputFormat, "wav", "wav_22050", "pcm_16000", "pcm_22050" };
        RuntimeException lastError = null;
        for (String fmt : candidates) {
            if (fmt == null || fmt.isBlank()) continue;
            if (tried.contains(fmt)) continue;
            tried.add(fmt);
            try {
                byte[] data = requestOnce(text, voiceId, fmt, sel == null ? null : sel.style);
                // Accept whatever audio we received; playback will handle MP3 or WAV
                return data;
            } catch (RuntimeException ex) {
                lastError = ex;
            }
        }
        if (lastError != null) throw lastError;
        throw new RuntimeException("ElevenLabs TTS: no audio returned");
    }

    private byte[] requestOnce(String text, String voiceId, String format, String style) throws Exception {
        String voiceSettings = voiceSettingsForStyle(style);
        String bodyJson = "{" +
                jsonField("text", text) + "," +
                jsonField("model_id", modelId) + "," +
                jsonField("output_format", format) + "," +
                "\"voice_settings\":" + voiceSettings +
                "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + voiceId))
                .timeout(Duration.ofSeconds(30))
                .header("xi-api-key", apiKey.trim())
                .header("accept", "audio/wav")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 == 2) {
            return resp.body();
        }
        String msg = tryUtf8(resp.body());
        String ct = resp.headers().firstValue("content-type").orElse("");
        throw new RuntimeException("ElevenLabs TTS error " + resp.statusCode() + " (ct=" + ct + "): " + msg);
    }

    private static String voiceSettingsForStyle(String style) {
        // Return JSON object string for voice_settings
        double stability = 0.4;
        double similarity = 0.7;
        if (style != null) {
            String s = style.toLowerCase();
            switch (s) {
                case "excited":
                    stability = 0.25; similarity = 0.75; break;
                case "sad":
                    stability = 0.7; similarity = 0.6; break;
                case "angry":
                    stability = 0.2; similarity = 0.6; break;
                case "whisper":
                    stability = 0.8; similarity = 0.35; break;
                default:
                    // chat / neutral
                    stability = 0.4; similarity = 0.7; break;
            }
        }
        return "{" + "\"stability\":" + stability + ",\"similarity_boost\":" + similarity + "}";
    }

    public String listVoicesSample() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.elevenlabs.io/v1/voices"))
                .timeout(Duration.ofSeconds(15))
                .header("xi-api-key", apiKey.trim())
                .header("accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 == 2) return resp.body();
        return "Voices list failed " + resp.statusCode() + ": " + resp.body();
    }

    private static String jsonField(String k, String v) {
        return "\"" + k + "\":\"" + escapeJson(v) + "\"";
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    private static String tryUtf8(byte[] b) {
        try { return b == null ? "" : new String(b, StandardCharsets.UTF_8); } catch (Exception e) { return ""; }
    }

    private static boolean looksRiffWav(byte[] data) {
        return data != null && data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E';
    }

    private static boolean looksMp3(byte[] data) {
        return (data != null && data.length >= 3 && data[0] == 'I' && data[1] == 'D' && data[2] == '3')
                || (data != null && data.length >= 2 && (data[0] & 0xFF) == 0xFF && ((data[1] & 0xE0) == 0xE0));
    }

    private static final Pattern ID_PATTERN = Pattern.compile("[A-Za-z0-9]{20,}");

    private static String extractVoiceId(String nameOrId) {
        if (nameOrId == null) return null;
        String s = nameOrId.trim();
        int i = s.lastIndexOf('(');
        int j = s.lastIndexOf(')');
        if (i >= 0 && j > i) {
            String id = s.substring(i + 1, j).trim();
            if (ID_PATTERN.matcher(id).matches()) return id;
        }
        if (ID_PATTERN.matcher(s).matches()) return s;
        return null;
    }

    @Override public boolean isConfigured(OsrsTtsConfig config) { return apiKey != null && !apiKey.isBlank(); }
    @Override public boolean testConnection(OsrsTtsConfig config) { try { listVoicesSample(); return true; } catch (Exception e) { return false; } }
    @Override public String getProviderName() { return "ElevenLabs"; }
    @Override public void shutdown() { }
}
