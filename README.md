# BOSS Plugin Config Editor

Centralized panel for editing every loaded plugin's settings in one view - the first plugin-config editor for BOSS.

## What it does

BOSS plugins each ship their own settings UI by registering a `SettingsPageProvider`. The host surfaces them under a "Plugins" divider in its Settings window, but there is no single place that lists every plugin's settings together, and there is no way to script a setting change without first knowing which plugin owns it.

The Plugin Config Editor fills that gap with two surfaces:

- A left-bottom sidebar panel that lists every loaded plugin it can observe, grouped by id. Each row shows display name, plugin id, version, and three small badges (MCP tools contributed, keyboard shortcuts contributed, healthy / disabled / system status).
- An MCP tool provider exposing four tools - `plugin_config_list`, `plugin_config_export`, `plugin_config_set`, `plugin_config_get` - so an in-terminal agent can ask the same question the user is looking at.

### Sources

The host does not expose a single "list every loaded plugin" API. The editor unions three sources it does expose:

- `PluginLoaderDelegate.getLoadedPlugins()` - the authoritative list. Each row's metadata (id, display name, version, jar path, system / admin flags, healthy state) comes from here.
- `McpToolRegistry.allTools` - tools contributed by every active provider. The `providerId` of each tool is the contributing plugin's id by convention. A plugin that does not register a tool does not show up here.
- `KeyboardShortcutProvider.getShortcuts()` - shortcuts whose action name matches the `plugin.<id>.<name>` convention.

The panel banner names the active sources so the user can tell at a glance whether the list is exhaustive or partial.

### Per-plugin toolbar

The detail column carries a Save / Discard / Reset toolbar for every selected plugin. The host's `SettingsPageProvider` interface in this api version only exposes `Content()` - no `save()`, no `reset()`, no `get()`, no `set()` - so the buttons always report "not exposed" today. They are wired through `PluginEntry` flags so a future host which does expose them can light the buttons up without a UI change.

### MCP tools

| Tool | Read/Write | Purpose |
|---|---|---|
| `plugin_config_list` | read-only | List every loaded plugin this editor can see, with sources observed. |
| `plugin_config_export` | read-only | One JSON object keyed by pluginId, capped at 64 KiB. |
| `plugin_config_set` | mutating | Always reports "not exposed by this host's api"; the schema is wired through so a future api can route to it. |
| `plugin_config_get` | read-only | Read a single field of one plugin. Recognised keys mirror `PluginEntry`. |

`plugin_config_get` and `plugin_config_list` share state with the panel, so an agent and a user looking at the panel see the same answer.

## Hard limits

- `MAX_PLUGINS = 500` - the editor truncates beyond this and reports it.
- `MAX_SETTINGS_JSON_BYTES = 64 KiB` - the export payload is capped.
- `MAX_KEY_LENGTH = 256` - keys beyond this are refused.
- `MAX_VALUE_LENGTH = 16 KiB` - values beyond this are refused.

## Requirements

- BOSS >= 9.5.0, boss-plugin-api >= 1.0.93
- `PluginLoaderDelegate` and `McpToolRegistry` from the host. Without `PluginLoaderDelegate`, the list is empty and the banner explains why.

## Build

```bash
./gradlew buildPluginJar
cp build/libs/boss-plugin-plugin-config-editor-*.jar ~/.boss/plugins/
```

See [AGENTS.md](AGENTS.md) for architecture and conventions.

## License

Proprietary - choksi2212
