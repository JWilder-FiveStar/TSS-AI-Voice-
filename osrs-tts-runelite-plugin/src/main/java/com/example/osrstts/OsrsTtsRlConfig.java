package com.example.osrstts;

import net.runelite.client.config.*;

@ConfigGroup("osrs-tts")
public interface OsrsTtsRlConfig extends Config {

    @ConfigItem(
            keyName = "enabled",
            name = "Enable TTS",
            description = "Enable text-to-speech for NPCs and narration"
    )
    default boolean enabled() { return true; }

    @ConfigItem(
            keyName = "provider",
            name = "TTS Provider",
            description = "Choose your TTS service provider"
    )
    default Provider provider() { return Provider.Azure; }

    enum Provider {
        Azure,
        Polly
    }

    @ConfigSection(
            name = "Azure Speech Settings",
            description = "Azure Cognitive Services configuration",
            position = 1
    )
    String azureSection = "azure";

    @ConfigItem(
            keyName = "azureKey",
            name = "Azure Speech Key",
            description = "Your Azure Cognitive Services Speech key (saved locally)",
            section = azureSection,
            secret = true
    )
    default String azureKey() { return ""; }

    @ConfigItem(
            keyName = "azureRegion",
            name = "Azure Region",
            description = "Azure region (e.g., eastus, westus2)",
            section = azureSection
    )
    default String azureRegion() { return "eastus"; }

    @ConfigItem(
            keyName = "testVoice",
            name = "Test Voice",
            description = "Click to test TTS with current settings (requires Azure key)",
            section = azureSection
    )
    default boolean testVoice() {
        return false;
    }

    @ConfigSection(
            name = "Voice Settings",
            description = "Voice and narration options",
            position = 2
    )
    String voiceSection = "voice";

    @ConfigItem(
            keyName = "narratorEnabled",
            name = "Narrator for books/journals",
            description = "Enable narration for long texts like books and journals",
            section = voiceSection
    )
    default boolean narratorEnabled() { return true; }

    @ConfigItem(
            keyName = "narratorVoice",
            name = "Narrator Voice",
            description = "Voice to use for narration (Azure voice name)",
            section = voiceSection
    )
    default String narratorVoice() { return "en-US-JennyNeural"; }
}
