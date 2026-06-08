# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an **NBA Live Score IntelliJ IDEA Plugin** that allows users to view NBA game scores, rankings, playoff brackets, and interact with an AI assistant directly within their IDE. It's written in Kotlin and uses the IntelliJ Platform Plugin SDK.

## Key Features

1. **Real-time Scores** - Display NBA games with live scores
2. **Rankings** - Show Eastern and Western conference standings
3. **Playoff Bracket** - Visualize playoff matchups
4. **AI Assistant** - Chat with an AI about NBA data
5. **Game Details** - View detailed game information
6. **Auto-refresh** - Support for automatic data refresh

## Technology Stack

- **Language**: Kotlin
- **Build System**: Gradle (Kotlin DSL)
- **IntelliJ Platform**: 2024.2+
- **Libraries**: OkHttp (network), Gson (JSON), Coroutines (async)
- **APIs**: ESPN public API for NBA data

## Project Structure

```
nba-score-plugin/
├── build.gradle.kts       # Gradle build configuration
├── settings.gradle.kts    # Gradle settings
├── src/main/
│   ├── kotlin/com/caro/nba/
│   │   ├── model/         # Data models (NBAGame, NBAStandings, etc.)
│   │   ├── service/       # Data service classes
│   │   ├── ui/            # UI components
│   │   └── *.kt           # Main plugin classes
│   └── resources/
│       └── META-INF/
│           └── plugin.xml # Plugin configuration
└── gradle/                # Gradle wrapper files
```

## Architecture Overview

### Data Flow

1. **UI Components**: `NBAScorePanel`, `StandingsPanel`, `PlayoffBracketPanel`, `AIAssistantPanel`
2. **Services**: `NBADataService`, `StandingsService`, `GameDetailService`
3. **Models**: `NBAGame`, `NBAStandings`, `TeamStanding`, `PlayByPlay`
4. **Tool Window**: `NBAScoreToolWindowFactory` creates the main tool window

### Key Classes

| Class | Responsibility |
|-------|----------------|
| `NBAScorePanel` | Main UI with tabs for scores, rankings, playoffs, and AI |
| `StandingsPanel` | Displays conference standings with color coding |
| `PlayoffBracketPanel` | Visualizes playoff matchups |
| `AIAssistantPanel` | AI chat interface with custom API support |
| `NBADataService` | Fetches game data from ESPN API |
| `NBASettingsState` | Persists AI assistant settings |
| `NBAScoreService` | Plugin service management |

## Common Development Tasks

### Building the Plugin

```bash
./gradlew buildPlugin
```

The built plugin will be located at `build/distributions/nba-score-plugin-*.zip`.

### Running the Plugin in Development

```bash
./gradlew runIde
```

This launches a new IDE instance with the plugin installed.

### Checking the Version

The plugin version is managed in `build.gradle.kts` (look for `version = "x.x.x"`) and in `src/main/resources/META-INF/plugin.xml` (look for `<change-notes>`).

## Plugin Configuration

The main plugin configuration file is `src/main/resources/META-INF/plugin.xml`, which defines:
- Plugin ID, name, vendor
- Dependencies on IntelliJ Platform
- Actions and tool windows
- Application services and settings

## Data Sources

- **NBA Scores & Standings**: ESPN public API (no API key required)
- **AI Assistant**: User-configurable API (default: Zhipu GLM-4.7-Flash)

## UI/UX Considerations

- All UI components use **JBColor** and **JBUI** for consistent theme support
- The plugin is **right-aligned** in the IDE toolbar
- Colors are used to indicate status: green (playoff spots), orange (play-in), gray (eliminated)
- The plugin supports **Chinese** (primary) and English (fallback)

## Dependencies Management

Dependencies are declared in `build.gradle.kts` under the `dependencies` block. Key dependencies:
- `intellijPlatform` - IntelliJ IDEA Community 2024.3
- `okhttp:4.12.0` - HTTP client
- `gson:2.10.1` - JSON parsing

## Important Notes

1. **Plugin Version**: The plugin is on version 4.x.y as of June 2026
2. **Compatibility**: Supports IDEA 2024.2 and newer
3. **AI Assistant**: Requires user configuration before use (API Key, etc.)
4. **Git History**: The repository contains several iterations of features (playoff clinched markers, real playoff data, AI assistant)

## How to Add a New Feature

1. **Define Model** (if needed): Add to `src/main/kotlin/com/caro/nba/model/`
2. **Create Service** (if needed): Add to `src/main/kotlin/com/caro/nba/service/`
3. **Build UI**: Create or modify a panel in `src/main/kotlin/com/caro/nba/`
4. **Integrate**: Hook up to `NBAScorePanel` tabs
5. **Test**: Run with `./gradlew runIde`

## Build Configuration

Build settings are in `build.gradle.kts`. Key sections:
- `intellijPlatform` - Configures the IntelliJ platform version
- `tasks.withType<RunIdeTask>` - Configures JVM arguments for `runIde` task
- `kotlin.jvmToolchain(17)` - Sets JDK 17 for Kotlin