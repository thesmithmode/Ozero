package ru.ozero.singboxroom.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "subscription_groups")
data class SubscriptionGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val subscriptionUrl: String = "",
    val isBuiltin: Boolean = false,
    val autoUpdate: Boolean = true,
    val autoUpdateDelay: Int = 360,
    val lastUpdated: Long = 0,
    val bytesUsed: Long = 0,
    val bytesRemaining: Long = 0,
    val expiryDate: Long = 0,
    val userOrder: Int = 0,
    val updateWhenConnectedOnly: Boolean = false,
)
