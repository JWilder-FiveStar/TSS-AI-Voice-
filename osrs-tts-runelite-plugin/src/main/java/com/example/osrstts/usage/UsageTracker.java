package com.example.osrstts.usage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class UsageTracker {
    private static final ObjectMapper M = new ObjectMapper();
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final Path file;
    private final Map<String, Integer> byMonth = new HashMap<>();

    public UsageTracker() {
        Path dir = Paths.get("config", "osrs-tts");
        try { Files.createDirectories(dir); } catch (Exception ignored) {}
        this.file = dir.resolve("usage.json");
        load();
    }

    public synchronized void addCharacters(int count) {
        String key = LocalDate.now().format(FMT);
        byMonth.put(key, getThisMonth() + Math.max(0, count));
        save();
    }

    public synchronized int getThisMonth() {
        String key = LocalDate.now().format(FMT);
        return byMonth.getOrDefault(key, 0);
    }

    public synchronized String estimateForElevenLabs() {
        int n = getThisMonth();
        // Simple guidance for common tiers
        if (n < 10_000) return n + " chars — under 10k free tier";
        if (n < 30_000) return n + " chars — under 30k hobby";
        if (n < 100_000) return n + " chars — under 100k creator";
        if (n < 500_000) return n + " chars — under 500k pro";
        return n + " chars — 500k+ (enterprise)";
    }

    private void load() {
        try {
            if (Files.exists(file)) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                Map<String,Integer> m = M.readValue(json, new TypeReference<Map<String,Integer>>(){});
                byMonth.clear(); if (m != null) byMonth.putAll(m);
            }
        } catch (Exception ignored) {}
    }

    private void save() {
        try {
            Files.writeString(file, M.writerWithDefaultPrettyPrinter().writeValueAsString(byMonth), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
