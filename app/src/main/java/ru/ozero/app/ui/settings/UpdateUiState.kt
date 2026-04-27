package ru.ozero.app.ui.settings

sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class Downloading(val percent: Int) : UpdateUiState()
    data object Verifying : UpdateUiState()
    data object Installing : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Failed(val reason: String) : UpdateUiState()
}

data class UpdateActions(
    val onCheck: () -> Unit,
    val onReset: () -> Unit,
)
