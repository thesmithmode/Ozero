package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "connection_logs")
data class ConnectionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val engineId: String,
    val connectedAt: Long,
    val disconnectedAt: Long? = null,
    val bytesIn: Long = 0L,
    val bytesOut: Long = 0L,
    val disconnectReason: String? = null
)
