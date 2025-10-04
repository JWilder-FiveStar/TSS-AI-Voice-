# Rune Echo

A RuneLite plugin that adds immersive voice narration to Old School RuneScape using AI-powered text-to-speech technology. The plugin reads quest dialogue, NPC overhead text, chat messages, books, scrolls, and more using ElevenLabs voices.

## Features

- **Quest Dialogue Narration**: Custom voice assignments for quest NPCs with quest-specific voice mappings
- **NPC Overhead Speech**: Reads overhead text from NPCs and players
- **Chat Message Reading**: Narrates game chat, private messages, clan chat, and more
- **Book & Scroll Reading**: Automatically detects and reads in-game books, scrolls, and interfaces
- **Character Voice Assignment**: Per-NPC voice selection with style tags (wise, ominous, regal, etc.)
- **Voice Runtime Support**: ElevenLabs text-to-speech integration
- **Configurable**: Fine-grained control over what gets narrated

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
  - [IntelliJ IDEA Setup](#intellij-idea-setup)
  - [Project Setup](#project-setup)
  - [RuneLite Configuration](#runelite-configuration)
- [Running in Development Mode](#running-in-development-mode)
- [Configuration](#configuration)
- [Quest Voice Mapping](#quest-voice-mapping)
- [Project Structure](#project-structure)
- [Development Workflow](#development-workflow)
- [Troubleshooting](#troubleshooting)

---

## Prerequisites

Before you begin, ensure you have the following installed:

1. **Java Development Kit (JDK) 11 or higher**
   - Download from [Adoptium](https://adoptium.net/) or [Oracle](https://www.oracle.com/java/technologies/downloads/)
   - Verify installation: `java -version`

2. **Git**
   - Download from [git-scm.com](https://git-scm.com/)
   - Verify installation: `git --version`

3. **ElevenLabs API Key**
   - Sign up at [ElevenLabs](https://elevenlabs.io/)
   - Obtain your API key from the dashboard

---

## Installation

### IntelliJ IDEA Setup

1. **Download IntelliJ IDEA**
   - Download the Community Edition (free) or Ultimate Edition from [JetBrains](https://www.jetbrains.com/idea/download/)
   - Run the installer and follow the setup wizard
   - Select default options unless you have specific preferences

2. **Launch IntelliJ IDEA**
   - On first launch, configure your JDK:
     - Go to **File → Project Structure → SDKs**
     - Click **+** and select **Add JDK**
     - Navigate to your JDK installation directory (e.g., `C:\Program Files\Eclipse Adoptium\jdk-11.0.x`)

3. **Install Recommended Plugins** (Optional but helpful)
   - Go to **File → Settings → Plugins**
   - Search for and install:
     - Lombok (if not already bundled)
     - Gradle (usually bundled)
     - JSON (usually bundled)

---

### Project Setup

1. **Clone the Repository**

   ```bash
   git clone https://github.com/JWilder-FiveStar/TSS-AI-Voice-.git
   cd TSS-AI-Voice-
   ```

2. **Open Project in IntelliJ IDEA**
   - Open IntelliJ IDEA
   - Click **File → Open**
   - Navigate to the cloned repository folder and select it
   - Click **OK**
   - IntelliJ will detect the Gradle project and import it automatically
   - Wait for Gradle sync to complete (may take a few minutes on first run)

3. **Configure ElevenLabs API Key**
   - Create or edit the configuration file:
     ```
     osrs-tts-runelite-plugin/osrs-tts-config.properties
     ```
   - Add your API key:
     ```properties
     elevenlabs.api.key=YOUR_API_KEY_HERE
     ```
   - **Important**: Do not commit this file to version control (it's in `.gitignore`)

4. **Build the Project**
   - Open the Gradle tool window: **View → Tool Windows → Gradle**
   - Expand **TSS-AI-Voice- → osrs-tts-runelite-plugin → Tasks → build**
   - Double-click **jar** to build the plugin
   - Or run from terminal:
     ```bash
     ./gradlew.bat :osrs-tts-runelite-plugin:jar
     ```

---

### RuneLite Configuration

#### Getting Jagex Launcher Credentials

To run RuneLite in development mode with your OSRS account, you need to extract credentials from the Jagex Launcher:

**Official RuneLite Documentation:**
- [RuneLite Development Guide - Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)

**Quick Summary:**
1. Launch OSRS through the Jagex Launcher
2. While the game is running, use the credential extraction tool (see RuneLite wiki)
3. Copy the credentials file to `~/.runelite/` directory
4. RuneLite dev client will use these credentials automatically

---

## Running in Development Mode

### Option 1: Using IntelliJ Run Configurations

1. **Create a Run Configuration**
   - Click **Run → Edit Configurations...**
   - Click **+** and select **Gradle**
   - Name it: `Run OSRS TTS Dev Client`
   - Gradle project: Select the root project
   - Tasks: `:dev-run:run`
   - Click **OK**

2. **Run the Configuration**
   - Select your new run configuration from the dropdown
   - Click the green **Run** button (or press **Shift+F10**)
   - RuneLite will launch with your plugin loaded

### Option 2: Using Gradle Tasks

1. **Open Gradle Tool Window**
   - **View → Tool Windows → Gradle**

2. **Run the Dev Client**
   - Expand **TSS-AI-Voice- → dev-run → Tasks → application**
   - Double-click **run**
   - RuneLite will launch with the plugin

### Option 3: Using Terminal Commands

```bash
# Build and run the dev client
./gradlew.bat :dev-run:run
```

### Debug Mode

To run in debug mode with breakpoints:

1. Set breakpoints in your code by clicking in the gutter next to line numbers
2. Click the **Debug** button (bug icon) instead of **Run**
3. RuneLite will pause at breakpoints, allowing you to inspect variables and step through code

---

## Configuration

The plugin provides extensive configuration options through its settings panel:

### Access Configuration Panel
- In RuneLite, click the **Plugin** icon in the sidebar
- Find **OSRS TTS** in the list
- Click the gear icon to open settings

### Key Settings

- **Master Enable/Disable**: Toggle entire plugin on/off
- **Quest Narration**: Enable/disable quest dialogue reading
- **Overhead Speech**: Control NPC/player overhead text narration
- **Chat Messages**: Configure which chat channels to narrate
- **Dialog/Interface**: Control book/scroll/interface reading
- **Voice Provider**: Select ElevenLabs or other providers
- **Debug Logging**: Enable detailed logs for troubleshooting

### Configuration File

Settings are stored in:
```
osrs-tts-runelite-plugin/osrs-tts-config.properties
```

---

## Quest Voice Mapping

Quest-specific NPC voices are defined in JSON files under:
```
osrs-tts-runelite-plugin/quest-voices/
```

### Example: Creating a Quest Voice Mapping

**File**: `quest-voices/my-quest.json`

```json
{
  "npcExact": {
    "Wise Old Man": "ElevenLabs:Antoni (ErXwobaYiN019PkySvjV)|style=wise",
    "Evil Wizard": "ElevenLabs:Brian (nPczCjzI2devNBz1zQrb)|style=ominous"
  },
  "npcRegex": {
    "(?i)guard.*": "ElevenLabs:Daniel (onwK4e9ZLuTAKqWW03F9)|style=stern"
  },
  "tags": {
    "wise": "ElevenLabs:Rachel (21m00Tcm4TlvDq8ikWAM)|style=wise",
    "villager": "ElevenLabs:Charlie (IKne3meq5aSn9XLyUdCD)|style=neutral"
  }
}
```

**Fields:**
- `npcExact`: Exact NPC name matches
- `npcRegex`: Regular expression patterns for NPC names
- `tags`: Fallback voices for categories
- Voice format: `ElevenLabs:VoiceName (VoiceID)|style=styleTag`

---

## Project Structure

```
TSS-AI-Voice-/
├── osrs-tts-runelite-plugin/       # Main plugin module
│   ├── src/main/java/
│   │   └── com/example/osrstts/
│   │       ├── OsrsTtsPlugin.java        # Main plugin class
│   │       ├── OsrsTtsConfig.java        # Configuration
│   │       ├── OsrsTtsConfigPanel.java   # UI panel
│   │       ├── dialog/
│   │       │   └── NarrationDetector.java # Dialog detection
│   │       └── voice/
│   │           └── VoiceRuntime.java      # TTS engine interface
│   ├── quest-voices/                # Quest voice mappings
│   │   ├── a-fairy-tale-part-i.json
│   │   ├── children-of-the-sun.json
│   │   └── ...
│   ├── build.gradle                 # Plugin build configuration
│   └── osrs-tts-config.properties   # Plugin configuration
├── dev-run/                         # Dev client runner
│   └── build.gradle
├── reference-runelite/              # RuneLite source reference
├── build.gradle                     # Root build config
├── settings.gradle                  # Gradle settings
└── README.md                        # This file
```

---

## Development Workflow

### Making Changes

1. **Edit Source Files**
   - Make changes to Java files in `osrs-tts-runelite-plugin/src/`
   - IntelliJ provides auto-completion, refactoring, and error detection

2. **Hot Reload (if supported)**
   - Some changes can be hot-reloaded without restarting
   - Most structural changes require a full restart

3. **Rebuild and Test**
   - Stop the running dev client (if any)
   - Rebuild: `./gradlew.bat :osrs-tts-runelite-plugin:jar`
   - Run dev client: `./gradlew.bat :dev-run:run`

### Adding New Quest Voices

1. Create a new JSON file in `quest-voices/` (e.g., `my-quest.json`)
2. Define NPC voice mappings (see [Quest Voice Mapping](#quest-voice-mapping))
3. Restart the plugin to load the new mappings

### Debugging Tips

- **Enable Debug Logging**: Add `-Dosrs.tts.debug=true` to VM arguments
- **Check Logs**: View RuneLite client logs in `~/.runelite/logs/client.log`
- **Breakpoints**: Use IntelliJ debugger to pause execution and inspect variables
- **Log Statements**: Add `log.info("message")` for custom debug output

### Building a Release JAR

```bash
./gradlew.bat :osrs-tts-runelite-plugin:jar
```

Output: `osrs-tts-runelite-plugin/build/libs/osrs-tts-runelite-plugin-1.0-SNAPSHOT.jar`

---

## Troubleshooting

### Common Issues

#### Plugin Not Loading
- **Check logs** for errors in `~/.runelite/logs/client.log`
- Verify `osrs-tts-config.properties` exists and has valid API key
- Rebuild the plugin: `./gradlew.bat clean :osrs-tts-runelite-plugin:jar`

#### No Audio Output
- Verify ElevenLabs API key is correct
- Check internet connection (API calls require network access)
- Enable debug logging to see API request/response details

#### Game Crash: `error_game_js5connect_outofdate`
- OSRS game was updated, but RuneLite client is outdated
- Update `reference-runelite/` to latest version:
  ```bash
  cd reference-runelite
  git pull origin master
  ```
- Rebuild and restart dev client

#### IntelliJ Can't Find JDK
- Go to **File → Project Structure → SDKs**
- Add your JDK installation path
- Ensure project SDK is set correctly

#### Gradle Sync Fails
- Check internet connection (Gradle downloads dependencies)
- Try: **File → Invalidate Caches / Restart**
- Clear Gradle cache: Delete `~/.gradle/caches/` and re-sync

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes and test thoroughly
4. Commit with clear messages: `git commit -m "Add feature X"`
5. Push to your fork: `git push origin feature/my-feature`
6. Open a Pull Request

---

## License

[Your License Here - e.g., MIT, GPL, etc.]

---

## Links & Resources

- [RuneLite Official Site](https://runelite.net/)
- [RuneLite Wiki - Development](https://github.com/runelite/runelite/wiki)
- [RuneLite Wiki - Jagex Accounts](https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts)
- [ElevenLabs](https://elevenlabs.io/)
- [IntelliJ IDEA](https://www.jetbrains.com/idea/)

---

## Acknowledgments

- RuneLite team for the excellent plugin framework
- ElevenLabs for high-quality TTS voices
- OSRS community for testing and feedback

---

**Enjoy immersive voice narration in Old School RuneScape!**
