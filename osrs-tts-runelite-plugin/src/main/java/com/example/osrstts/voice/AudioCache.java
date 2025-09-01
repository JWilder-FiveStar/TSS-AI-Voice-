package com.example.osrstts.voice;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AudioCache {
    private final Path dir;

    public AudioCache(String dir) {
        this.dir = Paths.get(dir);
        try { Files.createDirectories(this.dir); } catch (IOException ignored) {}
    }

    public Path pathFor(String key, String ext) {
        String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return dir.resolve(safe + "." + ext);
    }

    public byte[] get(String key, String ext) {
        try {
            Path p = pathFor(key, ext);
            if (Files.exists(p)) return Files.readAllBytes(p);
        } catch (IOException ignored) {}
        return null;
    }

    public void put(String key, String ext, byte[] data) {
        try {
            Files.write(pathFor(key, ext), data);
        } catch (IOException ignored) {}
    }
}
