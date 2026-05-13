package ru.ozero.app.ui.strategy

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

interface SavedStrategyStore {
    fun load(): List<SavedStrategy>
    fun save(strategies: List<SavedStrategy>)
    fun update(transform: (List<SavedStrategy>) -> List<SavedStrategy>): List<SavedStrategy> {
        val updated = transform(load())
        save(updated)
        return updated
    }
}

class FileSavedStrategyStore(
    filesDir: File,
    fileName: String = "saved_strategies.json",
    private val maxUnpinned: Int = 40,
) : SavedStrategyStore {

    private val file = File(filesDir, fileName)

    @Synchronized
    override fun update(transform: (List<SavedStrategy>) -> List<SavedStrategy>): List<SavedStrategy> {
        val updated = transform(load())
        save(updated)
        return updated
    }

    @Synchronized
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
                    lastVerifiedAtMs = o.optLong("lastVerifiedAtMs", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
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
                    .put("addedAt", s.addedAt)
                    .put("lastVerifiedAtMs", s.lastVerifiedAtMs),
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

fun SavedStrategyStore.add(command: String): List<SavedStrategy> = update { existing ->
    if (existing.any { it.command == command }) {
        existing
    } else {
        existing + SavedStrategy(id = UUID.randomUUID().toString(), command = command)
    }
}

fun SavedStrategyStore.pin(id: String): List<SavedStrategy> = update { list ->
    list.map { if (it.id == id) it.copy(isPinned = true) else it }
}

fun SavedStrategyStore.unpin(id: String): List<SavedStrategy> = update { list ->
    list.map { if (it.id == id) it.copy(isPinned = false) else it }
}

fun SavedStrategyStore.rename(id: String, name: String): List<SavedStrategy> = update { list ->
    list.map { if (it.id == id) it.copy(name = name.takeIf { it.isNotBlank() }) else it }
}

fun SavedStrategyStore.delete(id: String): List<SavedStrategy> = update { list ->
    list.filter { it.id != id }
}

fun SavedStrategyStore.markVerified(commands: Set<String>, nowMs: Long): List<SavedStrategy> = update { list ->
    list.map { s -> if (commands.contains(s.command)) s.copy(lastVerifiedAtMs = nowMs) else s }
}
