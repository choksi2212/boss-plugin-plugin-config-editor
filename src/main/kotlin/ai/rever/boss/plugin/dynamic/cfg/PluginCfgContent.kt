package ai.rever.boss.plugin.dynamic.cfg

import ai.rever.boss.plugin.ui.BossTheme
import ai.rever.boss.plugin.ui.BossThemeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Panel content for the Plugin Config Editor.
 *
 * Two columns side by side: a plugin list on the left, a detail view on the
 * right. The detail view holds a per-plugin toolbar (Save / Discard / Reset)
 * and a settings area; the settings area renders a metadata block today
 * because the host does not expose a settings-page registry, so the toolbar
 * always reports "not exposed" for Save / Reset / Get / Set.
 *
 * The "based on union of N sources" banner sits at the top so the user can
 * tell at a glance whether the list is exhaustive or partial.
 */
@Composable
fun PluginCfgContent(
    viewModel: PluginCfgViewModel,
) {
    BossTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colors.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBanner(
                    activeSources = viewModel.activeSources,
                    entriesCount = viewModel.entries.collectAsState().value.size,
                    onRefresh = { viewModel.refresh() },
                )

                ToastRow(
                    info = viewModel.info.collectAsState().value,
                    error = viewModel.error.collectAsState().value,
                    onDismiss = { viewModel.clearMessages() },
                )

                Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.1f))

                Body(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun TopBanner(
    activeSources: List<String>,
    entriesCount: Int,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(MaterialTheme.colors.surface)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Settings,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Plugin Config",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colors.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "($entriesCount)",
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = onRefresh,
            modifier = Modifier.size(24.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ToastRow(
    info: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(info, error) {
        delay(3500)
        onDismiss()
    }
    if (info == null && error == null) return
    val isError = error != null
    val text = error ?: info ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isError) BossThemeColors.ErrorColor else BossThemeColors.SuccessColor)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Error else Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = BossThemeColors.TextPrimary,
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            fontSize = 11.sp,
            color = BossThemeColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier.size(20.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Dismiss",
                modifier = Modifier.size(12.dp),
                tint = BossThemeColors.TextPrimary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Body(viewModel: PluginCfgViewModel) {
    val entries by viewModel.entries.collectAsState()
    val selectedId by viewModel.selectedPluginId.collectAsState()
    val selected = entries.firstOrNull { it.pluginId == selectedId }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
    ) {
        // Left: plugin list with a sources banner at the top.
        Column(modifier = Modifier.weight(0.4f)) {
            SourcesBanner(activeSources = viewModel.activeSources)
            if (entries.isEmpty()) {
                EmptyState(activeSources = viewModel.activeSources)
            } else {
                PluginList(
                    entries = entries,
                    selectedId = selectedId,
                    onSelect = { id -> viewModel.select(id) },
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Right: detail view with a toolbar and the settings area.
        Column(
            modifier = Modifier
                .weight(0.6f)
                .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
                .border(0.5.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                .padding(8.dp),
        ) {
            if (selected == null) {
                NoSelectionState()
            } else {
                PluginDetail(
                    entry = selected,
                    onSave = { viewModel.saveSelected() },
                    onDiscard = { viewModel.discardSelected() },
                    onReset = { viewModel.resetSelected() },
                )
            }
        }
    }
}

@Composable
private fun SourcesBanner(activeSources: List<String>) {
    val text = when {
        activeSources.isEmpty() -> "No sources: this host exposes none of PluginLoaderDelegate, McpToolRegistry or KeyboardShortcutProvider."
        activeSources.size == 1 -> "Based on: ${activeSources.first()}"
        else -> "Based on union of ${activeSources.joinToString(", ")} - the panel may not be exhaustive"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.surface.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(8.dp),
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun EmptyState(activeSources: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Extension,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No loaded plugins observed",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
            if (activeSources.isEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Host does not expose any plugin source.",
                    fontSize = 10.sp,
                    color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun NoSelectionState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select a plugin to view its settings",
                fontSize = 12.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun PluginList(
    entries: List<PluginEntry>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    val listScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface, RoundedCornerShape(6.dp))
            .border(0.5.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
            .verticalScroll(listScroll)
            .padding(4.dp),
    ) {
        for (entry in entries) {
            PluginRow(
                entry = entry,
                isSelected = entry.pluginId == selectedId,
                onClick = { onSelect(entry.pluginId) },
            )
        }
    }
}

@Composable
private fun PluginRow(
    entry: PluginEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (isSelected) {
        MaterialTheme.colors.primary.copy(alpha = 0.18f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName.ifEmpty { entry.pluginId },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${entry.pluginId} - ${entry.version.ifEmpty { "?" }}",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.fromMcpTools) {
                ToolBadge(count = entry.mcpToolCount)
                Spacer(modifier = Modifier.width(2.dp))
            }
            if (entry.fromShortcuts) {
                ShortcutBadge(count = entry.shortcutCount)
                Spacer(modifier = Modifier.width(2.dp))
            }
            if (!entry.healthy) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Unhealthy",
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFEF5350),
                )
            } else if (!entry.isEnabled) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Disabled",
                    modifier = Modifier.size(12.dp),
                    tint = Color(0xFFFFA726),
                )
            } else if (entry.isSystemPlugin) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "System plugin",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ToolBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1976D2).copy(alpha = 0.18f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "$count tools",
            fontSize = 9.sp,
            color = Color(0xFF1976D2),
        )
    }
}

@Composable
private fun ShortcutBadge(count: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF6A1B9A).copy(alpha = 0.18f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Text(
            text = "$count sc",
            fontSize = 9.sp,
            color = Color(0xFF6A1B9A),
        )
    }
}

@Composable
private fun PluginDetail(
    entry: PluginEntry,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onReset: () -> Unit,
) {
    val detailScroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(detailScroll),
    ) {
        Text(
            text = entry.displayName.ifEmpty { entry.pluginId },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = entry.pluginId,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Toolbar(
            entry = entry,
            onSave = onSave,
            onDiscard = onDiscard,
            onReset = onReset,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(8.dp))

        MetadataBlock(entry = entry)
        Spacer(modifier = Modifier.height(8.dp))
        Divider(color = MaterialTheme.colors.onBackground.copy(alpha = 0.1f))
        Spacer(modifier = Modifier.height(8.dp))

        SettingsArea(entry = entry)
    }
}

@Composable
private fun Toolbar(
    entry: PluginEntry,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colors.background, RoundedCornerShape(4.dp))
            .border(0.5.dp, MaterialTheme.colors.onBackground.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Per-plugin toolbar",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.weight(1f))
        TextButton(
            onClick = onSave,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.heightIn(min = 24.dp),
        ) {
            Text(
                text = "Save",
                fontSize = 11.sp,
                color = if (entry.hasSave) {
                    MaterialTheme.colors.primary
                } else {
                    MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
                },
            )
        }
        TextButton(
            onClick = onDiscard,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.heightIn(min = 24.dp),
        ) {
            Text(
                text = "Discard",
                fontSize = 11.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
        TextButton(
            onClick = onReset,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.heightIn(min = 24.dp),
        ) {
            Text(
                text = "Reset",
                fontSize = 11.sp,
                color = if (entry.hasReset) {
                    Color(0xFFEF5350)
                } else {
                    MaterialTheme.colors.onBackground.copy(alpha = 0.4f)
                },
            )
        }
    }
}

@Composable
private fun MetadataBlock(entry: PluginEntry) {
    Column {
        SectionHeader(title = "Metadata")
        MetadataRow("pluginId", entry.pluginId, mono = true)
        if (entry.displayName.isNotEmpty() && entry.displayName != entry.pluginId) {
            MetadataRow("displayName", entry.displayName)
        }
        if (entry.version.isNotEmpty()) MetadataRow("version", entry.version, mono = true)
        if (entry.author.isNotEmpty()) MetadataRow("author", entry.author)
        if (entry.type.isNotEmpty()) MetadataRow("type", entry.type, mono = true)
        if (entry.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.description,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
            )
        }
        if (entry.url.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.url,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(isOn = entry.healthy, color = Color(0xFF4CAF50), label = "healthy")
            Spacer(modifier = Modifier.width(8.dp))
            StatusDot(isOn = entry.isEnabled, color = Color(0xFF1976D2), label = "enabled")
            if (entry.isSystemPlugin) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(isOn = true, color = Color(0xFF6A1B9A), label = "system")
            }
            if (!entry.canUnload) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(isOn = true, color = Color(0xFFFFA726), label = "locked")
            }
            if (entry.requiresAdmin) {
                Spacer(modifier = Modifier.width(8.dp))
                StatusDot(isOn = true, color = Color(0xFFEF5350), label = "admin")
            }
        }
        if (entry.jarPath.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = entry.jarPath,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String, mono: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.width(90.dp),
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colors.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatusDot(isOn: Boolean, color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isOn) color else color.copy(alpha = 0.3f)),
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun SettingsArea(entry: PluginEntry) {
    Column {
        SectionHeader(title = "Settings")
        if (entry.fromMcpTools && entry.mcpToolNames.isNotEmpty()) {
            Text(
                text = "MCP tools contributed by this plugin:",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            )
            Spacer(modifier = Modifier.height(4.dp))
            for (name in entry.mcpToolNames) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1976D2)),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = name,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        SettingsSupportRow(label = "SettingsPageProvider", exposed = entry.hasSettingsPage)
        SettingsSupportRow(label = "save()", exposed = entry.hasSave)
        SettingsSupportRow(label = "reset()", exposed = entry.hasReset)
        SettingsSupportRow(label = "get()", exposed = entry.hasGet)
        SettingsSupportRow(label = "set()", exposed = entry.hasSet)

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "The host does not expose a central settings-page registry, so the editor " +
                "lists loaded plugins from PluginLoaderDelegate + McpToolRegistry + " +
                "KeyboardShortcutProvider. The Save / Discard / Reset toolbar stays visible so a " +
                "future host which exposes SettingsPageProvider.save / reset can light it up " +
                "without a UI change.",
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.55f),
        )
    }
}

@Composable
private fun SettingsSupportRow(label: String, exposed: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.width(120.dp),
        )
        if (exposed) {
            Text(
                text = "exposed",
                fontSize = 10.sp,
                color = Color(0xFF4CAF50),
            )
        } else {
            Text(
                text = "not exposed by SettingsPageProvider in this host",
                fontSize = 10.sp,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
    )
    Spacer(modifier = Modifier.height(4.dp))
}
