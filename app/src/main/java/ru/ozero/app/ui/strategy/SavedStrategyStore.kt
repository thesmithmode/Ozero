package ru.ozero.app.ui.strategy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

interface SavedStrategyStore {
    fun load(): List<SavedStrategy>
    fun save(strategies: List<SavedStrategy>)
}

class FileSavedStrategyStore(
    filesDir: File,
    fileName: String = "saved_strategies.json",
    private val maxUnpinned: Int = 40,
) : SavedStrategyStore {

    private val file = File(filesDir, fileName)

    override fun load(): List<SavedStrategy> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                SavedStrategy(
                    id = o.getString("id"),
                    command = o.getString("command"),
                    name = o.optString("name").takeIf { it.isNotBlank() },
                    isPinned = o.optBoolean("isPinned", false),
                    addedAt = o.optLong("addedAt", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun save(strategies: List<SavedStrategy>) {
        val trimmed = trimToLimit(strategies)
        val array = JSONArray()
        trimmed.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("command", s.command)
                    .put("name", s.name ?: "")
                    .put("isPinned", s.isPinned)
                    .put("addedAt", s.addedAt),
            )
        }
        runCatching { file.writeText(array.toString()) }
    }

    private fun trimToLimit(strategies: List<SavedStrategy>): List<SavedStrategy> {
        val pinned = strategies.filter { it.isPinned }
        val unpinned = strategies.filter { !it.isPinned }
        val trimmedUnpinned = if (unpinned.size > maxUnpinned) {
            unpinned.sortedBy { it.addedAt }.drop(unpinned.size - maxUnpinned)
        } else {
            unpinned
        }
        return pinned + trimmedUnpinned
    }
}

fun SavedStrategyStore.add(command: String): List<SavedStrategy> {
    val existing = load()
    if (existing.any { it.command == command }) return existing
    val updated = existing + SavedStrategy(id = UUID.randomUUID().toString(), command = command)
    save(updated)
    return updated
}

fun SavedStrategyStore.pin(id: String): List<SavedStrategy> {
    val updated = load().map { if (it.id == id) it.copy(isPinned = true) else it }
    save(updated)
    return updated
}

fun SavedStrategyStore.unpin(id: String): List<SavedStrategy> {
    val updated = load().map { if (it.id == id) it.copy(isPinned = false) else it }
    save(updated)
    return updated
}

fun SavedStrategyStore.rename(id: String, name: String): List<SavedStrategy> {
    val updated = load().map { if (it.id == id) it.copy(name = name.takeIf { it.isNotBlank() }) else it }
    save(updated)
    return updated
}

fun SavedStrategyStore.delete(id: String): List<SavedStrategy> {
    val updated = load().filter { it.id != id }
    save(updated)
    return updated
}
