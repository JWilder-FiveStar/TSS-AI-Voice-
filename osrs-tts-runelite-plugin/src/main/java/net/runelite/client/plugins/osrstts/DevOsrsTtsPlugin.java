package net.runelite.client.plugins.osrstts;

import net.runelite.client.plugins.PluginDescriptor;

// Wrapper so RuneLite's classpath scanner finds this plugin during dev-run
@PluginDescriptor(
        name = "OSRS TTS (Dev)",
        description = "Text-to-Speech plugin for Old School RuneScape - Dev Run",
        tags = {"tts", "text-to-speech", "osrs"}
)
public class DevOsrsTtsPlugin extends com.example.osrstts.OsrsTtsPlugin {
}
