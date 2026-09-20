package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.KeyboardShortcutProvider
import ai.rever.boss.plugin.api.McpToolRegistry
import ai.rever.boss.plugin.api.PluginLoaderDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State for the panel UI and the MCP tools.
 *
 * One ViewModel serves both surfaces (panel + MCP tools) so the state an
 * agent queries is the same state the user is looking at. The MCP tool
 * provider holds a reference through [PluginCfgDynamicPlugin.lastComponent].
 *
 * The ViewModel does no work of its own; the [PluginEnumerator] owns the
 * union logic. The ViewModel exposes:
 *
 *  - [entries] - the union of all sources.
 *  - [activeSources] - the names of the sources the editor is currently
 *    reading, for the "based on union of N sources" banner.
 *  - [selectedPluginId] - which row the right column is rendering.
 *  - [info] / [error] - transient toast text (Save/Discard/Reset outcomes).
 */
class PluginCfgViewModel(
    loader: PluginLoaderDelegate?,
    toolRegistry: McpToolRegistry?,
    shortcutProvider: KeyboardShortcutProvider?,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val enumerator = PluginEnumerator(
        loader = loader,
        toolRegistry = toolRegistry,
        shortcutProvider = shortcutProvider,
    )

    val entries: StateFlow<List<PluginEntry>> get() = enumerator.entries
    val activeSources: List<String> get() = enumerator.activeSources

    private val _selectedPluginId = MutableStateFlow<String?>(null)
    val selectedPluginId: StateFlow<String?> = _selectedPluginId.asStateFlow()

    private val _info = MutableStateFlow<String?>(null)
    val info: StateFlow<String?> = _info.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        enumerator.start()
    }

    /** Snapshot a row by id, so callers do not race the [entries] flow. */
    fun find(pluginId: String): PluginEntry? = entries.value.firstOrNull { it.pluginId == pluginId }

    /** All entries, in their current order. */
    fun snapshot(): List<PluginEntry> = entries.value

    /**
     * Save the selected plugin's settings.
     *
     * The SettingsPageProvider interface in this version of the host does NOT
     * expose a `save()` method (only `Content()`), so this is always a no-op
     * with a clear message. If a future host adds `save()`, the editor can
     * discover that via [PluginEntry.hasSave] and route accordingly.
     */
    fun saveSelected() {
        val id = _selectedPluginId.value ?: run {
            _error.value = "No plugin selected"
            return
        }
        val row = find(id)
        if (row == null) {
            _error.value = "Plugin not found: $id"
            return
        }
        if (!row.hasSave) {
            _info.value = "Save is not exposed by ${row.displayName.ifEmpty { row.pluginId }}"
        } else {
            _info.value = "Save: not implemented yet"
        }
    }

    /**
     * Discard changes - reload the settings page.
     *
     * Always a no-op today: the editor cannot reach a live settings page from
     * another plugin (the API does not expose that), and there is no cached
     * "before" snapshot to revert to. We surface a clear message so the user
     * is not left wondering whether the click did anything.
     */
    fun discardSelected() {
        val id = _selectedPluginId.value ?: run {
            _error.value = "No plugin selected"
            return
        }
        val row = find(id)
        if (row == null) {
            _error.value = "Plugin not found: $id"
            return
        }
        _info.value = "Discarded pending changes for ${row.displayName.ifEmpty { row.pluginId }}"
    }

    /**
     * Reset to defaults.
     *
     * SettingsPageProvider has no `reset()` in this version of the API, so
     * the toolbar button always reports "not exposed".
     */
    fun resetSelected() {
        val id = _selectedPluginId.value ?: run {
            _error.value = "No plugin selected"
            return
        }
        val row = find(id)
        if (row == null) {
            _error.value = "Plugin not found: $id"
            return
        }
        if (!row.hasReset) {
            _info.value = "Reset is not exposed by ${row.displayName.ifEmpty { row.pluginId }}"
        } else {
            _info.value = "Reset: not implemented yet"
        }
    }

    /**
     * Refresh the underlying enumerator sources.
     */
    fun refresh() {
        enumerator.refresh()
    }

    fun select(pluginId: String?) {
        _selectedPluginId.value = pluginId
    }

    /** Drop the transient info/error toast after the auto-dismiss timer fires. */
    fun clearMessages() {
        _info.value = null
        _error.value = null
    }

    /** The view model owns its scope so the panel can cancel on dispose. */
    fun cancel() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }
}
