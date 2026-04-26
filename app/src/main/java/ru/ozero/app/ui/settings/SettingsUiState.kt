package ru.ozero.app.ui.settings

import ru.ozero.app.settings.SettingsModel

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Content(val model: SettingsModel) : SettingsUiState
}
