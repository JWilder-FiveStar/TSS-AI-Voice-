package com.example.osrstts.tts;

import com.example.osrstts.voice.VoiceSelection;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class AzureSpeechTtsClient implements TtsClient {
        private final String key;
        private final String region;
        private final String outputFormat; // e.g., "riff-24000hz-16bit-mono-pcm" or mp3
        private final HttpClient http = HttpClient.newHttpClient();

        public AzureSpeechTtsClient(String key, String region, String outputFormat) {
                this.key = key;
                this.region = region;
                this.outputFormat = outputFormat;
        }

        @Override
        public byte[] synthesize(String text, VoiceSelection sel) throws Exception {
                String ssml = buildSsml(sel.voiceName, sel.style, text);
                HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"))
                                .header("Content-Type", "application/ssml+xml")
                                .header("X-Microsoft-OutputFormat", outputFormat)
                                .header("Ocp-Apim-Subscription-Key", key)
                                .header("User-Agent", "osrs-tts-plugin")
                                .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
                                .build();

                HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                        return resp.body();
                }
                throw new RuntimeException("Azure TTS error " + resp.statusCode());
        }

        private String buildSsml(String voice, String style, String text) {
                String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                if (style != null && !style.isEmpty()) {
                                return "<speak version=\"1.0\" xml:lang=\"en-US\" xmlns:mstts=\"https://www.w3.org/2001/mstts\">" +
                                        "<voice name=\"" + voice + "\">" +
                                        "<mstts:express-as style=\"" + style + "\">" + escaped + "</mstts:express-as>" +
                                        "</voice>" +
                                        "</speak>";
                }
                        return "<speak version=\"1.0\" xml:lang=\"en-US\">" +
                                "<voice name=\"" + voice + "\">" + escaped + "</voice>" +
                                "</speak>";
        }
}