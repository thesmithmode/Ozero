package ru.ozero.app.ui.strategy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class FileStrategyResultsStore(
    filesDir: File,
    fileName: String = DEFAULT_FILE_NAME,
) : StrategyResultsStore {

    private val file: File = File(filesDir, fileName)

    override fun load(): List<StrategyResult> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val text = file.readText()
            if (text.isBlank()) return@runCatching emptyList<StrategyResult>()
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                StrategyResult(
                    command = o.optString("command"),
                    successCount = o.optInt("successCount"),
                    totalRequests = o.optInt("totalRequests"),
                    currentProgress = o.optInt("currentProgress"),
                    isCompleted = o.optBoolean("isCompleted"),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun save(results: List<StrategyResult>) {
        val array = JSONArray()
        results.forEach { r ->
            val obj = JSONObject()
            obj.put("command", r.command)
            obj.put("successCount", r.successCount)
            obj.put("totalRequests", r.totalRequests)
            obj.put("currentProgress", r.currentProgress)
            obj.put("isCompleted", r.isCompleted)
            array.put(obj)
        }
        runCatching { file.writeText(array.toString()) }
    }

    private companion object {
        const val DEFAULT_FILE_NAME: String = "proxy_test_results.json"
    }
}
