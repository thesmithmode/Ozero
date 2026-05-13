package ru.ozero.app.ui.strategy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

interface UsageHistoryStore {
    fun load(): List<UsageEntry>
    fun record(command: String, name: String?): List<UsageEntry>
}

class FileUsageHistoryStore(
    filesDir: File,
    fileName: String = "strategy_usage_history.json",
    private val maxEntries: Int = 50,
) : UsageHistoryStore {

    private val file = File(filesDir, fileName)

    @Synchronized
    override fun load(): List<UsageEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                UsageEntry(
                    command = o.getString("command"),
                    appliedAt = o.optLong("appliedAt", 0L),
                    name = o.optString("name").takeIf { it.isNotBlank() },
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    override fun record(command: String, name: String?): List<UsageEntry> {
        val trimmed = (listOf(UsageEntry(command = command, name = name)) + load()).take(maxEntries)
        val array = JSONArray()
        trimmed.forEach { e ->
            array.put(
                JSONObject()
                    .put("command", e.command)
                    .put("appliedAt", e.appliedAt)
                    .put("name", e.name ?: ""),
            )
        }
        runCatching { file.writeText(array.toString()) }
        return trimmed
    }
}
