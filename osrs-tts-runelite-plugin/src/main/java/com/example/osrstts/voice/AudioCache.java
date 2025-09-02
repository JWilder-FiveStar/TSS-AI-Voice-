package com.example.osrstts.voice;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class AudioCache {
    private final Path dir;
    private final Map<String, CompletableFuture<Path>> inflight = new ConcurrentHashMap<>();
    public static final String CURRENT_VERSION = "v1"; // bump to invalidate all

    public AudioCache(String dir) {
        this.dir = Paths.get(dir);
        try { Files.createDirectories(this.dir); } catch (IOException ignored) {}
    }

    public Path pathFor(String key, String ext) {
        String safe = key.replaceAll("[^a-zA-Z0-9._-]", "_");
        return dir.resolve(safe + "." + ext);
    }

    // Legacy helpers
    public byte[] get(String key, String ext) {
        try {
            Path p = pathFor(key, ext);
            if (Files.exists(p)) return Files.readAllBytes(p);
        } catch (IOException ignored) {}
        return null;
    }

    public void put(String key, String ext, byte[] data) {
        try { Files.write(pathFor(key, ext), data); } catch (IOException ignored) {}
    }

    // New API
    public Optional<Path> getPath(String key, String ext) {
        Path p = pathFor(key, ext);
        return Files.exists(p) ? Optional.of(p) : Optional.empty();
    }

    public Path putAndReturn(String key, String ext, byte[] data) {
        Path p = pathFor(key, ext);
        try { Files.write(p, data); } catch (IOException ignored) {}
        return p;
    }

    public String keyFor(String provider, String voiceId, String npcKey, String normalizedText, String version) {
        String material = String.join("|", nullSafe(provider), nullSafe(voiceId), nullSafe(npcKey), nullSafe(normalizedText), nullSafe(version));
        return sha1(material);
    }

    public CompletableFuture<Path> singleFlight(String key, String ext, java.util.concurrent.Callable<byte[]> synth) {
        return inflight.computeIfAbsent(key + "." + ext, k ->
                CompletableFuture.supplyAsync(() -> {
                    try { return synth.call(); } catch (Exception e) { throw new RuntimeException(e); }
                }).thenApply(data -> {
                    try { Files.createDirectories(dir); } catch (IOException ignored) {}
                    return putAndReturn(key, ext, data);
                }).whenComplete((p, t) -> inflight.remove(key + "." + ext))
        );
    }

    public static String normalizeText(String s) {
        if (s == null) return "";
        String t = s.replaceAll("<[^>]*>", " "); // strip markup
        t = t.replace('\n', ' ').replace('\r', ' ');
        t = t.trim().replaceAll("\\s+", " ");
        return t;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    private static String sha1(String in) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] d = md.digest(in.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(in.hashCode());
        }
    }
}

