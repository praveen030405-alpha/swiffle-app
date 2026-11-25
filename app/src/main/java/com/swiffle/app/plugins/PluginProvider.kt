package com.swiffle.app.plugins

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.util.Base64
import kotlinx.coroutines.runBlocking
import org.json.JSONObject

/** Narrow device bridge for the user's authorized Android debugging host. */
class PluginProvider : ContentProvider() {
    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        if (Binder.getCallingUid() != 2000) throw SecurityException("An authorized Android shell is required.")
        val app = requireNotNull(context).applicationContext
        val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        val response = try {
            val result = when (method) {
                "list" -> SwifflePlugins.list(app)
                "invoke" -> {
                    if (arg == null || arg.length > 8192) throw PluginInputError("Invalid tool request size.")
                    val request = JSONObject(String(Base64.decode(arg, flags), Charsets.UTF_8))
                    runBlocking { SwifflePlugins.call(app, request.getString("name"), request.getJSONObject("arguments")) }
                }
                else -> throw PluginInputError("Unknown operation.")
            }
            JSONObject().put("result", result)
        } catch (e: PluginInputError) {
            JSONObject().put("error", e.message)
        } catch (_: Exception) {
            JSONObject().put("error", "Swiffle could not complete this request. Check the arguments and the app.")
        }
        return Bundle().apply { putString("payload", Base64.encodeToString(response.toString().toByteArray(), flags)) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = throw UnsupportedOperationException()
    override fun getType(uri: Uri): String? = null
}
