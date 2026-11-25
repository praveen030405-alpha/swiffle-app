package com.swiffle.app.evidence

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swiffle.app.theme.OrigamiPDFTheme
import com.swiffle.app.theme.SwiffleTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EvidencePacksActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OrigamiPDFTheme {
                EvidencePacksScreen(initialPack = intent.getStringExtra(PACK), onExit = ::finish)
            }
        }
    }

    companion object {
        private const val PACK = "evidence_pack"

        fun open(context: Context, packId: String? = null) {
            context.startActivity(Intent(context, EvidencePacksActivity::class.java).putExtra(PACK, packId))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EvidencePacksScreen(initialPack: String?, onExit: () -> Unit) {
    val context = LocalContext.current
    val store = remember { EvidenceStore.from(context) }
    val library by store.library.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var packId by rememberSaveable { mutableStateOf(initialPack) }
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var create by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var deleting by rememberSaveable { mutableStateOf<String?>(null) }
    var relinking by rememberSaveable { mutableStateOf<String?>(null) }
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var loadRetry by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }

    fun perform(action: suspend () -> Unit) {
        if (working) return
        working = true
        scope.launch {
            try {
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                snackbar.showSnackbar(error.message ?: "Swiffle couldn’t complete that action.")
            } finally {
                working = false
            }
        }
    }

    LaunchedEffect(loadRetry) {
        loadError = null
        try {
            store.load()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadError = "Swiffle couldn’t load your saved evidence packs."
        }
    }

    val pack = library?.packs?.firstOrNull { it.id == packId }
    val source = pack?.items?.firstOrNull { it.id == sourceId }
    val relinkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val original = pack?.items?.firstOrNull { it.id == relinking }
        relinking = null
        if (uri != null && original != null) perform { store.relink(original.source, uri) }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val snapshot = library?.packs?.firstOrNull { it.id == exporting }
        exporting = null
        if (uri != null && snapshot != null) perform {
            exportEvidencePack(context, store, snapshot, uri)
            snackbar.showSnackbar("Evidence pack exported")
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPanes = maxWidth >= 720.dp
        fun goBack() {
            when {
                sourceId != null -> sourceId = null
                packId != null -> packId = null
                else -> onExit()
            }
        }
        BackHandler(packId != null || sourceId != null) { goBack() }
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (source != null && !twoPanes) "Source PDF" else pack?.title ?: "Evidence packs",
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = ::goBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (source == null || twoPanes) {
                            if (pack != null) {
                                TextButton(enabled = !working, onClick = { rename = true }) { Text("Rename") }
                                TextButton(enabled = pack.items.isNotEmpty() && !working, onClick = {
                                    exporting = pack.id
                                    val filename = pack.title.replace(Regex("[^\\p{L}\\p{N} ._-]"), "_").take(80).trim()
                                    exportPicker.launch("${filename.ifBlank { "Evidence pack" }}.pdf")
                                }) { Text("Export PDF") }
                            } else {
                                TextButton(enabled = library != null && !working, onClick = { create = true }) { Text("New pack") }
                            }
                        }
                    }
                )
            }
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                if (library == null) {
                    Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        loadError?.let {
                            Text(it)
                            TextButton(onClick = { loadRetry++ }) { Text("Retry") }
                        } ?: CircularProgressIndicator()
                    }
                } else if (pack == null) {
                    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        if (library?.packs.orEmpty().isEmpty()) {
                            item {
                                Text(
                                    "Create a pack here or add an excerpt from any open PDF.",
                                    Modifier.padding(vertical = 24.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(library?.packs.orEmpty(), key = { it.id }) { entry ->
                            Surface(
                                onClick = {
                                    packId = entry.id
                                    perform { store.activate(entry.id) }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.background
                            ) {
                                Column(Modifier.padding(vertical = 16.dp)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    val active = if (library?.activeId == entry.id) " · Active pack" else ""
                                    Text("${entry.items.size} excerpts$active", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            key(pack.id) {
                                PackItems(
                                    pack, store, sourceId, library?.activeId == pack.id,
                                    onActivate = { perform { store.activate(pack.id) } },
                                    onSource = { sourceId = it.id },
                                    onEdit = { editing = it.id },
                                    onMove = { item, offset -> perform { store.move(pack.id, item.id, offset) } },
                                    onRemove = { deleting = it.id }
                                )
                            }
                        }
                        if (twoPanes && source != null) {
                            Box(Modifier.weight(1.2f).fillMaxSize()) {
                                EvidenceSourcePane(store, source, onClose = { sourceId = null }, onRelink = {
                                    relinking = source.id
                                    relinkPicker.launch(arrayOf("application/pdf"))
                                })
                            }
                        }
                    }
                    if (!twoPanes && source != null) {
                        Surface(Modifier.fillMaxSize()) {
                            EvidenceSourcePane(store, source, onClose = null, onRelink = {
                                relinking = source.id
                                relinkPicker.launch(arrayOf("application/pdf"))
                            })
                        }
                    }
                }
            }
        }
    }

    if (create || rename) {
        val title = if (rename) pack?.title.orEmpty() else ""
        var name by rememberSaveable(title) { mutableStateOf(title) }
        AlertDialog(
            onDismissRequest = { create = false; rename = false },
            title = { Text(if (create) "New evidence pack" else "Rename pack") },
            text = {
                OutlinedTextField(name, { name = it.take(100) }, label = { Text("Pack name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && !working, onClick = {
                    val isCreating = create
                    create = false
                    rename = false
                    perform {
                        if (isCreating) packId = store.create(name) else pack?.let { store.rename(it.id, name) }
                    }
                }) { Text(if (create) "Create" else "Save") }
            },
            dismissButton = { TextButton(onClick = { create = false; rename = false }) { Text("Cancel") } }
        )
    }
    pack?.items?.firstOrNull { it.id == editing }?.let { item ->
        EditEvidenceDialog(item, onDismiss = { editing = null }) { edited ->
            editing = null
            perform { store.editItem(pack.id, edited) }
        }
    }
    pack?.items?.firstOrNull { it.id == deleting }?.let { item ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Remove excerpt?") },
            text = { Text("This removes the excerpt and its note from the pack. The source PDF stays in place.") },
            confirmButton = {
                TextButton(enabled = !working, onClick = {
                    deleting = null
                    if (sourceId == item.id) sourceId = null
                    perform { store.remove(pack.id, item.id) }
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PackItems(
    pack: EvidencePack,
    store: EvidenceStore,
    sourceId: String?,
    active: Boolean,
    onActivate: () -> Unit,
    onSource: (EvidenceItem) -> Unit,
    onEdit: (EvidenceItem) -> Unit,
    onMove: (EvidenceItem, Int) -> Unit,
    onRemove: (EvidenceItem) -> Unit
) {
    val listState = rememberLazyListState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), state = listState) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${pack.items.count { it.reviewed }} of ${pack.items.size} reviewed")
                if (!active) TextButton(onClick = onActivate) { Text("Make active") }
                else Text("Active pack", color = SwiffleTheme.colors.primaryAccent)
            }
            if (pack.items.isEmpty()) {
                Text(
                    "Select text in a PDF and tap Add to pack, or use Clip page region for a scan.",
                    Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        pack.items.groupBy { it.group }.forEach { (group, clips) ->
            item("group:$group") {
                Text(
                    group.ifBlank { "Ungrouped" }, Modifier.padding(top = 16.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold
                )
            }
            items(clips, key = { it.id }) { item ->
                val position = clips.indexOf(item)
                var menu by remember { mutableStateOf(false) }
                Surface(
                    color = if (sourceId == item.id) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.background
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { onSource(item) }, modifier = Modifier.weight(1f)) {
                                Text("${item.source.filename} · Page ${item.page}", Modifier.fillMaxWidth())
                            }
                            Box {
                                IconButton(onClick = { menu = true }) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Excerpt actions")
                                }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("View excerpt and edit note") }, onClick = { menu = false; onEdit(item) })
                                    DropdownMenuItem(text = { Text("Move up") }, enabled = position > 0, onClick = {
                                        menu = false
                                        onMove(item, -1)
                                    })
                                    DropdownMenuItem(text = { Text("Move down") }, enabled = position < clips.lastIndex, onClick = {
                                        menu = false
                                        onMove(item, 1)
                                    })
                                    DropdownMenuItem(text = { Text("Remove excerpt") }, onClick = { menu = false; onRemove(item) })
                                }
                            }
                        }
                        Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (item.excerpt != null) {
                                Text(item.excerpt, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            } else {
                                val preview by produceState<Bitmap?>(null, item.id) {
                                    value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(store.imageFile(item.id).path) }
                                }
                                preview?.let {
                                    Image(it.asImageBitmap(), contentDescription = "Clipped page region", Modifier.heightIn(max = 200.dp))
                                }
                            }
                            if (item.note.isNotBlank()) Text(item.note, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (item.reviewed) "Reviewed" else "Unresolved",
                                color = if (item.reviewed) SwiffleTheme.colors.success else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EditEvidenceDialog(item: EvidenceItem, onDismiss: () -> Unit, onSave: (EvidenceItem) -> Unit) {
    var group by rememberSaveable(item.id) { mutableStateOf(item.group) }
    var note by rememberSaveable(item.id) { mutableStateOf(item.note) }
    var reviewed by rememberSaveable(item.id) { mutableStateOf(item.reviewed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Review excerpt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.excerpt?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                OutlinedTextField(group, { group = it.take(100) }, label = { Text("Group") }, singleLine = true)
                OutlinedTextField(note, { note = it.take(4000) }, label = { Text("Note") }, minLines = 2, maxLines = 5)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (reviewed) "Reviewed" else "Unresolved")
                    Switch(checked = reviewed, onCheckedChange = { reviewed = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(item.copy(group = group, note = note, reviewed = reviewed)) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EvidenceSourcePane(store: EvidenceStore, item: EvidenceItem, onClose: (() -> Unit)?, onRelink: () -> Unit) {
    val context = LocalContext.current
    var page by rememberSaveable(item.id) { mutableIntStateOf(item.page) }
    var fit by rememberSaveable(item.id) { mutableStateOf(false) }
    val source by produceState<Result<android.net.Uri>?>(null, item.source) {
        value = null
        value = evidenceResult { store.resolve(item.source) }
    }
    val resolved = source?.getOrNull()
    val rendered by produceState<Result<EvidenceRenderedPage>?>(null, resolved, page) {
        value = null
        if (resolved != null) value = evidenceResult {
            withContext(Dispatchers.IO) { renderEvidencePage(context, resolved, page, 1800) }
        }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.source.filename, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (onClose != null) TextButton(onClick = onClose) { Text("Close") }
        }
        if (resolved != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                TextButton(enabled = page > 1, onClick = { page-- }) { Text("Previous") }
                Text("Page $page")
                TextButton(enabled = page < (rendered?.getOrNull()?.pageCount ?: page), onClick = { page++ }) { Text("Next") }
                TextButton(onClick = { fit = !fit }) { Text(if (fit) "Zoom in" else "Fit page") }
                if (page != item.page) TextButton(onClick = { page = item.page }) { Text("Go to excerpt") }
            }
        }
        val error = source?.exceptionOrNull() ?: rendered?.exceptionOrNull()
        val image = rendered?.getOrNull()?.bitmap
        when {
            error != null -> {
                Text(error.message ?: "Unable to open the source PDF.", color = MaterialTheme.colorScheme.error)
                if (resolved == null) OutlinedButton(onClick = onRelink) { Text("Relink original PDF") }
            }
            image == null -> CircularProgressIndicator(Modifier.padding(18.dp).size(28.dp))
            else -> {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val scroll = rememberScrollState()
                    val horizontal = rememberScrollState()
                    val ratio = image.width.toFloat() / image.height
                    val width = if (fit) minOf(maxWidth, maxHeight * ratio) else maxWidth * 1.5f
                    val height = width / ratio
                    val density = LocalDensity.current
                    val regions = if (page == item.page) item.regions else emptyList()
                    val target = regions.firstOrNull()
                    LaunchedEffect(image, fit, target) {
                        val x = target?.let { (it.left + it.right) / 2f } ?: 0.5f
                        val y = target?.let { (it.top + it.bottom) / 2f } ?: 0f
                        val preferredX = with(density) { (width * x - maxWidth / 2).roundToPx() }
                        val preferredY = with(density) { (height * y - maxHeight / 2).roundToPx() }
                        horizontal.scrollTo(preferredX.coerceAtLeast(0))
                        scroll.scrollTo(preferredY.coerceAtLeast(0))
                    }
                    Box(Modifier.fillMaxSize().horizontalScroll(horizontal).verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
                        EvidencePageImage(image, regions, Modifier.size(width, height))
                    }
                }
            }
        }
    }
}

internal suspend fun <T> evidenceResult(action: suspend () -> T): Result<T> = try {
    Result.success(action())
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    Result.failure(error)
}
