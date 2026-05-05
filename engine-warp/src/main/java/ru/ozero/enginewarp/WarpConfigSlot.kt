package ru.ozero.enginewarp

import java.util.UUID

data class WarpConfigSlot(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val config: WarpConfig,
    val isActive: Boolean = false,
)
