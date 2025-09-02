# OSRS TTS System Implementation Guide

## Overview

This implementation provides a comprehensive Text-to-Speech system for RuneLite with the following key features:

- **Deterministic Voice Assignment**: NPCs get persistent voices that remain consistent across sessions
- **ElevenLabs-First**: Prioritizes ElevenLabs voices with fallback to Azure/Polly
- **Gender Correctness**: Intelligent gender-based voice selection with configurable guardrails
- **Forever Cache**: Generated audio is cached indefinitely for consistent playback
- **Cost Awareness**: Tracks character usage and provides monthly cost estimates
- **Comprehensive UI**: Basic and advanced settings with per-NPC voice overrides

## Architecture

### Core Components

1. **VoiceAssignment**: Records that store persistent voice assignments
2. **NpcMetadata**: Service providing NPC information including gender and tags
3. **VoiceSelectionPipeline**: Orchestrates the voice selection process
4. **AudioCache**: Enhanced caching with single-flight deduplication
5. **UsageTracker**: Monitors character consumption for cost estimation

### Data Flow

```
NPC Dialogue → VoiceSelectionPipeline → Check Persistent Assignment
                     ↓ (if none exists)
              NpcMetadataService → Apply Gender Guardrails → VoiceSelector
                     ↓
              Create Assignment → Cache Audio → Play Audio → Track Usage
```

## Usage

### Basic Integration

```java
// Initialize the enhanced VoiceRuntime
VoiceRuntime runtime = new VoiceRuntime(config);

// Speak NPC dialogue with NPC ID (preferred)
runtime.speakNpc(1234, "Guard", "Halt! Who goes there?", Set.of("guard", "male"));

// Speak NPC dialogue with name only (fallback)
runtime.speakNpc("Wise Old Man", "Welcome, adventurer!", Set.of("wizard", "male"));
```

### UI Integration

```java
// Basic settings panel
SettingsPanel settingsPanel = new SettingsPanel(
    enabled, provider, volume, usageTracker,
    this::testVoice, this::openNpcOverride,
    this::setTtsEnabled, this::setProvider, this::setVolume,
    this::openAdvancedSettings
);

// Advanced settings flyout
AdvancedSettingsFlyout flyout = new AdvancedSettingsFlyout(parentWindow);
flyout.showRightOf(settingsPanel);

// NPC voice override dialog
NpcOverrideDialog.showForNpc(parentWindow, npcName, npcId,
    choice -> runtime.getPipeline().assignVoiceManually(npcId, npcName, 
        choice.provider(), choice.voiceId(), choice.voiceLabel()),
    () -> runtime.getPipeline().clearVoiceAssignment(npcId, npcName)
);
```

### Configuration

Voice mappings are defined in `resources/osrs-voices.json`:

```json
{
  "npcExact": {
    "Wise Old Man": "ElevenLabs:Antoni (ErXwobaYiN019PkySvjV)"
  },
  "npcRegex": {
    "(?i)^King ": "ElevenLabs:Adam (pNInz6obpgDQGcFmaJgB)"
  },
  "tags": {
    "elf": "ElevenLabs:Elli (MF3mGyEYCl7XYWbV9V6O)",
    "dwarf": "ElevenLabs:Arnold (VR6AewLTigWG4xSOukaG)",
    "male": "ElevenLabs:Adam (pNInz6obpgDQGcFmaJgB)",
    "female": "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)"
  },
  "npcs": {
    "wise-old-man": {
      "gender": "male",
      "tags": ["wizard", "old", "wise"]
    }
  }
}
```

## Features

### Voice Assignment Persistence

- Voice assignments are stored in `voice-assignments.json`
- Auto-assignments (first encounter) vs user-assignments (manual override)
- Assignments persist across sessions and plugin updates

### Gender Guardrails

Three strictness levels:
- **STRICT**: Never allow gender mismatches
- **PREFER**: Prefer gender matches but allow mismatches if needed (default)
- **OFF**: Ignore gender completely

### Caching System

- **Forever Cache**: Audio files cached indefinitely unless manually cleared
- **Single-Flight**: Prevents duplicate synthesis requests
- **Version-Based**: Cache invalidation when mappings change
- **Compression**: SHA-256 based cache keys for efficient storage

### Usage Tracking

- Tracks characters sent to TTS services
- Monthly rolling totals
- ElevenLabs tier estimates (10k/30k/100k/500k characters)
- Cost awareness UI with progress bars

### UI Components

#### Basic Settings Panel
- Enable/disable TTS
- Provider selection (ElevenLabs/Azure/Polly)
- Master volume control
- Voice testing
- NPC override access
- Usage meter
- Advanced settings access

#### Advanced Settings Flyout
- **Voices & Tags**: Gender guardrails, default voices, tag mappings
- **Provider & API**: API keys, model settings, connection testing
- **Caching**: Cache management, version control, statistics
- **Safety & Limits**: Character budgets, busy chat handling, hotkeys
- **Import/Export**: Configuration backup and sharing

#### NPC Override Dialog
- Per-NPC voice assignment
- Provider switching
- Voice search and filtering
- Voice testing
- Assignment clearing

## File Structure

```
src/main/java/com/example/osrstts/
├── cache/
│   └── AudioCache.java               # Enhanced caching with single-flight
├── npc/
│   ├── NpcMetadata.java             # NPC information record
│   └── NpcMetadataService.java      # NPC metadata and gender resolution
├── tts/
│   ├── ElevenLabsTtsClient.java     # Enhanced with listVoices()
│   └── ElevenVoice.java             # ElevenLabs voice representation
├── ui/
│   ├── SettingsPanel.java           # Basic TTS settings
│   ├── AdvancedSettingsFlyout.java  # Advanced configuration dialog
│   ├── NpcOverrideDialog.java       # Per-NPC voice assignment
│   ├── VoicesTagsPanel.java         # Voice and tag configuration
│   ├── ProviderApiPanel.java        # API settings and credentials
│   ├── CachingPanel.java            # Cache management
│   ├── SafetyPanel.java             # Usage limits and safety
│   └── ImportExportPanel.java       # Configuration backup/restore
├── usage/
│   └── UsageTracker.java            # Character usage and cost tracking
└── voice/
    ├── VoiceAssignment.java         # Voice assignment record
    ├── VoiceAssignmentStore.java    # JSON persistence
    ├── VoiceAssignmentService.java  # Assignment management
    ├── VoiceSelectionPipeline.java  # Main selection orchestrator
    └── VoiceRuntime.java            # Enhanced runtime integration
```

## Testing

Comprehensive test suite covering:
- Voice assignment persistence and service operations
- Audio cache functionality with single-flight verification
- Integration testing of the complete pipeline
- Gender guardrail verification
- NPC metadata generation

Run tests with: `./gradlew test`

## Future Enhancements

1. **Real-time Voice Preview**: Live voice testing during selection
2. **Bulk Voice Assignment**: Assign voices to multiple NPCs at once  
3. **Voice Similarity Matching**: Automatic similar voice suggestions
4. **Regional Voice Variants**: Location-based accent selection
5. **Emotional Context**: Dynamic style based on dialogue content
6. **Voice Sharing**: Community voice assignment sharing
7. **Advanced Filtering**: Complex voice search and filtering options

## Troubleshooting

### Common Issues

1. **Voices not persisting**: Check file permissions on config directory
2. **Cache not working**: Verify cache directory is writable
3. **ElevenLabs connection**: Validate API key and network connectivity
4. **Gender mismatches**: Adjust gender strictness settings
5. **High usage**: Check usage tracker and set appropriate limits

### Debug Mode

Enable debug logging with system property:
```
-Dosrs.tts.debug=true
```

This provides detailed voice selection and synthesis logging.