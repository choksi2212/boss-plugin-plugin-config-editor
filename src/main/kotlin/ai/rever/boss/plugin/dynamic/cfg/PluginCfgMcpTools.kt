package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.McpToolArgs
import ai.rever.boss.plugin.api.McpToolDefinition
import ai.rever.boss.plugin.api.McpToolHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.McpToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * MCP tools contributed by the Plugin Config Editor.
 *
 * Four tools - three read-only, one read+write gated on the host exposing
 * `SettingsPageProvider.set` (it does not in this api version, so the write
 * tool always reports "not exposed"). The same [PluginCfgViewModel] the
 * panel renders drives every tool, so an agent and a user looking at the
 * panel see the same answer.
 *
 * Hard limits:
 *
 *  - [MAX_KEY_LENGTH] = 256 chars - rejects oversized keys without trying.
 *  - [MAX_VALUE_LENGTH] = 16 KiB - rejects oversized values before they
 *    touch any storage.
 *  - [MAX_SETTINGS_JSON_BYTES] = 64 KiB - caps the export payload so a
 *    single tool call cannot drain the loopback.
 *  - [MAX_PLUGINS] - enforced by the enumerator (see [PluginEnumerator]).
 *
 * No tool talks to disk or to the network. The list / get / export tools
 * read the [PluginEntry]s the host already has in memory; the set tool
 * reports "not exposed" because `SettingsPageProvider` does not expose
 * a setter in this host.
 */
internal class PluginCfgMcpToolProvider(
    override val providerId: String,
    private val component: () -> PluginCfgComponent?,
) : McpToolProvider {

    override fun tools(): List<McpToolDefinition> = listOf(
        McpToolDefinition(
            name = "plugin_config_list",
            description =
                "List every loaded plugin this editor can see, with its id, displayName, version, " +
                    "and which sources observed it (loader, MCP tools, shortcuts). The editor " +
                    "composes from PluginLoaderDelegate, McpToolRegistry and KeyboardShortcutProvider - " +
                    "the list may not be exhaustive if a plugin contributes nothing to any of those.",
            handler = McpToolHandler { handleList() },
        ),
        McpToolDefinition(
            name = "plugin_config_export",
            description =
                "Export every loaded plugin this editor can see as one JSON object keyed by " +
                    "pluginId. The same shape as plugin_config_list, just batched. The payload is " +
                    "capped at " + MAX_SETTINGS_JSON_BYTES + " bytes - oversized responses are " +
                    "truncated with a clear status field on the last entry.",
            handler = McpToolHandler { handleExport() },
        ),
        McpToolDefinition(
            name = "plugin_config_set",
            description =
                "Programmatically set a setting on a plugin's SettingsPageProvider. The current " +
                    "SettingsPageProvider interface does NOT expose a setter, so this tool always " +
                    "reports 'not exposed'. If the host adds `set(pluginId, key, value)` in a " +
                    "future api, this tool will route to it without a schema change.",
            inputSchema = SET_SCHEMA,
            readOnly = false,
            handler = McpToolHandler { handleSet(it) },
        ),
        McpToolDefinition(
            name = "plugin_config_get",
            description =
                "Read a single field of a single plugin's settings. Recognised keys are the " +
                    "fields of PluginEntry (pluginId, displayName, version, author, description, " +
                    "url, type, isEnabled, healthy, isSystemPlugin, jarPath, mcpToolNames, ...). " +
                    "Anything else returns an explicit 'unknown key' error so a typo is not " +
                    "silently treated as an empty value.",
            inputSchema = GET_SCHEMA,
            handler = McpToolHandler { handleGet(it) },
        ),
    )

    /**
     * Build a JSON object with the same shape for list / export, but return
     * the entries as a list of JsonObject so both call sites share the
     * serialisation. Errors return a JsonObject with `error` set.
     */
    private fun snapshotEntries(): List<JsonObject> {
        val comp = component() ?: return emptyList()
        val entries = comp.exposedViewModel.snapshot()
        if (entries.isEmpty()) return emptyList()
        return entries.take(MAX_PLUGINS).map { entryToJson(it) }
    }

    /**
     * Shape one [PluginEntry] as a JsonObject. Every field is included so a
     * client can render any of them without first asking the schema.
     */
    private fun entryToJson(entry: PluginEntry): JsonObject = buildJsonObject {
        put("pluginId", entry.pluginId)
        put("displayName", entry.displayName)
        put("version", entry.version)
        put("author", entry.author)
        put("description", entry.description)
        put("url", entry.url)
        put("type", entry.type)
        put("isEnabled", entry.isEnabled)
        put("healthy", entry.healthy)
        put("isSystemPlugin", entry.isSystemPlugin)
        put("canUnload", entry.canUnload)
        put("requiresAdmin", entry.requiresAdmin)
        put("jarPath", entry.jarPath)
        put("fromLoader", entry.fromLoader)
        put("fromMcpTools", entry.fromMcpTools)
        put("mcpToolCount", entry.mcpToolCount)
        put("fromShortcuts", entry.fromShortcuts)
        put("shortcutCount", entry.shortcutCount)
        put("hasSettingsPage", entry.hasSettingsPage)
        put("hasSave", entry.hasSave)
        put("hasReset", entry.hasReset)
        put("hasGet", entry.hasGet)
        put("hasSet", entry.hasSet)
        putJsonArray("mcpToolNames") {
            for (name in entry.mcpToolNames.take(MAX_TOOL_NAMES_IN_EXPORT)) add(name)
        }
    }

    private fun handleList(): McpToolResult {
        val comp = component() ?: return noPanel()
        val entries = comp.exposedViewModel.snapshot()
        val sources = comp.exposedViewModel.activeSources
        val payload = buildJsonObject {
            put("count", entries.size)
            put("activeSources", sources.joinToString(","))
            putJsonArray("entries") {
                for (entry in entries.take(MAX_PLUGINS)) add(entryToJson(entry))
            }
        }
        return McpToolResult(json.encodeToString(JsonObject.serializer(), payload))
    }

    /**
     * Export the entire union as one JSON object keyed by pluginId. The
     * cap is [MAX_SETTINGS_JSON_BYTES] - a malicious / huge plugin list
     * would otherwise produce a multi-megabyte payload that the loopback
     * has to read in one go.
     */
    private fun handleExport(): McpToolResult {
        val comp = component() ?: return noPanel()
        val entries = comp.exposedViewModel.snapshot()
        if (entries.isEmpty()) {
            return McpToolResult("{}")
        }
        val builder = StringBuilder()
        builder.append('{')
        var first = true
        var truncated = false
        for (entry in entries.take(MAX_PLUGINS)) {
            if (!first) builder.append(',')
            val piece = "\"${escape(entry.pluginId)}\":${json.encodeToString(JsonObject.serializer(), entryToJson(entry))}"
            // Reserve room for a final truncation marker + closing brace.
            if (builder.length + piece.length + TRUNCATION_RESERVE > MAX_SETTINGS_JSON_BYTES) {
                truncated = true
                break
            }
            builder.append(piece)
            first = false
        }
        builder.append('}')
        if (truncated) {
            builder.append(" /* truncated at ${MAX_SETTINGS_JSON_BYTES} bytes */")
        }
        return McpToolResult(builder.toString())
    }

    /**
     * Set a setting. The host's SettingsPageProvider does NOT expose a
     * `set(pluginId, key, value)` method in this api version, so this tool
     * always returns an explicit `not exposed` error - honest refusal beats
     * silent success. When a future api exposes it, the gate below is the
     * one place to update.
     */
    private fun handleSet(args: McpToolArgs): McpToolResult {
        val pluginId = args.string("pluginId")
            ?: return McpToolResult("Missing required argument: pluginId", isError = true)
        if (pluginId.length > MAX_KEY_LENGTH) {
            return McpToolResult(
                "pluginId exceeds MAX_KEY_LENGTH=$MAX_KEY_LENGTH chars",
                isError = true,
            )
        }
        val key = args.string("key")
            ?: return McpToolResult("Missing required argument: key", isError = true)
        if (key.isBlank()) {
            return McpToolResult("Argument 'key' must not be blank", isError = true)
        }
        if (key.length > MAX_KEY_LENGTH) {
            return McpToolResult(
                "key exceeds MAX_KEY_LENGTH=$MAX_KEY_LENGTH chars",
                isError = true,
            )
        }
        val rawValue = args.string("value")
            ?: return McpToolResult("Missing required argument: value", isError = true)
        if (rawValue.length > MAX_VALUE_LENGTH) {
            return McpToolResult(
                "value exceeds MAX_VALUE_LENGTH=$MAX_VALUE_LENGTH chars",
                isError = true,
            )
        }
        val comp = component() ?: return noPanel()
        val row = comp.exposedViewModel.find(pluginId)
        if (row == null) {
            return McpToolResult("Unknown pluginId: $pluginId", isError = true)
        }
        if (!row.hasSet) {
            return McpToolResult(
                "SettingsPageProvider.set is not exposed by this host's api (plugin " +
                    "${row.pluginId} has no set() handler); the request was not applied.",
                isError = true,
            )
        }
        // Defensive: a future host that exposes set() would route here. The
        // current branch is unreachable because every row reports hasSet=false.
        return McpToolResult("set: not implemented in this build (forward-compatible only).")
    }

    /**
     * Read a single field from a single plugin. The recognised keys mirror
     * [PluginEntry] so a typo is loud - the read returns "unknown key"
     * rather than a JSON null that might be mistaken for a real value.
     */
    private fun handleGet(args: McpToolArgs): McpToolResult {
        val pluginId = args.string("pluginId")
            ?: return McpToolResult("Missing required argument: pluginId", isError = true)
        if (pluginId.isBlank()) {
            return McpToolResult("Argument 'pluginId' must not be blank", isError = true)
        }
        if (pluginId.length > MAX_KEY_LENGTH) {
            return McpToolResult(
                "pluginId exceeds MAX_KEY_LENGTH=$MAX_KEY_LENGTH chars",
                isError = true,
            )
        }
        val key = args.string("key")
            ?: return McpToolResult("Missing required argument: key", isError = true)
        if (key.length > MAX_KEY_LENGTH) {
            return McpToolResult(
                "key exceeds MAX_KEY_LENGTH=$MAX_KEY_LENGTH chars",
                isError = true,
            )
        }
        val comp = component() ?: return noPanel()
        val row = comp.exposedViewModel.find(pluginId)
        if (row == null) {
            return McpToolResult("Unknown pluginId: $pluginId", isError = true)
        }
        if (!row.hasGet) {
            // The host does not expose a get(pluginId, key) in this api
            // version, but every PluginEntry already carries its fields.
            // We fall back to reading them directly from the row, which is
            // what the user's panel sees.
            return readRowField(row, key)
        }
        // Defensive: a future host that exposes get() would route here.
        return readRowField(row, key)
    }

    private fun readRowField(row: PluginEntry, key: String): McpToolResult {
        val payload: Any? = when (key) {
            "pluginId" -> row.pluginId
            "displayName" -> row.displayName
            "version" -> row.version
            "author" -> row.author
            "description" -> row.description
            "url" -> row.url
            "type" -> row.type
            "isEnabled" -> row.isEnabled
            "healthy" -> row.healthy
            "isSystemPlugin" -> row.isSystemPlugin
            "canUnload" -> row.canUnload
            "requiresAdmin" -> row.requiresAdmin
            "jarPath" -> row.jarPath
            "fromLoader" -> row.fromLoader
            "fromMcpTools" -> row.fromMcpTools
            "mcpToolCount" -> row.mcpToolCount
            "mcpToolNames" -> row.mcpToolNames.take(MAX_TOOL_NAMES_IN_EXPORT)
            "fromShortcuts" -> row.fromShortcuts
            "shortcutCount" -> row.shortcutCount
            "hasSettingsPage" -> row.hasSettingsPage
            "hasSave" -> row.hasSave
            "hasReset" -> row.hasReset
            "hasGet" -> row.hasGet
            "hasSet" -> row.hasSet
            else -> null
        }
        if (payload == null && key !in KNOWN_BOOLEAN_FIELDS) {
            return McpToolResult(
                "Unknown key: $key. Recognised keys: " + KNOWN_KEYS.joinToString(", "),
                isError = true,
            )
        }
        // The recognised set is exhaustive; if we got here, payload is non-null.
        return when (payload) {
            is Boolean -> McpToolResult(if (payload) "true" else "false")
            is Int -> McpToolResult(payload.toString())
            is List<*> -> McpToolResult(json.encodeToString(JsonArray.serializer(), JsonArray(payload.map { JsonPrimitive(it.toString()) })))
            else -> McpToolResult(payload?.toString().orEmpty())
        }
    }

    private fun noPanel(): McpToolResult =
        McpToolResult("Plugin Config Editor panel is not open in any window.", isError = true)

    /** Escape a pluginId for inclusion in a JSON object key. */
    private fun escape(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length + 2)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '' -> sb.append("\\f")
                else -> if (c.code < 32) sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }

    private companion object {
        /** JSON-Schema for plugin_config_set. */
        const val SET_SCHEMA =
            """{"type":"object","properties":{"pluginId":{"type":"string","description":"Plugin id of the target plugin."},"key":{"type":"string","description":"Setting key."},"value":{"type":"string","description":"Setting value (string-typed in this api version; SettingsPageProvider.set is not exposed)."}},"required":["pluginId","key","value"]}"""

        /** JSON-Schema for plugin_config_get. */
        const val GET_SCHEMA =
            """{"type":"object","properties":{"pluginId":{"type":"string","description":"Plugin id of the target plugin."},"key":{"type":"string","description":"Field of PluginEntry to read."}},"required":["pluginId","key"]}"""

        /**
         * Reserve at the end of the export payload so the truncation marker
         * always fits even if the body grew to the cap.
         */
        const val TRUNCATION_RESERVE = 64

        /** Limit how many MCP tool names appear in an export row. */
        const val MAX_TOOL_NAMES_IN_EXPORT = 32

        /** All keys recognised by plugin_config_get. */
        val KNOWN_KEYS = listOf(
            "pluginId", "displayName", "version", "author", "description", "url", "type",
            "isEnabled", "healthy", "isSystemPlugin", "canUnload", "requiresAdmin", "jarPath",
            "fromLoader", "fromMcpTools", "mcpToolCount", "mcpToolNames",
            "fromShortcuts", "shortcutCount",
            "hasSettingsPage", "hasSave", "hasReset", "hasGet", "hasSet",
        )

        /** Boolean fields. Used by the unknown-key branch to distinguish a real false from null. */
        val KNOWN_BOOLEAN_FIELDS = setOf(
            "isEnabled", "healthy", "isSystemPlugin", "canUnload", "requiresAdmin",
            "fromLoader", "fromMcpTools", "fromShortcuts",
            "hasSettingsPage", "hasSave", "hasReset", "hasGet", "hasSet",
        )

        val json = Json {
            prettyPrint = false
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/** Hard cap on a single key (pluginId or setting key). */
const val MAX_KEY_LENGTH = 256

/** Hard cap on a single value passed to set(). */
const val MAX_VALUE_LENGTH = 16 * 1024

/** Hard cap on the export payload - one document the MCP server has to read in one go. */
const val MAX_SETTINGS_JSON_BYTES = 64 * 1024
