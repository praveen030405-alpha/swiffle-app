package com.swiffle.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun HomeDocumentActions(
    recentTitle: String?,
    packTitle: String?,
    onOpen: () -> Unit,
    onScan: () -> Unit,
    onTools: () -> Unit,
    onRecent: () -> Unit,
    onPack: () -> Unit,
    onAllPacks: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open document") }
            OutlinedButton(onClick = onScan) { Text("Scan") }
            TextButton(onClick = onTools) { Text("Tools") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (recentTitle != null || packTitle != null) Text("Continue working", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onAllPacks) { Text("Evidence packs") }
        }
        if (packTitle != null || recentTitle != null) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    packTitle?.let {
                        ContinueItem(it, "Evidence pack", Modifier.weight(1f), onPack)
                    }
                    recentTitle?.let {
                        ContinueItem(it, "Recent document", Modifier.weight(1f), onRecent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueItem(title: String, caption: String, modifier: Modifier, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = modifier, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
