package ru.ozero.enginebyedpi.strategy

import org.json.JSONObject
import java.io.File

class StrategyFitnessCache(
    private val file: File,
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {

    private data class Entry(val fitness: Double, val testedAtMs: Long)

    private val entries = mutableMapOf<String, Entry>()

    @Synchronized
    fun load() {
        if (!file.exists()) return
        runCatching {
            val obj = JSONObject(file.readText())
            obj.keys().forEach { key ->
                val node = obj.getJSONObject(key)
                entries[key] = Entry(
                    fitness = node.getDouble("fitness"),
                    testedAtMs = node.getLong("testedAtMs"),
                )
            }
        }
    }

    @Synchronized
    fun save() {
        runCatching {
            val obj = JSONObject()
            entries.forEach { (key, entry) ->
                obj.put(
                    key,
                    JSONObject().apply {
                        put("fitness", entry.fitness)
                        put("testedAtMs", entry.testedAtMs)
                    },
                )
            }
            file.writeText(obj.toString())
        }
    }

    @Synchronized
    fun get(command: String): Double? {
        val entry = entries[command] ?: return null
        if (nowMs() - entry.testedAtMs > ttlMs) {
            entries.remove(command)
            return null
        }
        return entry.fitness
    }

    @Synchronized
    fun put(command: String, fitness: Double) {
        entries[command] = Entry(fitness = fitness, testedAtMs = nowMs())
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun clearStale() {
        val now = nowMs()
        entries.entries.removeAll { now - it.value.testedAtMs > ttlMs }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        runCatching { if (file.exists()) file.delete() }
    }

    companion object {
        const val DEFAULT_TTL_MS: Long = 24L * 60L * 60L * 1000L
    }
}
