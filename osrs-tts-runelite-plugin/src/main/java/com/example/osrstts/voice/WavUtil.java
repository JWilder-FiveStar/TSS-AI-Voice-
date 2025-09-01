package com.example.osrstts.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class WavUtil {
    private WavUtil() {}

    public static byte[] wrapPcmToWav(byte[] pcmData, int sampleRateHz, short bitsPerSample, short channels) {
        int byteRate = sampleRateHz * channels * (bitsPerSample / 8);
        int blockAlign = channels * (bitsPerSample / 8);
        int dataSize = pcmData.length;
        int chunkSize = 36 + dataSize;

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
            // RIFF header
            out.write(new byte[]{'R','I','F','F'});
            out.write(intLE(chunkSize));
            out.write(new byte[]{'W','A','V','E'});
            // fmt chunk
            out.write(new byte[]{'f','m','t',' '});
            out.write(intLE(16)); // PCM fmt chunk size
            out.write(shortLE((short)1)); // audio format = 1 (PCM)
            out.write(shortLE(channels));
            out.write(intLE(sampleRateHz));
            out.write(intLE(byteRate));
            out.write(shortLE((short)blockAlign));
            out.write(shortLE(bitsPerSample));
            // data chunk
            out.write(new byte[]{'d','a','t','a'});
            out.write(intLE(dataSize));
            out.write(pcmData);
            return out.toByteArray();
        } catch (IOException e) {
            // should not happen with ByteArrayOutputStream
            return pcmData;
        }
    }

    private static byte[] intLE(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }
    private static byte[] shortLE(short v) {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v).array();
    }
}
