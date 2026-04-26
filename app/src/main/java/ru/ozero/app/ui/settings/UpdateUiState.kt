package ru.ozero.app.ui.settings

/**
 * UI-состояние секции self-update в SettingsScreen.
 *
 * Lifecycle:
 *   Idle → Checking → (Downloading*)? → Verifying → Installing → (UpToDate|Failed)
 *   Idle → Checking → UpToDate (если latest <= current)
 *   Idle → Checking → Failed (любая ошибка fetch/download/verify/install)
 *
 * Кнопка «Проверить обновление» enabled только в терминальных состояниях
 * (Idle, UpToDate, Failed) — иначе игнорится двойной клик.
 */
sealed class UpdateUiState {
    data object Idle : UpdateUiState()
    data object Checking : UpdateUiState()
    data class Downloading(val percent: Int) : UpdateUiState()
    data object Verifying : UpdateUiState()
    data object Installing : UpdateUiState()
    data object UpToDate : UpdateUiState()
    data class Failed(val reason: String) : UpdateUiState()
}

/** Группа update-колбэков для UI. */
data class UpdateActions(
    val onCheck: () -> Unit,
    val onReset: () -> Unit,
)
