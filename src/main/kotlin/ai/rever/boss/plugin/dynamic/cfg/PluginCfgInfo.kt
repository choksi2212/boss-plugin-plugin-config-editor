package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import ai.rever.boss.plugin.api.Panel.Companion.left
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import compose.icons.FeatherIcons
import compose.icons.feathericons.Settings

/**
 * Panel info for the Plugin Config Editor.
 *
 * Lives in the left bottom slot at priority 88 - the same neighbourhood as
 * other management / inspection surfaces (Plugin X-Ray at 60, the Toolbox).
 * High enough that it is easy to find in a crowded sidebar but below the
 * toolbox so the heavier UI does not displace it.
 */
object PluginCfgInfo : PanelInfo {
    override val id = PanelId("plugin-config-editor", 88)
    override val displayName = "Plugin Config"
    override val icon = FeatherIcons.Settings
    override val defaultSlotPosition = left.bottom
}
