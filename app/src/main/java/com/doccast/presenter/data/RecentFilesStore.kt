package com.doccast.presenter.data

import android.content.Context
import com.doccast.presenter.model.DocFormat
import com.doccast.presenter.model.RecentFileItem
import org.json.JSONArray
import org.json.JSONObject

class RecentFilesStore(context: Context) {
    private val prefs = context.getSharedPreferences("doccast_recent_files", Context.MODE_PRIVATE)

    fun loadAll(): List<RecentFileItem> {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val result = mutableListOf<RecentFileItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val fmt = runCatching { DocFormat.valueOf(obj.getString("format")) }
                    .getOrDefault(DocFormat.UNKNOWN)
                result.add(
                    RecentFileItem(
                        uriString = obj.getString("uri"),
                        displayName = obj.getString("name"),
                        format = fmt,
                        lastOpenedAt = obj.optLong("ts", 0L)
                    )
                )
            }
            result.sortedByDescending { it.lastOpenedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun recordOpened(uriString: String, displayName: String, format: DocFormat) {
        val current = loadAll().filterNot { it.uriString == uriString }.toMutableList()
        current.add(0, RecentFileItem(uriString, displayName, format, System.currentTimeMillis()))
        val trimmed = current.take(MAX_RECENT)
        val arr = JSONArray()
        for (item in trimmed) {
            val obj = JSONObject()
            obj.put("uri", item.uriString)
            obj.put("name", item.displayName)
            obj.put("format", item.format.name)
            obj.put("ts", item.lastOpenedAt)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    companion object {
        private const val KEY_ITEMS = "items_json"
        private const val MAX_RECENT = 20
    }
}
