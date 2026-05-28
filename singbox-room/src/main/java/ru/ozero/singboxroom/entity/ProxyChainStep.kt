package ru.ozero.singboxroom.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "proxy_chain_steps",
    foreignKeys = [
        ForeignKey(
            entity = ProxyProfile::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId"), Index("userOrder")],
)
data class ProxyChainStep(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val userOrder: Int,
)
