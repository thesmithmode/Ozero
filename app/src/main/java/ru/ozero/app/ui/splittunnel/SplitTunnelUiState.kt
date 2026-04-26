package ru.ozero.app.ui.splittunnel

import ru.ozero.commonvpn.split.SplitTunnelMode

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
