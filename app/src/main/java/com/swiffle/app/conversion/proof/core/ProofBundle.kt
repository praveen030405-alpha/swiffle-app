package com.swiffle.app.conversion.proof.core

import java.io.File
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class CellType { EMPTY, NOT_APPLICABLE, TEXT, DECIMAL, DATE, BOOLEAN, ERROR }

@Serializable
data class SourceToken(val id: String, val text: String, val bbox: String)

@Serializable
data class Cell(
    val id: String, val sourceRegion: String, val location: String,
    val section: String, val rowLabel: String, val columnKey: String,
    val valueRaw: String?, val valueTyped: String?, val displayText: String,
    val type: CellType, val provenanceHash: String, val formula: String? = null,
    val cachedValue: String? = null, val tokens: List<SourceToken> = emptyList()
) {
    init {
        require(id.isNotBlank() && sourceRegion.isNotBlank() && location.isNotBlank())
        require(provenanceHash.matches(Regex("[0-9a-f]{64}")))
        when (type) {
            CellType.EMPTY -> require(valueRaw == null && valueTyped == null)
            CellType.NOT_APPLICABLE -> require(valueTyped == null)
            CellType.DECIMAL -> requireNotNull(valueTyped).let { BigDecimal(it) }
            CellType.DATE -> LocalDate.parse(requireNotNull(valueTyped))
            CellType.BOOLEAN -> require(valueTyped in listOf("true", "false"))
            CellType.TEXT, CellType.ERROR -> require(valueTyped != null)
        }
    }
}

@Serializable
data class Shard(val name: String, val sha256: String, val cells: Long)

@Serializable
data class Evidence(
    val version: Int = 1, val sourceSha256: String, val shards: List<Shard>,
    val merkleRoot: String, val sidecarSha256: String, val createdUtc: String,
    val tool: String = "Swiffle conversion foundation 1",
    val roundTrip: String = "PASS", val sourceFidelity: String = "UNVERIFIED",
    val readerBenchmark: String = "NOT_RUN"
)

object ProofBundle {
    private val json = Json { encodeDefaults = true }
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun hash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                md.update(buffer, 0, count)
            }
        }
        return hex(md.digest())
    }
    internal fun merkle(shards: List<Shard>): String {
        var nodes = shards.map { digest(("leaf\u0000" + it.name + "\u0000" + it.sha256).toByteArray()) }
        require(nodes.isNotEmpty())
        while (nodes.size > 1) nodes = nodes.chunked(2).map { digest(byteArrayOf(1) + it.first() + it.last()) }
        return hex(nodes.single())
    }

    // The iterator supplies one complete logical row. A shard boundary can only occur between rows.
    fun write(source: File, target: File, rows: Sequence<List<Cell>>, rowsPerShard: Int = 1000): Evidence {
        require(rowsPerShard in 500..2000 && !target.exists())
        val root = Files.createTempDirectory(target.absoluteFile.parentFile.toPath(), ".proof-").toFile()
        try {
            val shardsDir = File(root, "shards").apply { mkdir() }
            val sidecar = File(root, "data/cells.ndjson").apply { parentFile!!.mkdir() }
            val shards = mutableListOf<Shard>()
            val stream = rows.iterator()
            sidecar.bufferedWriter().use { side ->
                while (stream.hasNext()) {
                    val name = "shards/" + (shards.size + 1).toString().padStart(6, '0') + ".md"
                    val file = File(root, name)
                    var count = 0L
                    file.bufferedWriter().use { out ->
                        out.appendLine("# Source cells")
                        out.appendLine("~~~~jsonl")
                        var rowCount = 0
                        while (rowCount < rowsPerShard && stream.hasNext()) {
                            val row = stream.next()
                            require(row.isNotEmpty())
                            for (cell in row) {
                                val encoded = json.encodeToString(cell)
                                side.appendLine(encoded)
                                out.appendLine(encoded)
                                count++
                            }
                            rowCount++
                        }
                        out.appendLine("~~~~")
                    }
                    shards += Shard(name, hash(file), count)
                }
            }
            val cert = Evidence(sourceSha256 = hash(source), shards = shards, merkleRoot = merkle(shards),
                sidecarSha256 = hash(sidecar), createdUtc = java.time.Instant.now().toString())
            File(root, "cert.json").writeText(json.encodeToString(cert))
            File(root, "index.md").bufferedWriter().use { index ->
                index.appendLine("# Conversion evidence")
                index.appendLine("Source SHA-256: " + cert.sourceSha256)
                index.appendLine("Merkle root: " + cert.merkleRoot)
                for (s in shards) index.appendLine("- [" + s.name + "](" + s.name + ") " + s.sha256)
            }
            // Decode Markdown back to typed cells before making the directory visible.
            sidecar.bufferedReader().use { original ->
                for (s in shards) File(root, s.name).bufferedReader().use { view ->
                    check(view.readLine() == "# Source cells" && view.readLine() == "~~~~jsonl")
                    var n = 0L
                    while (n < s.cells) {
                        val expected = json.decodeFromString<Cell>(requireNotNull(original.readLine()))
                        val actual = json.decodeFromString<Cell>(requireNotNull(view.readLine()))
                        check(expected == actual) { expected.sourceRegion + "!" + expected.location }
                        n++
                    }
                    check(view.readLine() == "~~~~" && view.readLine() == null)
                }
                check(original.readLine() == null)
            }
            Files.move(root.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            return cert
        } finally { root.deleteRecursively() }
    }
}
