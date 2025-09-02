package com.example.osrstts.cache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enhanced audio cache with single-flight deduplication and version-based invalidation.
 * Provides "forever cache" - generated audio is cached indefinitely unless manually purged.
 */
public class AudioCache {
    private static final String DEFAULT_VERSION = "1";
    
    private final Path cacheDir;
    private final String version;
    private final ConcurrentHashMap<String, CompletableFuture<Path>> inFlightRequests = new ConcurrentHashMap<>();
    
    public AudioCache(String dir) {
        this(dir, DEFAULT_VERSION);
    }
    
    public AudioCache(String dir, String version) {
        this.cacheDir = Paths.get(dir);
        this.version = version != null ? version : DEFAULT_VERSION;
        
        try {
            Files.createDirectories(this.cacheDir);
        } catch (IOException e) {
            System.err.println("Warning: Could not create cache directory: " + this.cacheDir);
        }
    }
    
    /**
     * Get cached audio file if it exists.
     */
    public Optional<Path> get(String key) {
        try {
            Path path = pathFor(key);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        } catch (Exception e) {
            System.err.println("Warning: Error accessing cache for key " + key + ": " + e.getMessage());
        }
        return Optional.empty();
    }
    
    /**
     * Store audio data in cache and return the path.
     */
    public Path put(String key, byte[] audio, String ext) {
        Path path = pathFor(key, ext);
        try {
            Files.write(path, audio);
            return path;
        } catch (IOException e) {
            System.err.println("Warning: Could not write to cache: " + path);
            throw new RuntimeException("Cache write failed", e);
        }
    }
    
    /**
     * Generate cache key for the given parameters.
     * Includes version to allow cache invalidation when mappings change.
     */
    public String keyFor(String provider, String voiceId, String npcKey, String normalizedText, String version) {
        String input = provider + "|" + voiceId + "|" + npcKey + "|" + normalizedText + "|" + version;
        return hashString(input);
    }
    
    /**
     * Generate cache key using the current cache version.
     */
    public String keyFor(String provider, String voiceId, String npcKey, String normalizedText) {
        return keyFor(provider, voiceId, npcKey, normalizedText, this.version);
    }
    
    /**
     * Get cache path for a key with default wav extension.
     */
    public Path pathFor(String key) {
        return pathFor(key, "wav");
    }
    
    /**
     * Get cache path for a key with specified extension.
     */
    public Path pathFor(String key, String ext) {
        String filename = sanitizeKey(key) + "." + ext;
        return cacheDir.resolve(filename);
    }
    
    /**
     * Single-flight: ensure only one synthesis happens for the same key at a time.
     * Returns a CompletableFuture that will complete with the cached audio path.
     */
    public CompletableFuture<Path> getOrCompute(String key, java.util.function.Supplier<byte[]> synthesizer) {
        // Check if already cached
        Optional<Path> existing = get(key);
        if (existing.isPresent()) {
            return CompletableFuture.completedFuture(existing.get());
        }
        
        // Use single-flight pattern to prevent duplicate synthesis
        return inFlightRequests.computeIfAbsent(key, k -> {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    // Double-check cache after acquiring the future
                    Optional<Path> cached = get(key);
                    if (cached.isPresent()) {
                        return cached.get();
                    }
                    
                    // Synthesize and cache
                    byte[] audio = synthesizer.get();
                    return put(key, audio, "wav");
                } finally {
                    // Remove from in-flight map when done
                    inFlightRequests.remove(key);
                }
            });
        });
    }
    
    /**
     * Clear all cached files.
     */
    public void clearAll() {
        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("Warning: Could not delete cache file: " + path);
                        }
                    });
            }
        } catch (IOException e) {
            System.err.println("Warning: Error clearing cache: " + e.getMessage());
        }
    }
    
    /**
     * Clear cached files for a specific NPC.
     */
    public void clearForNpc(String npcKey) {
        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        // This is a heuristic - we'd need to store metadata to do this perfectly
                        String filename = path.getFileName().toString();
                        return filename.contains(hashString(npcKey));
                    })
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            System.err.println("Warning: Could not delete cache file: " + path);
                        }
                    });
            }
        } catch (IOException e) {
            System.err.println("Warning: Error clearing cache for NPC: " + e.getMessage());
        }
    }
    
    /**
     * Get cache size information.
     */
    public CacheStats getStats() {
        try {
            if (!Files.exists(cacheDir)) {
                return new CacheStats(0, 0);
            }
            
            long[] stats = {0, 0}; // fileCount, totalBytes
            Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        stats[0]++; // file count
                        stats[1] += Files.size(path); // total bytes
                    } catch (IOException ignored) {}
                });
                
            return new CacheStats((int) stats[0], stats[1]);
        } catch (IOException e) {
            return new CacheStats(0, 0);
        }
    }
    
    /**
     * Normalize text for consistent cache keys.
     */
    public static String normalizeText(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\\s+", " ")  // Normalize whitespace
                .replaceAll("[\\r\\n]+", " ") // Remove line breaks
                .toLowerCase(); // Case insensitive
    }
    
    private String sanitizeKey(String key) {
        return key.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    private String hashString(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 16); // Use first 16 chars for shorter filenames
        } catch (Exception e) {
            // Fallback to simple hash if SHA-256 fails
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }
    
    /**
     * Cache statistics for UI display.
     */
    public static record CacheStats(
        int fileCount,
        long totalBytes
    ) {
        public String getDisplayText() {
            if (totalBytes < 1024) {
                return fileCount + " files, " + totalBytes + " bytes";
            } else if (totalBytes < 1024 * 1024) {
                return fileCount + " files, " + (totalBytes / 1024) + " KB";
            } else {
                return fileCount + " files, " + (totalBytes / (1024 * 1024)) + " MB";
            }
        }
    }
}