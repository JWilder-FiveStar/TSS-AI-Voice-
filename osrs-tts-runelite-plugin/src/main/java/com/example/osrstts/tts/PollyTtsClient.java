package com.example.osrstts.tts;

import com.amazonaws.services.polly.AmazonPolly;
import com.amazonaws.services.polly.AmazonPollyClientBuilder;
import com.amazonaws.services.polly.model.OutputFormat;
import com.amazonaws.services.polly.model.SynthesizeSpeechRequest;
import com.amazonaws.services.polly.model.SynthesizeSpeechResult;
import com.example.osrstts.voice.VoiceSelection;
import com.example.osrstts.voice.WavUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import com.example.osrstts.OsrsTtsConfig;

public class PollyTtsClient implements TtsClient {
    private final AmazonPolly polly;

    public PollyTtsClient() {
        this.polly = AmazonPollyClientBuilder.defaultClient();
    }

    @Override
    public byte[] synthesize(String text, VoiceSelection selection) throws Exception {
        // Polly TTS limit is 3000 chars per request (as of 2025)
        if (text.length() > 3000) {
            throw new IllegalArgumentException("Text too long for Polly TTS (max 3000 chars per request). Split into smaller chunks.");
        }
        String voiceId = selection != null && selection.voiceName != null ? selection.voiceName : "Matthew";
        SynthesizeSpeechRequest request = new SynthesizeSpeechRequest()
                .withText(text)
                .withVoiceId(voiceId)
                // Use PCM so we can wrap it into a WAV container for unified playback
                .withOutputFormat(OutputFormat.Pcm)
                // Use 22050 Hz mono 16-bit; Polly's PCM returns raw little-endian 16-bit by default
                .withSampleRate("22050");

        SynthesizeSpeechResult result = polly.synthesizeSpeech(request);
        try (InputStream in = result.getAudioStream(); ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            in.transferTo(buf);
            byte[] pcm = buf.toByteArray();
            return WavUtil.wrapPcmToWav(pcm, 22050, (short)16, (short)1);
        }
    }

    @Override public boolean isConfigured(OsrsTtsConfig config) { return true; }
    @Override public boolean testConnection(OsrsTtsConfig config) { return true; }
    @Override public String getProviderName() { return "Polly"; }
    @Override public void shutdown() { }
}