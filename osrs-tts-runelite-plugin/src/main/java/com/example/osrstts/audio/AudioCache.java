package com.example.osrstts.audio;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;
import java.util.Queue;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Professional audio caching system for optimized TTS playback.
 * Provides intelligent caching with memory management and preloading support.
 */
@Singleton
public class AudioCache {
    private static final Logger log = LoggerFactory.getLogger(AudioCache.class);
    
    // Cache settings
    private static final int MAX_CACHE_SIZE = 100;
    private static final long MAX_CACHE_MEMORY = 50 * 1024 * 1024; // 50MB
    
    // Cache storage
    private final Map<String, CachedAudio> audioCache = new ConcurrentHashMap<>();
    private final Queue<String> accessOrder = new ConcurrentLinkedQueue<>();
    private volatile long totalCacheSize = 0;
    
    /**
     * Cached audio data with metadata
     */
    private static class CachedAudio {
        final byte[] audioData;
        final AudioFormat format;
        final long timestamp;
        final int size;
        
        CachedAudio(byte[] audioData, AudioFormat format) {
            this.audioData = audioData.clone();
            this.format = format;
            this.timestamp = System.currentTimeMillis();
            this.size = audioData.length;
        }
    }
    
    /**
     * Store audio data in cache
     */
    public void put(String key, byte[] audioData, AudioFormat format) {
        if (key == null || audioData == null || format == null) {
            return;
        }
        
        try {
            CachedAudio cached = new CachedAudio(audioData, format);
            
            // Remove existing entry if present
            remove(key);
            
            // Check if we need to make space
            ensureCapacity(cached.size);
            
            // Add to cache
            audioCache.put(key, cached);
            accessOrder.offer(key);
            totalCacheSize += cached.size;
            
            log.debug("Cached audio: {} ({} bytes, total: {} entries, {} bytes)", 
                key, cached.size, audioCache.size(), totalCacheSize);
                
        } catch (Exception e) {
            log.warn("Failed to cache audio for key: {}", key, e);
        }
    }
    
    /**
     * Retrieve audio data from cache
     */
    public byte[] get(String key) {
        if (key == null) {
            return null;
        }
        
        CachedAudio cached = audioCache.get(key);
        if (cached != null) {
            // Update access order
            accessOrder.remove(key);
            accessOrder.offer(key);
            
            log.debug("Cache hit for: {}", key);
            return cached.audioData.clone();
        }
        
        log.debug("Cache miss for: {}", key);
        return null;
    }
    
    /**
     * Get audio format for cached entry
     */
    public AudioFormat getFormat(String key) {
        if (key == null) {
            return null;
        }
        
        CachedAudio cached = audioCache.get(key);
        return cached != null ? cached.format : null;
    }
    
    /**
     * Check if audio is cached
     */
    public boolean contains(String key) {
        return key != null && audioCache.containsKey(key);
    }
    
    /**
     * Remove specific entry from cache
     */
    public void remove(String key) {
        if (key == null) {
            return;
        }
        
        CachedAudio cached = audioCache.remove(key);
        if (cached != null) {
            accessOrder.remove(key);
            totalCacheSize -= cached.size;
            log.debug("Removed from cache: {} ({} bytes)", key, cached.size);
        }
    }
    
    /**
     * Clear all cached audio
     */
    public void clear() {
        audioCache.clear();
        accessOrder.clear();
        totalCacheSize = 0;
        log.info("Audio cache cleared");
    }
    
    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        return new CacheStats(
            audioCache.size(),
            totalCacheSize,
            MAX_CACHE_SIZE,
            MAX_CACHE_MEMORY
        );
    }
    
    /**
     * Ensure cache has space for new entry
     */
    private void ensureCapacity(int newEntrySize) {
        // Remove old entries if we exceed limits
        while ((audioCache.size() >= MAX_CACHE_SIZE || 
                totalCacheSize + newEntrySize > MAX_CACHE_MEMORY) && 
               !accessOrder.isEmpty()) {
            
            String oldestKey = accessOrder.poll();
            if (oldestKey != null) {
                CachedAudio oldest = audioCache.remove(oldestKey);
                if (oldest != null) {
                    totalCacheSize -= oldest.size;
                    log.debug("Evicted from cache: {} ({} bytes)", oldestKey, oldest.size);
                }
            }
        }
    }
    
    /**
     * Generate cache key for TTS request
     */
    public static String generateKey(String text, String voice, String provider, String model) {
        if (text == null) {
            return null;
        }
        
        StringBuilder key = new StringBuilder();
        key.append(provider != null ? provider : "unknown");
        key.append("|");
        key.append(model != null ? model : "default");
        key.append("|");
        key.append(voice != null ? voice : "default");
        key.append("|");
        key.append(text.hashCode());
        
        return key.toString();
    }
    
    /**
     * Convert audio data to cacheable format
     */
    public static byte[] convertToCacheFormat(Clip clip) {
        if (clip == null) {
            return null;
        }
        
        try {
            AudioFormat format = clip.getFormat();
            int frameLength = (int) clip.getFrameLength();
            int frameSize = format.getFrameSize();
            
            byte[] audioData = new byte[frameLength * frameSize];
            clip.open(format, audioData, 0, audioData.length);
            
            return audioData;
        } catch (Exception e) {
            log.warn("Failed to convert clip to cache format", e);
            return null;
        }
    }
    
    /**
     * Create clip from cached audio data
     */
    public Clip createClip(String key) {
        CachedAudio cached = audioCache.get(key);
        if (cached == null) {
            return null;
        }
        
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(cached.format, cached.audioData, 0, cached.audioData.length);
            return clip;
        } catch (Exception e) {
            log.warn("Failed to create clip from cached audio: {}", key, e);
            return null;
        }
    }
    
    /**
     * Cache statistics
     */
    public static class CacheStats {
        public final int entries;
        public final long memoryUsed;
        public final int maxEntries;
        public final long maxMemory;
        public final double memoryUsagePercent;
        public final double entryUsagePercent;
        
        CacheStats(int entries, long memoryUsed, int maxEntries, long maxMemory) {
            this.entries = entries;
            this.memoryUsed = memoryUsed;
            this.maxEntries = maxEntries;
            this.maxMemory = maxMemory;
            this.memoryUsagePercent = maxMemory > 0 ? (memoryUsed * 100.0 / maxMemory) : 0;
            this.entryUsagePercent = maxEntries > 0 ? (entries * 100.0 / maxEntries) : 0;
        }
        
        @Override
        public String toString() {
            return String.format("AudioCache[entries=%d/%d (%.1f%%), memory=%dKB/%dKB (%.1f%%)]",
                entries, maxEntries, entryUsagePercent,
                memoryUsed / 1024, maxMemory / 1024, memoryUsagePercent);
        }
    }
}
