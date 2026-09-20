package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.runtime.Composable
import com.arkivanov.decompose.ComponentContext

/**
 * Panel component for the Plugin Config Editor.
 *
 * Owns the [PluginCfgViewModel]. The MCP tools reach the same ViewModel
 * through [PluginCfgDynamicPlugin.lastComponent] - the same shape plugin
 * X-Ray uses.
 *
 * Component scope: when the panel is closed, the view model is cancelled by
 * the lifecycle (we explicitly cancel on dispose; see [dispose] below). The
 * component itself is a [PanelComponentWithUI] - content is composed into
 * the host's plugin boundary so a crash here is reported, not propagated.
 */
class PluginCfgComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo,
    private val viewModel: PluginCfgViewModel,
) : PanelComponentWithUI, ComponentContext by ctx {

    /** Exposed so the MCP tool provider can drive the same state. */
    val exposedViewModel: PluginCfgViewModel get() = viewModel

    @Composable
    override fun Content() {
        PluginCfgContent(viewModel = viewModel)
    }

    /**
     * Cancel the view model scope so background subscriptions stop. The
     * [PluginEnumerator]'s subscription to `McpToolRegistry.allTools` is a
     * plain StateFlow collector that lives inside that scope - it has to be
     * cancelled when the panel is gone, otherwise the registry would carry a
     * dangling subscription.
     */
    fun dispose() {
        viewModel.cancel()
    }
}
