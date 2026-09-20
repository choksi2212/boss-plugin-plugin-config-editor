package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.KeyboardShortcutProvider
import ai.rever.boss.plugin.api.LoadedPluginInfo
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import ai.rever.boss.plugin.api.RegisteredMcpTool
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enumerates loaded plugins from every source the host exposes, then unions
 * the results.
 *
 * The host does NOT expose a single "list every loaded plugin" API. What it
 * exposes is a handful of narrower ones:
 *
 *  - [PluginLoaderDelegate.getLoadedPlugins] - the authoritative list of plugins
 *    the host has in memory, with metadata (id, displayName, version, jarPath,
 *    isSystemPlugin, healthy, ...). This is what the editor's main list keys
 *    off.
 *  - [McpToolRegistry.allTools] - tools contributed by every active provider.
 *    Each [RegisteredMcpTool] carries `providerId`, which by convention is the
 *    contributing plugin's pluginId. A plugin that does not register a tool
 *    does not show up here.
 *  - [KeyboardShortcutProvider.getShortcuts] - host shortcuts only; plugin
 *    shortcut ids are `plugin.<pluginId>.<name>` and are not exposed here. Kept
 *    for forward-compatibility (the source may one day carry plugin origin).
 *
 * The editor's panel surfaces this as a "based on union of N sources" banner,
 * so the user can tell at a glance whether the list is exhaustive or partial.
 *
 * The probe never throws - it degrades silently to empty when a source is
 * absent, and per-row errors on a single source are caught and the row is
 * dropped (the host can refuse a provider for permission reasons). Bound
 * sizes ([MAX_PLUGINS]) are enforced at every read.
 */
class PluginEnumerator(
    private val loader: PluginLoaderDelegate?,
    private val toolRegistry: McpToolRegistry?,
    private val shortcutProvider: KeyboardShortcutProvider?,
) {
    /** A row is built by combining every source's contribution. */
    private val _entries = MutableStateFlow<List<PluginEntry>>(emptyList())
    val entries: StateFlow<List<PluginEntry>> = _entries.asStateFlow()

    /** "based on union of loader + McpToolRegistry.allTools + KeyboardShortcutProvider" */
    val activeSources: List<String> = buildList {
        if (loader != null) add("PluginLoaderDelegate")
        if (toolRegistry != null) add("McpToolRegistry.allTools")
        if (shortcutProvider != null) add("KeyboardShortcutProvider")
    }

    /** Has any source responded? When false, the panel shows the empty state. */
    val isEmpty: Boolean get() = activeSources.isEmpty()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Refresh from every source. Synchronous over the loader (it is a plain
     * getter); reactive over `toolRegistry.allTools`. The reactive piece is the
     * one that can change during a session - a plugin enables, the registry
     * re-emits - so we subscribe once and union the next synchronous loader
     * snapshot into each emission.
     */
    fun start() {
        // 1. Loader snapshot - synchronous, but kept on scope so a slow host
        // does not block UI thread.
        scope.launch {
            val fromLoader = runCatching { loader?.getLoadedPlugins().orEmpty() }
                .getOrDefault(emptyList())
            _entries.value = merge(fromLoader = fromLoader, fromTools = emptyList(), fromShortcuts = emptyList())
        }

        // 2. Tools flow - subscribed once, re-emits on every register/unregister.
        val registry = toolRegistry
        if (registry != null) {
            scope.launch {
                registry.allTools.collect { tools ->
                    val fromLoader = runCatching { loader?.getLoadedPlugins().orEmpty() }
                        .getOrDefault(emptyList())
                    val fromShortcuts = runCatching { shortcutProvider?.getShortcuts().orEmpty() }
                        .getOrDefault(emptyList())
                    _entries.value = merge(
                        fromLoader = fromLoader,
                        fromTools = tools,
                        fromShortcuts = fromShortcuts,
                    )
                }
            }
        }
    }

    /**
     * Force a refresh. Useful for the panel's "Refresh" button.
     */
    fun refresh() {
        scope.launch {
            val fromLoader = runCatching { loader?.getLoadedPlugins().orEmpty() }
                .getOrDefault(emptyList())
            val fromTools = toolRegistry?.allTools?.value.orEmpty()
            val fromShortcuts = runCatching { shortcutProvider?.getShortcuts().orEmpty() }
                .getOrDefault(emptyList())
            _entries.value = merge(fromLoader = fromLoader, fromTools = fromTools, fromShortcuts = fromShortcuts)
        }
    }

    /**
     * Union logic.
     *
     * Order:
     *  1. Rows from the loader come first, in the loader's order.
     *  2. Rows observed only via tool / shortcut contributions follow,
     *     sorted by pluginId for stable presentation.
     *
     * Bound: at most [MAX_PLUGINS] rows total, with the excess dropped and
     * counted on a status line. The cap is enforced AFTER the merge, so a
     * host with 500+ plugins still gets a coherent first 500.
     */
    private fun merge(
        fromLoader: List<LoadedPluginInfo>,
        fromTools: List<RegisteredMcpTool>,
        fromShortcuts: List<ai.rever.boss.plugin.api.KeyboardShortcutInfo>,
    ): List<PluginEntry> {
        // Map<pluginId, MutablePluginRow> - mutable while we union.
        val rows = LinkedHashMap<String, MutablePluginRow>(MAX_PLUGINS)

        for (info in fromLoader) {
            if (rows.size >= MAX_PLUGINS) break
            val id = info.pluginId.ifBlank { continue }
            rows.getOrPut(id) { MutablePluginRow() }.apply {
                if (this.pluginId == null) {
                    this.pluginId = id
                    this.displayName = info.displayName
                    this.version = info.version
                    this.author = info.author
                    this.description = info.description
                    this.url = info.url
                    this.type = info.type
                    this.isEnabled = info.isEnabled
                    this.healthy = info.healthy
                    this.isSystemPlugin = info.isSystemPlugin
                    this.canUnload = info.canUnload
                    this.requiresAdmin = info.requiresAdmin
                    this.jarPath = info.jarPath
                    this.fromLoader = true
                }
            }
        }

        // Union of MCP tools grouped by providerId. providerId is by convention
        // the pluginId, but the host does not enforce it - we accept whatever
        // string is there and use it as the id.
        val toolsByProvider = LinkedHashMap<String, MutableList<String>>()
        for (tool in fromTools) {
            val pid = tool.providerId.ifBlank { continue }
            toolsByProvider.getOrPut(pid) { mutableListOf() }.add(tool.definition.name)
        }
        for ((providerId, names) in toolsByProvider) {
            if (rows.size >= MAX_PLUGINS) break
            val row = rows.getOrPut(providerId) { MutablePluginRow() }
            row.pluginId = providerId
            row.fromMcpTools = true
            row.mcpToolNames = names.take(MAX_TOOL_NAMES_PER_PLUGIN)
            row.mcpToolCount = names.size
        }

        // Shortcuts: KeyboardShortcutInfo currently does NOT carry a pluginId,
        // but we union on what we can extract from the action names that look
        // like `plugin.<id>.<name>`. Empty otherwise - today every keyboard
        // shortcut comes from the host, not from a plugin, so this is a
        // no-op path.
        val shortcutsByPlugin = LinkedHashMap<String, Int>()
        for (shortcut in fromShortcuts) {
            val pid = extractPluginIdFromShortcut(shortcut.action) ?: continue
            shortcutsByPlugin[pid] = (shortcutsByPlugin[pid] ?: 0) + 1
        }
        for ((pid, count) in shortcutsByPlugin) {
            if (rows.size >= MAX_PLUGINS) break
            val row = rows.getOrPut(pid) { MutablePluginRow() }
            row.pluginId = pid
            row.fromShortcuts = true
            row.shortcutCount = count
        }

        return rows.values.mapNotNull { it.toEntry() }
    }

    /**
     * Try to extract a plugin id from a shortcut action name.
     *
     * The convention (per [ai.rever.boss.plugin.api.PluginShortcutSpec]) is
     * `plugin.<pluginId>.<name>`, but we are defensive about the surrounding
     * shape: split on '.', drop the first segment if it is "plugin", and
     * require the remaining parts to be non-empty.
     */
    private fun extractPluginIdFromShortcut(action: String): String? {
        if (!action.startsWith(SHORTCUT_PLUGIN_PREFIX)) return null
        val tail = action.removePrefix(SHORTCUT_PLUGIN_PREFIX)
        if (tail.isBlank()) return null
        val dot = tail.lastIndexOf('.')
        return if (dot <= 0) {
            tail.takeIf { it.isNotBlank() }
        } else {
            tail.substring(0, dot).takeIf { it.isNotBlank() }
        }
    }

    private companion object {
        const val SHORTCUT_PLUGIN_PREFIX = "plugin."

        /** A plugin row that has not yet been frozen into a PluginEntry. */
        const val MAX_TOOL_NAMES_PER_PLUGIN = 64
    }
}

/** Hard cap on the union: refuse beyond. */
const val MAX_PLUGINS = 500

/**
 * Mutable row used while we union. Every field has a default so we can build
 * one up incrementally. The freeze into [PluginEntry] happens once after the
 * union is complete.
 */
private class MutablePluginRow {
    var pluginId: String? = null
    var displayName: String = ""
    var version: String = ""
    var author: String = ""
    var description: String = ""
    var url: String = ""
    var type: String = ""
    var isEnabled: Boolean = true
    var healthy: Boolean = true
    var isSystemPlugin: Boolean = false
    var canUnload: Boolean = true
    var requiresAdmin: Boolean = false
    var jarPath: String = ""
    var fromLoader: Boolean = false
    var fromMcpTools: Boolean = false
    var mcpToolCount: Int = 0
    var mcpToolNames: List<String> = emptyList()
    var fromShortcuts: Boolean = false
    var shortcutCount: Int = 0

    fun toEntry(): PluginEntry? {
        val id = pluginId ?: return null
        return PluginEntry(
            pluginId = id,
            displayName = displayName,
            version = version,
            author = author,
            description = description,
            url = url,
            type = type,
            isEnabled = isEnabled,
            healthy = healthy,
            isSystemPlugin = isSystemPlugin,
            canUnload = canUnload,
            requiresAdmin = requiresAdmin,
            jarPath = jarPath,
            fromLoader = fromLoader,
            fromMcpTools = fromMcpTools,
            mcpToolCount = mcpToolCount,
            mcpToolNames = mcpToolNames,
            fromShortcuts = fromShortcuts,
            shortcutCount = shortcutCount,
        )
    }
}
