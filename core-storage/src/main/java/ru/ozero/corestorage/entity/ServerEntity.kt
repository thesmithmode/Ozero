package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val country: String,
    val role: String,
    val protocol: String,
    val uri: String,
    val port: Int,
    val priority: Int = 10,
    val isAlive: Boolean = true,
    val lastCheckedAt: Long = 0L
)
