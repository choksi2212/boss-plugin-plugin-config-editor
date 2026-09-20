# AGENTS.md

## Project Overview

**Plugin Config Editor (Dynamic)** (`ai.rever.boss.plugin.dynamic.cfg`) is a dynamic plugin for the BOSS desktop application.

Centralized panel listing every loaded plugin and surfacing its MCP tools, shortcuts and metadata - one view of what is loaded, with MCP tools to export the list and read individual fields.

- **Plugin ID**: `ai.rever.boss.plugin.dynamic.cfg`
- **Main Class**: `ai.rever.boss.plugin.dynamic.cfg.PluginCfgDynamicPlugin`
- **API Version**: 1.0.93

## Essential Commands

```bash
./gradlew buildPluginJar    # Build plugin JAR (output: build/libs/)
./gradlew build              # Full build (depends on buildPluginJar)
./gradlew processResources   # Process resources (syncs version)
```

## Workflow Rules

- Do NOT run the BOSS application to test. The user will test manually.
- After building, copy JAR to `~/.boss/plugins/` for local testing.

## Architecture

### Plugin Structure
```
src/main/kotlin/   -> Plugin source code (package: ai.rever.boss.plugin.dynamic.cfg)
src/main/resources/META-INF/boss-plugin/plugin.json -> Plugin manifest
build.gradle.kts   -> Build config + version (single source of truth)
```

### Key Patterns
- Entry point: `DynamicPlugin` interface with `register(context)` and `dispose()`
- UI: `PanelComponentWithUI` with `@Composable Content()`
- State: ViewModel pattern with `StateFlow`
- Providers from `PluginContext`: `getPluginAPI(PluginLoaderDelegate)`, `mcpToolRegistry`, `keyboardShortcutProvider`
- Null-safe provider access: providers may be null, UI must handle gracefully

### Enumeration sources

The host does NOT expose a single "list every loaded plugin" API. The editor composes from three narrower sources:

- `PluginLoaderDelegate.getLoadedPlugins()` (via `context.getPluginAPI(PluginLoaderDelegate::class.java)`)
- `McpToolRegistry.allTools` (via `context.mcpToolRegistry`)
- `KeyboardShortcutProvider.getShortcuts()` (via `context.keyboardShortcutProvider`)

The panel banner names the active sources so the user can tell at a glance whether the list is exhaustive or partial.

### Settings exposure

`SettingsPageProvider` in this api version exposes only `Content()` - no `save()`, `reset()`, `get()`, or `set()`. The editor's per-plugin toolbar always reports "not exposed" today. The schema is wired through `PluginEntry.hasSave`/`hasReset`/`hasGet`/`hasSet` so a future host can light the buttons up without a UI change.

### Dependencies
- **boss-plugin-api**: compileOnly (provided by host app at runtime)
- **Compose Desktop**: UI framework
- **Decompose**: Navigation and component lifecycle
- **Coroutines**: Async operations
- **kotlinx-serialization-json**: JSON payloads for MCP tools

## Version Management

**`build.gradle.kts` is the single source of truth for version.**

The `processResources` task automatically syncs the version into `plugin.json` at build time. Never manually edit the version in `plugin.json` - only change it in `build.gradle.kts`.

## Hard Limits

- `MAX_PLUGINS = 500` - truncate; refuse beyond.
- `MAX_SETTINGS_JSON_BYTES = 64 KiB` - export payload cap.
- `MAX_KEY_LENGTH = 256` - key cap.
- `MAX_VALUE_LENGTH = 16 KiB` - value cap.

## Code Quality

- Use Compose Multiplatform APIs (not Android-specific)
- All Kotlin files must end with a newline
- Handle null providers gracefully - show fallback UI, never crash
- Spaced hyphens (` - `) in prose, never em-dashes (U+2014)

## CI/CD

- Pushes to `main` trigger the Release workflow defined in `.github/workflows/build.yml`, which delegates to the shared workflow in `risa-labs-inc/BossConsole-Releases`.
- Pull requests trigger the Tests workflow in `.github/workflows/test.yml`, which compiles and assembles the plugin jar.
- Both workflows need the standard permissions block; `build.yml` adds `permissions: contents: write` for the version-bump commit the release workflow pushes back.
