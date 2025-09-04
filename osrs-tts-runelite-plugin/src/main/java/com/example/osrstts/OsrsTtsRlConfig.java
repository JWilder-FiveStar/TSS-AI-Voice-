package com.example.osrstts;

import net.runelite.client.config.*;

@ConfigGroup("osrs-tts")
public interface OsrsTtsRlConfig extends Config {

    // --- Provider Selection ---
    enum Provider { Azure, ElevenLabs, Polly }

    @ConfigItem(
	    keyName = "provider",
	    name = "Provider",
	    description = "TTS provider to use (Azure / ElevenLabs / Polly)",
	    position = 0
    )
    default Provider provider() { return Provider.ElevenLabs; }

    // Master enable
    @ConfigItem(
	    keyName = "enabled",
	    name = "Enable TTS",
	    description = "Master enable/disable for TTS playback",
	    position = 1
    )
    default boolean enabled() { return true; }

    // --- Azure ---
    enum AzureRegion {
	eastus, eastus2, southcentralus, westus, westus2, westus3,
	centralus, northcentralus, canadacentral, brazilsouth,
	northeurope, westeurope, uksouth, francecentral,
	germanywestcentral, switzerlandnorth, norwayeast,
	eastasia, southeastasia, japaneast, koreacentral,
	australiaeast, australiasoutheast, uaenorth, southafricanorth
    }

    @ConfigItem(
	    keyName = "azureRegionSelect",
	    name = "Azure Region (Dropdown)",
	    description = "Preferred Azure Speech region (dropdown)",
	    position = 10
    )
    default AzureRegion azureRegionSelect() { return AzureRegion.eastus; }

    @ConfigItem(
	    keyName = "azureRegion",
	    name = "Azure Region (Custom)",
	    description = "Optional custom Azure region override (takes precedence if not blank)",
	    position = 11
    )
    default String azureRegion() { return ""; }

    @ConfigItem(
	    keyName = "azureKey",
	    name = "Azure Speech Key",
	    description = "Your Azure Cognitive Services Speech key",
	    secret = true,
	    position = 12
    )
    default String azureKey() { return ""; }

    // --- ElevenLabs ---
    @ConfigItem(
	    keyName = "elevenKey",
	    name = "ElevenLabs API Key",
	    description = "Your ElevenLabs API key",
	    secret = true,
	    position = 20
    )
    default String elevenKey() { return ""; }

    @ConfigItem(
	    keyName = "elevenModel",
	    name = "ElevenLabs Model",
	    description = "Model id (e.g. eleven_turbo_v2_5)",
	    position = 21
    )
    default String elevenModel() { return "eleven_turbo_v2_5"; }

    // Narrator
    @ConfigItem(
	    keyName = "narratorEnabled",
	    name = "Narrator Enabled",
	    description = "Narrate long-form texts like books, journals, scrolls",
	    position = 30
    )
    default boolean narratorEnabled() { return true; }

    @ConfigItem(
	    keyName = "narratorVoice",
	    name = "Narrator Voice",
	    description = "Voice used for narration (Azure short name or ElevenLabs 'Name (id)')",
	    position = 31
    )
    default String narratorVoice() { return "en-US-JennyNeural"; }

    // Player voice
    @ConfigItem(
	    keyName = "playerVoice",
	    name = "Player Voice",
	    description = "Voice used for your player character",
	    position = 40
    )
    default String playerVoice() { return "en-US-DavisNeural"; }
}
