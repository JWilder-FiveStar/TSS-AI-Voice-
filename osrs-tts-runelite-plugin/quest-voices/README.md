# Quest NPC Voice Mapping

Place one JSON file per quest (or group) in this directory. All `*.json` files here are auto-loaded after the built-in mappings and `quest-npc-voices.json`.

## JSON Structure
```
{
  "npcExact": {
    "NPC Name": "Provider:Voice (id)|style=descriptor",
    "Another NPC": "ElevenLabs:Adam (pNInz6obpgDQGcFmaJgB)|style=heroic"
  },
  "npcRegex": {
    "(?i)pattern.*": "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)|style=regal"
  },
  "tags": {
    "customtag": "ElevenLabs:Antoni (ErXwobaYiN019PkySvjV)|style=story"
  }
}
```
All sections are optional. Duplicate keys later in load order override earlier definitions.

## Guidelines
- Prefer canonical display names as they appear in chat dialogues.
- Reuse styles (e.g., `regal`, `arcane`, `gruff`, `scholarly`, `ethereal`, `booming`, `fae`, `grim`, `sinister`, `devout`).
- Keep voice diversity: avoid overusing Adam/Rachel—use Antoni, Josh, Arnold, Bella, Elli, Dorothy, Sam, etc.
- For undead/vampiric/demonic select Dorothy (gothic), Arnold (deep), or custom if added.
- For children / small creatures consider Bella or future added light voices.

## Suggested Workflow
1. Create / edit quest file (e.g., `cook's-assistant.json`).
2. Launch client; interact with quest NPC; verify unmapped voice then confirm override works after reload.
3. Commit incremental batches to keep diffs reviewable.

## Roadmap
- Add per-quest tagging for analytics.
- Add script to detect unmapped high-frequency quest NPCs.
- Potential future: dynamic stylistic shift mid-quest via tag injection.
