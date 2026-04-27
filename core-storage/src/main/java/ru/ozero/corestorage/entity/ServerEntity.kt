package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "servers",
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
        val pairId: String? = null
)
