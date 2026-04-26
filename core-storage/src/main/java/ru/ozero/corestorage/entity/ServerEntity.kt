package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "servers",
    // Индексы для hot-path запросов: getLiveServers (WHERE isAlive),
    // observeAll (ORDER BY priority). Без индексов — full-table scan,
    // деградирует при тысячах серверов из harvester'а.
    indices = [Index(value = ["isAlive"]), Index(value = ["priority"])],
)
data class ServerEntity(
    @PrimaryKey val id: String,
    val country: String,
    val role: String,
    val protocol: String,
    val uri: String,
    val port: Int,
    val priority: Int = 10,
    val isAlive: Boolean = true,
    val lastCheckedAt: Long = 0L,
    /**
     * E8 double-hop: id парного [ServerEntity] (entry↔exit). Если задан и role="entry",
     * Orchestrator собирает chain через XrayConfigBuilder.buildChain(entry, exit).
     * Для role="single" остаётся null.
     */
    val pairId: String? = null
)
