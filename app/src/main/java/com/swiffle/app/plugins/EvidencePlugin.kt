package com.swiffle.app.plugins

import com.swiffle.app.evidence.EvidenceStore
import org.json.JSONArray
import org.json.JSONObject

private fun schema(vararg fields: Pair<String, JSONObject>, required: List<String> = emptyList()) =
    JSONObject().put("type", "object").put("additionalProperties", false)
        .put("properties", JSONObject().apply { fields.forEach { (name, spec) -> put(name, spec) } })
        .put("required", JSONArray(required))

private fun textField(limit: Int) = JSONObject().put("type", "string").put("minLength", 1).put("maxLength", limit)

private fun JSONObject.text(name: String, limit: Int): String {
    val value = opt(name) as? String ?: throw PluginInputError("Missing or invalid ${name}.")
    if (value.isBlank() || value.length > limit) throw PluginInputError("Invalid length for ${name}.")
    return value.trim()
}

private val offsetField get() = JSONObject().put("type", "integer").put("minimum", 0).put("maximum", Int.MAX_VALUE)
private fun JSONObject.offset(): Int {
    if (!has("offset")) return 0
    val number = opt("offset")
    if (number !is Int || number < 0) throw PluginInputError("Offset must be a non-negative integer.")
    return number
}

private fun <T> page(values: List<T>, offset: Int, size: Int, key: String, encode: (T) -> JSONObject): JSONObject {
    val batch = values.drop(offset).take(size)
    val next = offset.toLong() + batch.size
    return JSONObject().put(key, JSONArray(batch.map(encode)))
        .put("total", values.size).put("nextOffset", if (next < values.size) next else JSONObject.NULL)
}

internal object EvidencePlugin : SwifflePlugin {
    override val id = "evidence"
    override val title = "Evidence Packs"
    override val description = "Read pack titles, saved excerpts, notes and source page references. Write access adds pack creation and review status changes."

    override val tools = listOf(
        PluginTool("swiffle_list_packs", "List up to 25 Evidence Packs on the paired phone. Use nextOffset for more.",
            schema("offset" to offsetField), readOnly = true, idempotent = true) { context, args ->
            val library = EvidenceStore.from(context).load()
            page(library.packs, args.offset(), 25, "packs") {
                JSONObject().put("id", it.id).put("title", it.title.take(256)).put("itemCount", it.items.size)
            }
        },
        PluginTool("swiffle_read_pack", "Read up to 10 saved evidence items, including page numbers and text previews. Use nextOffset for more.",
            schema("packId" to textField(64), "offset" to offsetField, required = listOf("packId")),
            readOnly = true, idempotent = true) { context, args ->
            val id = args.text("packId", 64)
            val pack = EvidenceStore.from(context).load().packs.find { it.id == id }
                ?: throw PluginInputError("Evidence Pack not found.")
            page(pack.items, args.offset(), 10, "items") {
                JSONObject().put("id", it.id).put("filename", it.source.filename.take(256)).put("page", it.page)
                    .put("excerpt", it.excerpt?.take(1500) ?: JSONObject.NULL).put("note", it.note.take(1000))
                    .put("group", it.group.take(200)).put("reviewed", it.reviewed)
            }.put("id", pack.id).put("title", pack.title.take(256))
        },
        PluginTool("swiffle_create_pack", "Create an empty Evidence Pack and make it active for new captures on the phone.",
            schema("title" to textField(120), required = listOf("title")), readOnly = false, idempotent = false) { context, args ->
            val title = args.text("title", 120)
            JSONObject().put("id", EvidenceStore.from(context).create(title)).put("title", title)
        },
        PluginTool("swiffle_set_reviewed", "Set one saved item's reviewed flag. Use the pack and item IDs from Swiffle.",
            schema("packId" to textField(64), "itemId" to textField(64), "reviewed" to JSONObject().put("type", "boolean"),
                required = listOf("packId", "itemId", "reviewed")), readOnly = false, idempotent = true) { context, args ->
            val packId = args.text("packId", 64)
            val itemId = args.text("itemId", 64)
            val reviewed = args.opt("reviewed") as? Boolean ?: throw PluginInputError("Reviewed must be true or false.")
            val found = EvidenceStore.from(context).setReviewed(packId, itemId, reviewed)
            if (!found) throw PluginInputError("Evidence item not found in that pack.")
            JSONObject().put("packId", packId).put("itemId", itemId).put("reviewed", reviewed)
        }
    )
}
