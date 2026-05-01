package ru.ozero.app.ui.splittunnel

import ru.ozero.enginescore.settings.SplitTunnelMode

sealed interface SplitTunnelUiState {
    data object Loading : SplitTunnelUiState

    data class Content(
        val mode: SplitTunnelMode,
        val query: String,
        val apps: List<AppRow>,
    ) : SplitTunnelUiState
}

data class AppRow(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val included: Boolean,
)
