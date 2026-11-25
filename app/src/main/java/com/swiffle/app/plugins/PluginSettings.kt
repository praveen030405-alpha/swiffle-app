package com.swiffle.app.plugins

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.swiffle.app.ui.settings.SettingsSubpageShell

@Composable
internal fun PluginSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    SettingsSubpageShell("Plugins", onBack) {
        Text("Connect ChatGPT through your paired computer. Each enabled plugin shares the data listed below.",
            modifier = Modifier.padding(16.dp))
        for (plugin in SwifflePlugins.installed) {
            var access by remember(plugin.id) { mutableIntStateOf(SwifflePlugins.access(context, plugin)) }
            fun change(level: Int) {
                SwifflePlugins.setAccess(context, plugin, level)
                access = level
            }
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(plugin.title, style = MaterialTheme.typography.titleMedium)
                Text(plugin.description, modifier = Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Allow reading", Modifier.weight(1f))
                    Switch(checked = access >= 1, onCheckedChange = { change(if (it) 1 else 0) })
                }
                if (plugin.tools.any { !it.readOnly }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Allow changes", Modifier.weight(1f))
                        Switch(checked = access == 2, enabled = access >= 1, onCheckedChange = { change(if (it) 2 else 1) })
                    }
                }
            }
        }
    }
}
