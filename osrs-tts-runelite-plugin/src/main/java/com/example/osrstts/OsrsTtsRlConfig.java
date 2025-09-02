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
        Polly,
        ElevenLabs
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
            name = "Azure Region (custom)",
            description = "Custom Azure region text if needed (e.g., eastus, westus2)",
            section = azureSection,
            position = 1
    )
    default String azureRegion() { return "eastus"; }

    enum AzureRegion {
        eastus, eastus2, southcentralus, westus, westus2, westus3,
        centralus, northcentralus,
        canadacentral, brazilsouth,
        northeurope, westeurope, uksouth,
        francecentral, germanywestcentral, switzerlandnorth, norwayeast,
        eastasia, southeastasia, japaneast, koreacentral,
        australiaeast, australiasoutheast,
        uaenorth, southafricanorth
    }

    @ConfigItem(
            keyName = "azureRegionSelect",
            name = "Azure Region",
            description = "Select an Azure region",
            section = azureSection,
            position = 0
    )
    default AzureRegion azureRegionSelect() { return AzureRegion.eastus; }

    @ConfigItem(
            keyName = "testVoice",
            name = "Test Voice",
            description = "Click to test TTS with current settings (requires provider key)",
            section = azureSection
    )
    default boolean testVoice() {
        return false;
    }

    @ConfigSection(
            name = "ElevenLabs Settings",
            description = "ElevenLabs text-to-speech configuration",
            position = 2
    )
    String elevenSection = "eleven";

    @ConfigItem(
            keyName = "elevenKey",
            name = "ElevenLabs API Key",
            description = "Your ElevenLabs API key (saved locally)",
            section = elevenSection,
            secret = true
    )
    default String elevenKey() { return ""; }

    @ConfigItem(
            keyName = "elevenModel",
            name = "ElevenLabs Model",
            description = "Model id (e.g., eleven_turbo_v2_5)",
            section = elevenSection
    )
    default String elevenModel() { return "eleven_turbo_v2_5"; }

    @ConfigSection(
            name = "Voice Settings",
            description = "Voice and narration options",
            position = 3
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
            description = "Voice to use for narration (Azure short name or ElevenLabs 'Name (id)')",
            section = voiceSection
    )
    default String narratorVoice() { return "en-US-JennyNeural"; }

    @ConfigItem(
            keyName = "playerVoice",
            name = "Player Voice",
            description = "Voice to use for your own character (Azure short name or ElevenLabs 'Name (id)')",
            section = voiceSection
    )
    default String playerVoice() { return "en-US-DavisNeural"; }
}
