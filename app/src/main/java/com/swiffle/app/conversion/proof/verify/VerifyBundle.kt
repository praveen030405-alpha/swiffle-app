package com.swiffle.app.conversion.proof.verify

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*

// Standalone JVM entry point. Requires only the Kotlin runtime and kotlinx-serialization-json.
object VerifyBundle {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun hash(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(32768)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }
    fun checkBundle(root: File, source: File): String {
        val cert = Json.parseToJsonElement(File(root,"cert.json").readText()).jsonObject
        fun field(key: String) = cert.getValue(key).jsonPrimitive.content
        require(field("version") == "1" && field("sourceFidelity") == "UNVERIFIED")
        require(hash(source) == field("sourceSha256")) { "Source hash mismatch" }
        val data = File(root,"data/cells.ndjson")
        val shards = cert.getValue("shards").jsonArray
        require(shards.isNotEmpty())
        val leaves = mutableListOf<ByteArray>()
        val names = mutableSetOf<String>()
        val indexLines = mutableListOf("# Conversion evidence", "Source SHA-256: " + field("sourceSha256"),
            "Merkle root: " + field("merkleRoot"))
        data.bufferedReader().use { rows ->
            for ((i, entry) in shards.withIndex()) {
                val obj = entry.jsonObject
                val name = obj.getValue("name").jsonPrimitive.content
                require(name == "shards/" + (i + 1).toString().padStart(6,'0') + ".md")
                names.add(name.substringAfter('/'))
                val shard = File(root,name)
                shard.bufferedReader().use { reader ->
                    require(reader.readLine() == "# Source cells" && reader.readLine() == "~~~~jsonl") { name }
                    val count = obj.getValue("cells").jsonPrimitive.long
                    require(count > 0)
                    var n = 0L
                    while (n < count) {
                        val expected = Json.parseToJsonElement(requireNotNull(rows.readLine()) { name }).jsonObject
                        val where = expected.getValue("sourceRegion").toString() + "!" + expected.getValue("location")
                        val actual = reader.readLine()
                        require(actual != null && runCatching { Json.parseToJsonElement(actual) }.getOrNull() == expected) { where }
                        n++
                    }
                    require(reader.readLine() == "~~~~" && reader.readLine() == null) { name }
                }
                val sha = hash(shard)
                require(sha == obj.getValue("sha256").jsonPrimitive.content) { name }
                leaves += digest(("leaf\u0000" + name + "\u0000" + sha).toByteArray())
                indexLines.add("- [" + name + "](" + name + ") " + sha)
            }
            require(rows.readLine() == null) { "Extra sidecar cells" }
        }
        require(File(root,"shards").list()?.toSet() == names) { "Unexpected shard inventory" }
        require(hash(data) == field("sidecarSha256")) { "Sidecar hash mismatch" }
        var nodes: List<ByteArray> = leaves
        while (nodes.size > 1) nodes = nodes.chunked(2).map { pair -> digest(byteArrayOf(1) + pair[0] + pair[pair.lastIndex]) }
        require(hex(nodes.single()) == field("merkleRoot")) { "Merkle mismatch" }
        require(File(root,"index.md").readLines() == indexLines) { "Index mismatch" }
        return field("merkleRoot")
    }
    @JvmStatic fun main(args: Array<String>) { require(args.size == 2); println(checkBundle(File(args[0]),File(args[1]))) }
}
