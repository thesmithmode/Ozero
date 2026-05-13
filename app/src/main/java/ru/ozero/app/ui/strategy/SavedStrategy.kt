package ru.ozero.app.ui.strategy

data class SavedStrategy(
    val id: String,
    val command: String,
    val name: String? = null,
    val isPinned: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastVerifiedAtMs: Long = 0L,
)
