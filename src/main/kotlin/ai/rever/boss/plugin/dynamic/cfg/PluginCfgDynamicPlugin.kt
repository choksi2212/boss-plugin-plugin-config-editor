package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.DynamicPlugin
import ai.rever.boss.plugin.api.PluginContext
import com.arkivanov.essenty.lifecycle.doOnDestroy

/**
 * Plugin Config Editor - Loaded from external JAR.
 *
 * Two surfaces:
 *  - A left_bottom sidebar panel that lists every loaded plugin and surfaces
 *    a per-plugin toolbar (Save / Discard / Reset) plus a settings area.
 *  - An MCP tool provider exposing four tools - `plugin_config_list`,
 *    `plugin_config_export`, `plugin_config_set`, `plugin_config_get`.
 *
 * The host does NOT expose a single "list every loaded plugin" API, so the
 * editor composes from [ai.rever.boss.plugin.api.PluginLoaderDelegate],
 * [ai.rever.boss.plugin.api.McpToolRegistry] and
 * [ai.rever.boss.plugin.api.KeyboardShortcutProvider]. The panel banner
 * names the active sources.
 *
 * Lifecycle:
 *  - The [PluginCfgComponent] is owned by the host through `panelRegistry`,
 *    which keeps a strong reference. We hold a separate weak-ish reference
 *    through [lastComponent] so the MCP tools can drive the same state.
 *  - When the panel is destroyed, [lastComponent] is cleared so an MCP
 *    call does not drive a torn-down view model.
 *  - [dispose] nulls the references on plugin unload.
 */
class PluginCfgDynamicPlugin : DynamicPlugin {
    override val pluginId: String = "ai.rever.boss.plugin.dynamic.cfg"
    override val displayName: String = "Plugin Config Editor (Dynamic)"
    override val version: String = "0.1.0"
    override val description: String =
        "Centralized panel listing every loaded plugin and its MCP tools / shortcuts - " +
            "with MCP tools to list, export and read individual fields."
    override val author: String = "choksi2212"
    override val url: String = "https://github.com/choksi2212/boss-plugin-plugin-config-editor"

    /**
     * Last opened panel, so the MCP tools can drive the same state the panel renders.
     *
     * The panel's component owns the [PluginCfgViewModel]; MCP tools that want to update or
     * read the panel's visible state go through it. A destroyed component's scope is cancelled,
     * so MCP tools driving a destroyed component would silently no-op or throw - clear the
     * reference when the panel closes.
     */
    @Volatile
    private var lastComponent: PluginCfgComponent? = null

    /**
     * The MCP tool provider. Held so [register] can unregister it on [dispose] if the host
     * ever needs that; today the host cleans up automatically with `registerMcpToolProvider`.
     */
    private var toolProvider: PluginCfgMcpToolProvider? = null

    override fun register(context: PluginContext) {
        // Probe the sources defensively. The editor degrades silently when a source is absent -
        // a user on a host without a PluginLoaderDelegate still sees the banner explaining why
        // the list is empty.
        val loader = runCatching { context.getPluginAPI(ai.rever.boss.plugin.api.PluginLoaderDelegate::class.java) }
            .getOrNull()
        val toolRegistry = context.mcpToolRegistry
        val shortcutProvider = context.keyboardShortcutProvider

        context.panelRegistry.registerPanel(PluginCfgInfo) { ctx, panelInfo ->
            val viewModel = PluginCfgViewModel(
                loader = loader,
                toolRegistry = toolRegistry,
                shortcutProvider = shortcutProvider,
            )
            PluginCfgComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                viewModel = viewModel,
            ).also { comp ->
                lastComponent = comp
                ctx.lifecycle.doOnDestroy {
                    if (lastComponent === comp) {
                        comp.dispose()
                        lastComponent = null
                    }
                }
            }
        }

        val provider = PluginCfgMcpToolProvider(
            providerId = pluginId,
            component = { lastComponent },
        )
        toolProvider = provider
        context.registerMcpToolProvider(provider)
    }

    override fun dispose() {
        lastComponent?.dispose()
        lastComponent = null
        toolProvider = null
    }
}
