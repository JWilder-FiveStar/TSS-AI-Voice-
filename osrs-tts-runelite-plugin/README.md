# OSRS TTS RuneLite Plugin

Voice-over for OSRS quests, NPC dialogue, and books/journals with intelligent voice switching and a narrator mode.

## Features
- NPC chat TTS with per-NPC voice selection (JSON mapping + heuristics)
- Book/Journal/Scroll narrator (auto-detects long visible text)
- Azure Speech (primary) and Amazon Polly (fallback), unified WAV playback
- Disk cache for synthesized audio
- In-plugin settings panel: provider, Azure key/region, test voice

## Requirements
- Java 11+
- RuneLite client
- Your own TTS credentials (BYO key)

## Build
This module is Maven-based. From `osrs-tts-runelite-plugin/`:

```bash
mvn -q -e -DskipTests package
```

The jar will be in `target/`.

## Install into RuneLite
1) Locate your RuneLite plugins folder:
- Windows: %USERPROFILE%\.runelite\plugins
- macOS: ~/Library/Application Support/RuneLite/plugins
- Linux: ~/.runelite/plugins

2) Copy the built jar (e.g., `target/osrs-tts-runelite-plugin-1.0-SNAPSHOT.jar`) into that folder.

3) Start RuneLite, open the plugin list, enable “Old School RuneScape TTS”.

## Configure (BYO key)
Open the plugin panel:
- Provider: Azure or Polly
- Azure: paste your Speech key and region (e.g., eastus)
- Click Save, then Test Voice

Environment variables are also supported (preferred for dev):
- AZURE_SPEECH_KEY, AZURE_SPEECH_REGION

## Jagex Accounts (dev login)
If your account is converted to a Jagex Account and you need local dev login:
1. Ensure RuneLite launcher v2.6.3+.
2. Run the launcher with configure mode:
   - Windows: “RuneLite (configure)” from Start menu
   - macOS/Linux: run the launcher with `--configure` (e.g. `/Applications/RuneLite.app/Contents/MacOS/RuneLite --configure`).
3. In the dialog, set Client arguments: `--insecure-write-credentials` and Save.
4. Launch RuneLite via the Jagex launcher once; it will write `.runelite/credentials.properties`.
   - DO NOT share this file; it allows login without your password.
5. Launch the RuneLite client from your IDE; it will use the saved credentials.
6. When done, delete `credentials.properties`. To invalidate sessions, use “End sessions” in account settings on runescape.com.

## Notes
- Azure output format defaults to WAV for built-in playback; Polly is converted to WAV automatically.
- Audio cache lives in `.tts-cache/`. You can clear it anytime.
- Some voices/styles may not be supported by all Azure voices; fallback is applied automatically.

## Contributing
PRs welcome for more NPC mappings, better widget detection, and providers.

## License
MIT