package com.example.osrstts.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AudioCache.
 */
class AudioCacheTest {
    
    @TempDir
    Path tempDir;
    
    private AudioCache cache;
    
    @BeforeEach
    void setUp() {
        cache = new AudioCache(tempDir.toString(), "test-version");
    }
    
    @Test
    void testPutAndGet() {
        String key = "test-key";
        byte[] audio = "test audio data".getBytes();
        
        Path storedPath = cache.put(key, audio, "wav");
        assertNotNull(storedPath);
        assertTrue(storedPath.toFile().exists());
        
        Optional<Path> retrievedPath = cache.get(key);
        assertTrue(retrievedPath.isPresent());
        assertEquals(storedPath, retrievedPath.get());
    }
    
    @Test
    void testKeyGeneration() {
        String key1 = cache.keyFor("ElevenLabs", "voice1", "npc1", "hello world", "v1");
        String key2 = cache.keyFor("ElevenLabs", "voice1", "npc1", "hello world", "v1");
        String key3 = cache.keyFor("ElevenLabs", "voice1", "npc1", "hello world", "v2");
        
        assertEquals(key1, key2); // Same inputs should generate same key
        assertNotEquals(key1, key3); // Different version should generate different key
    }
    
    @Test
    void testTextNormalization() {
        String text1 = "  Hello   World!  \n";
        String text2 = "hello world!";
        String text3 = "Hello\r\nWorld!";
        
        String normalized1 = AudioCache.normalizeText(text1);
        String normalized2 = AudioCache.normalizeText(text2);
        String normalized3 = AudioCache.normalizeText(text3);
        
        assertEquals("hello world!", normalized1);
        assertEquals("hello world!", normalized2);
        assertEquals("hello world!", normalized3);
    }
    
    @Test
    void testSingleFlight() throws Exception {
        AtomicInteger synthesisCount = new AtomicInteger(0);
        
        // Supplier that tracks how many times it's called
        java.util.function.Supplier<byte[]> synthesizer = () -> {
            synthesisCount.incrementAndGet();
            try {
                Thread.sleep(100); // Simulate synthesis time
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "synthesized audio".getBytes();
        };
        
        String key = "single-flight-test";
        
        // Start multiple concurrent requests for the same key
        CompletableFuture<Path> future1 = cache.getOrCompute(key, synthesizer);
        CompletableFuture<Path> future2 = cache.getOrCompute(key, synthesizer);
        CompletableFuture<Path> future3 = cache.getOrCompute(key, synthesizer);
        
        // Wait for all to complete
        Path result1 = future1.get();
        Path result2 = future2.get();
        Path result3 = future3.get();
        
        // All should return the same path
        assertEquals(result1, result2);
        assertEquals(result2, result3);
        
        // Synthesis should only happen once
        assertEquals(1, synthesisCount.get());
    }
    
    @Test
    void testCacheStats() {
        var stats = cache.getStats();
        assertEquals(0, stats.fileCount());
        assertEquals(0, stats.totalBytes());
        
        // Add some files
        cache.put("key1", "data1".getBytes(), "wav");
        cache.put("key2", "data2data2".getBytes(), "wav");
        
        stats = cache.getStats();
        assertEquals(2, stats.fileCount());
        assertTrue(stats.totalBytes() > 0);
    }
    
    @Test
    void testClearAll() {
        cache.put("key1", "data1".getBytes(), "wav");
        cache.put("key2", "data2".getBytes(), "wav");
        
        assertEquals(2, cache.getStats().fileCount());
        
        cache.clearAll();
        
        assertEquals(0, cache.getStats().fileCount());
        assertFalse(cache.get("key1").isPresent());
        assertFalse(cache.get("key2").isPresent());
    }
}