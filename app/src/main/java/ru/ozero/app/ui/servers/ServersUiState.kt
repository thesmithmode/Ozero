package ru.ozero.app.ui.servers

import ru.ozero.corestorage.entity.ServerEntity

sealed interface ServersUiState {
    data object Loading : ServersUiState

    data object Empty : ServersUiState

    data class Content(
        val servers: List<ServerEntity>,
        val entryId: String?,
        val exitId: String?,
    ) : ServersUiState {
        val entry: ServerEntity? get() = servers.firstOrNull { it.id == entryId }
        val exit: ServerEntity? get() = servers.firstOrNull { it.id == exitId }
        val canSave: Boolean
            get() = entry != null && exit != null && entryId != exitId
    }
}
