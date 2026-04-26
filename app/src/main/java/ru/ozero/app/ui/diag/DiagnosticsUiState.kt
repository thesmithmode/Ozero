package ru.ozero.app.ui.diag

sealed interface DiagnosticsUiState {
    data object NotConnected : DiagnosticsUiState

    data object Idle : DiagnosticsUiState

    data class Running(
        val total: Int,
        val completed: Int,
    ) : DiagnosticsUiState

    data class Done(val results: List<DiagResult>) : DiagnosticsUiState
}
