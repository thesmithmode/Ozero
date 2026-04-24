package ru.ozero.corestorage.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_split_rules")
data class AppSplitRule(
    @PrimaryKey val packageName: String,
    val isExcluded: Boolean = false
)
