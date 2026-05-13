package ru.ozero.app.ui.strategy

data class UsageEntry(
    val command: String,
    val appliedAt: Long = System.currentTimeMillis(),
    val name: String? = null,
)
