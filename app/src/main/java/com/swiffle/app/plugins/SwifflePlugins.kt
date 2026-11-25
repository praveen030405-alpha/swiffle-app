package com.swiffle.app.plugins

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A bundled extension. The host discovers the tool contract and handler together. */
internal interface SwifflePlugin {
    val id: String
    val title: String
    val description: String
    val tools: List<PluginTool>
}

internal data class PluginTool(
    val name: String,
    val description: String,
    val schema: JSONObject,
    val readOnly: Boolean,
    val idempotent: Boolean,
    val run: suspend (Context, JSONObject) -> JSONObject
) {
    fun definition() = JSONObject()
        .put("name", name).put("description", description).put("inputSchema", schema)
        .put("annotations", JSONObject().put("readOnlyHint", readOnly)
            .put("destructiveHint", false).put("idempotentHint", idempotent).put("openWorldHint", false))
}

internal class PluginInputError(message: String) : Exception(message)

internal object SwifflePlugins {
    val installed: List<SwifflePlugin> = listOf(EvidencePlugin)
    private val catalog = installed.flatMap { plugin -> plugin.tools.map { plugin to it } }

    init {
        require(installed.distinctBy { it.id }.size == installed.size)
        require(catalog.distinctBy { it.second.name }.size == catalog.size)
    }

    private fun preferences(context: Context) = context.getSharedPreferences("swiffle_plugin_access", Context.MODE_PRIVATE)
    fun access(context: Context, plugin: SwifflePlugin) = preferences(context).getInt(plugin.id, 0)
    fun setAccess(context: Context, plugin: SwifflePlugin, level: Int) {
        require(level in 0..2)
        preferences(context).edit().putInt(plugin.id, level).apply()
    }

    private fun allowed(context: Context, plugin: SwifflePlugin, tool: PluginTool): Boolean {
        val level = access(context, plugin)
        return level == 2 || (level == 1 && tool.readOnly)
    }

    fun list(context: Context) = JSONObject().put("tools", JSONArray().apply {
        catalog.filter { (plugin, tool) -> allowed(context, plugin, tool) }.forEach { put(it.second.definition()) }
    })

    suspend fun call(context: Context, name: String, args: JSONObject): JSONObject {
        val (plugin, tool) = catalog.find { it.second.name == name }
            ?: throw PluginInputError("Unknown Swiffle tool.")
        if (!allowed(context, plugin, tool)) throw PluginInputError("Enable access to this tool in Swiffle Settings > Plugins.")
        val properties = tool.schema.getJSONObject("properties")
        if (args.keys().asSequence().any { !properties.has(it) }) throw PluginInputError("Unexpected argument.")
        return tool.run(context, args)
    }
}
