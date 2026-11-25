package com.swiffle.app.evidence

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.AtomicFile
import com.swiffle.app.ui.home.getFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

internal class EvidenceSourceException(message: String) : IOException(message)

internal class EvidenceStore private constructor(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "evidence").apply { mkdirs() }
    private val index = AtomicFile(File(directory, "packs.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val sourceMutations = Mutex()
    private var loaded = false
    private val _library = MutableStateFlow<EvidenceLibrary?>(null)
    val library = _library.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        synchronized(lock) { readLocked() }
    }

    private fun readLocked(): EvidenceLibrary {
        if (!loaded) {
            val saved = try {
                json.decodeFromString<EvidenceLibrary>(index.readFully().decodeToString())
            } catch (_: FileNotFoundException) {
                EvidenceLibrary()
            }
            _library.value = saved
            loaded = true
        }
        return checkNotNull(_library.value)
    }

    private suspend fun update(change: (EvidenceLibrary) -> EvidenceLibrary) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val old = readLocked()
            val next = change(old)
            if (old == next) return@synchronized
            val stream = index.startWrite()
            try {
                stream.write(json.encodeToString(EvidenceLibrary.serializer(), next).encodeToByteArray())
                index.finishWrite(stream)
            } catch (error: Throwable) {
                index.failWrite(stream)
                throw error
            }
            _library.value = next
        }
    }

    suspend fun create(title: String): String {
        val pack = EvidencePack(UUID.randomUUID().toString(), title.trim().ifBlank { "Untitled pack" })
        update { it.copy(activeId = pack.id, packs = listOf(pack) + it.packs) }
        return pack.id
    }

    suspend fun activate(id: String) = update { library ->
        if (library.packs.any { it.id == id }) library.copy(activeId = id) else library
    }

    suspend fun rename(packId: String, title: String) = editPack(packId) {
        it.copy(title = title.trim().ifBlank { it.title })
    }

    suspend fun editItem(packId: String, item: EvidenceItem) = editPack(packId) { pack ->
        pack.copy(items = pack.items.map {
            if (it.id == item.id) it.copy(group = item.group.trim(), note = item.note.trim(), reviewed = item.reviewed)
            else it
        })
    }

    suspend fun setReviewed(packId: String, itemId: String, reviewed: Boolean): Boolean {
        var found = false
        editPack(packId) { pack ->
            pack.copy(items = pack.items.map { item ->
                if (item.id == itemId) {
                    found = true
                    item.copy(reviewed = reviewed)
                } else item
            })
        }
        return found
    }

    suspend fun move(packId: String, itemId: String, direction: Int) = editPack(packId) { pack ->
        pack.moveWithinGroup(itemId, direction)
    }

    private suspend fun editPack(id: String, change: (EvidencePack) -> EvidencePack) = update { library ->
        library.copy(packs = library.packs.map { if (it.id == id) change(it) else it })
    }

    suspend fun capture(uri: Uri, filename: String, capture: EvidenceCapture): EvidencePack =
        sourceMutations.withLock {
            withContext(Dispatchers.IO) { captureFromSource(uri, filename, capture) }
        }

    private suspend fun captureFromSource(uri: Uri, filename: String, capture: EvidenceCapture): EvidencePack {
        require(capture.page > 0 && capture.regions.isNotEmpty())
        val id = UUID.randomUUID().toString()
        val source = prepareSource(uri, filename)
        try {
            if (capture.excerpt == null) {
                val crop = renderEvidencePage(context, sourceUri(source), capture.page, 1400, capture.regions.single()).bitmap
                try {
                    imageFile(id).outputStream().use { out ->
                        check(crop.compress(Bitmap.CompressFormat.PNG, 100, out)) { "Unable to save the page clip." }
                    }
                } finally {
                    crop.recycle()
                }
            }
            val item = EvidenceItem(id, source, capture.page, capture.regions, capture.excerpt)
            lateinit var target: EvidencePack
            update { library ->
                val existing = library.activePack ?: EvidencePack(UUID.randomUUID().toString(), "Evidence pack")
                target = existing.copy(items = existing.items + item)
                library.copy(
                    activeId = target.id,
                    packs = listOf(target) + library.packs.filterNot { it.id == target.id }
                )
            }
            return target
        } catch (error: Exception) {
            if (_library.value?.packs.orEmpty().none { pack -> pack.items.any { it.id == id } }) {
                imageFile(id).delete()
                removeUnusedCopy(source)
            }
            throw error
        }
    }

    suspend fun remove(packId: String, itemId: String) = sourceMutations.withLock {
        withContext(Dispatchers.IO) {
            var removed: EvidenceItem? = null
            editPack(packId) { pack ->
                removed = pack.items.firstOrNull { it.id == itemId }
                pack.copy(items = pack.items.filterNot { it.id == itemId })
            }
            removed?.let {
                imageFile(itemId).delete()
                removeUnusedCopy(it.source)
            }
        }
    }

    private fun removeUnusedCopy(source: EvidenceSource) {
        if (!source.retainedCopy) return
        val stillUsed = _library.value?.packs.orEmpty().any { pack ->
            pack.items.any { item -> item.source.retainedCopy && item.source.fingerprint == source.fingerprint }
        }
        if (!stillUsed) File(directory, "${source.fingerprint}.pdf").delete()
    }

    fun imageFile(id: String) = File(directory, "$id.png")

    suspend fun resolve(source: EvidenceSource): Uri = withContext(Dispatchers.IO) {
        val uri = sourceUri(source)
        val current = try {
            fingerprint(uri)
        } catch (_: IOException) {
            throw EvidenceSourceException("The source PDF is unavailable. Select the original PDF to relink it.")
        } catch (_: SecurityException) {
            throw EvidenceSourceException("Swiffle no longer has access. Select the original PDF to relink it.")
        }
        if (current != source.fingerprint) {
            throw EvidenceSourceException("The source PDF has changed. Select the original version to preserve the page reference.")
        }
        uri
    }

    suspend fun relink(old: EvidenceSource, uri: Uri) = sourceMutations.withLock {
        withContext(Dispatchers.IO) {
            if (fingerprint(uri) != old.fingerprint) {
                throw EvidenceSourceException("That file is a different version. Choose a copy of the original PDF.")
            }
            val replacement = prepareSource(uri, getFileName(context, uri))
            try {
                if (replacement.fingerprint != old.fingerprint) {
                    throw EvidenceSourceException("The selected file changed while it was being opened.")
                }
                update { library ->
                    library.copy(packs = library.packs.map { pack ->
                        pack.copy(items = pack.items.map { item ->
                            if (item.source == old) item.copy(source = replacement) else item
                        })
                    })
                }
            } finally {
                removeUnusedCopy(old)
                removeUnusedCopy(replacement)
            }
        }
    }

    private fun sourceUri(source: EvidenceSource): Uri =
        if (source.retainedCopy) Uri.fromFile(File(directory, "${source.fingerprint}.pdf"))
        else Uri.parse(source.uri)

    private fun prepareSource(uri: Uri, filename: String): EvidenceSource {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some Android share providers issue a temporary grant only.
        } catch (_: IllegalArgumentException) {
            // File URIs do not support persisted provider grants.
        }

        val persistent = context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (persistent) return EvidenceSource(uri.toString(), filename, fingerprint(uri))

        val temporary = File.createTempFile("source-", ".tmp", directory)
        try {
            val hash = temporary.outputStream().use { fingerprint(uri, it) }
            val retained = File(directory, "$hash.pdf")
            synchronized(lock) {
                if (!retained.exists() && !temporary.renameTo(retained)) throw IOException("Unable to preserve access to this PDF.")
            }
            return EvidenceSource(uri.toString(), filename, hash, retainedCopy = true)
        } finally {
            temporary.delete()
        }
    }

    private fun fingerprint(uri: Uri, copy: java.io.OutputStream? = null): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        input.use {
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
                copy?.write(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile private var instance: EvidenceStore? = null

        fun from(context: Context): EvidenceStore = instance ?: synchronized(this) {
            instance ?: EvidenceStore(context.applicationContext).also { instance = it }
        }
    }
}

internal data class EvidenceRenderedPage(val bitmap: Bitmap, val pageCount: Int)

internal fun renderEvidencePage(
    context: Context,
    uri: Uri,
    page: Int,
    maxSide: Int,
    bounds: EvidenceBounds = EvidenceBounds.FullPage
): EvidenceRenderedPage {
    fun openRenderer(descriptor: ParcelFileDescriptor): PdfRenderer = try {
        PdfRenderer(descriptor)
    } catch (error: Exception) {
        descriptor.close()
        throw error
    }

    val resolver = context.contentResolver
    var temporary: File? = null
    try {
        val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw FileNotFoundException()
        val renderer = try {
            openRenderer(descriptor)
        } catch (_: IllegalArgumentException) {
            val localFile = File.createTempFile("evidence-render-", ".pdf", context.cacheDir)
            temporary = localFile
            val input = resolver.openInputStream(uri) ?: throw FileNotFoundException()
            input.use { source -> localFile.outputStream().use { source.copyTo(it) } }
            openRenderer(ParcelFileDescriptor.open(localFile, ParcelFileDescriptor.MODE_READ_ONLY))
        }
        return renderer.use { document ->
            if (page !in 1..document.pageCount) throw EvidenceSourceException("That page is no longer available.")
            document.openPage(page - 1).use { source ->
                val width = (source.width * bounds.width).coerceAtLeast(1f)
                val height = (source.height * bounds.height).coerceAtLeast(1f)
                val scale = maxSide / max(width, height)
                val bitmap = Bitmap.createBitmap(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888
                )
                try {
                    bitmap.eraseColor(Color.WHITE)
                    val transform = Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(-bounds.left * source.width * scale, -bounds.top * source.height * scale)
                    }
                    source.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    EvidenceRenderedPage(bitmap, document.pageCount)
                } catch (error: Exception) {
                    bitmap.recycle()
                    throw error
                }
            }
        }
    } finally {
        temporary?.delete()
    }
}
