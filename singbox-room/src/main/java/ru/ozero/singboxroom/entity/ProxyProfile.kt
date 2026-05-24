package ru.ozero.singboxroom.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "proxy_profiles",
    foreignKeys = [
        ForeignKey(
            entity = SubscriptionGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("groupId")],
)
data class ProxyProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val groupId: Long,
    val name: String,
    val beanBlob: ByteArray,
    val protocolType: Int,
    val userOrder: Int = 0,
    val latencyMs: Int = -1,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ProxyProfile) return false
        return id == other.id &&
            groupId == other.groupId &&
            name == other.name &&
            beanBlob.contentEquals(other.beanBlob) &&
            protocolType == other.protocolType &&
            userOrder == other.userOrder &&
            latencyMs == other.latencyMs
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + beanBlob.contentHashCode()
        result = 31 * result + protocolType
        result = 31 * result + userOrder
        result = 31 * result + latencyMs
        return result
    }
}
