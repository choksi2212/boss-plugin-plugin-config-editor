package ai.rever.boss.plugin.dynamic.cfg

/**
 * A single plugin as the editor sees it.
 *
 * One row per pluginId. The editor does not read the host's settings-page
 * registry because the host does not expose one ([PluginContext] only exposes
 * `registerSettingsPage` / `unregisterSettingsPage`), so [hasSettingsPage] is
 * reported as false for every plugin today; that field exists so a future
 * host which does expose a settings-page registry can light it up without a
 * schema change.
 *
 * Sources are recorded on the row so the union banner at the top of the
 * panel can name them ("based on union of N sources"). They are flags, not
 * paths, because every entry has at most one of each.
 */
data class PluginEntry(
    val pluginId: String,
    val displayName: String,
    val version: String,
    val author: String = "",
    val description: String = "",
    val url: String = "",
    val type: String = "",
    val isEnabled: Boolean = true,
    val healthy: Boolean = true,
    val isSystemPlugin: Boolean = false,
    val canUnload: Boolean = true,
    val requiresAdmin: Boolean = false,
    val jarPath: String = "",
    /** True when this row came from [ai.rever.boss.plugin.api.PluginLoaderDelegate.getLoadedPlugins]. */
    val fromLoader: Boolean = false,
    /** True when at least one MCP tool with this provider id was observed. */
    val fromMcpTools: Boolean = false,
    /** Number of MCP tools contributed by this plugin id, across providers. */
    val mcpToolCount: Int = 0,
    /** Tool names contributed by this plugin id (small, bounded by [MAX_PLUGINS] filtering). */
    val mcpToolNames: List<String> = emptyList(),
    /** True when at least one keyboard shortcut actionId matched `plugin.<pluginId>.<name>`. */
    val fromShortcuts: Boolean = false,
    /** Number of shortcuts observed under this plugin id. */
    val shortcutCount: Int = 0,
    /** Placeholder for a future host that exposes the settings-page registry. */
    val hasSettingsPage: Boolean = false,
    /** Placeholder for a future host that exposes [SettingsPageProvider.save]. */
    val hasSave: Boolean = false,
    /** Placeholder for a future host that exposes [SettingsPageProvider.reset]. */
    val hasReset: Boolean = false,
    /** Placeholder for a future host that exposes [SettingsPageProvider.get]. */
    val hasGet: Boolean = false,
    /** Placeholder for a future host that exposes [SettingsPageProvider.set]. */
    val hasSet: Boolean = false,
)

/**
 * Stable identity for the "current selection" so a recomposition that drops a
 * row does not silently clear the user's pick. Two rows with the same
 * pluginId share a key.
 */
internal fun PluginEntry.key(): String = pluginId
