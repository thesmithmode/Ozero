package ru.ozero.app.ui.servers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ozero.app.subscription.ServerImportService
import javax.inject.Inject

sealed interface ManualServerUiState {
    data class Idle(val uri: String = "") : ManualServerUiState

    data class Importing(val uri: String) : ManualServerUiState

    data class Success(val protocol: String) : ManualServerUiState

    data class Error(val reason: String, val uri: String) : ManualServerUiState
}

@HiltViewModel
class ManualServerViewModel @Inject constructor(
    private val importer: ServerImportService,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ManualServerUiState>(ManualServerUiState.Idle())
    val uiState: StateFlow<ManualServerUiState> = _uiState.asStateFlow()

    fun onUriChange(text: String) {
        val current = _uiState.value
        val baseUri = when (current) {
            is ManualServerUiState.Idle -> current.uri
            is ManualServerUiState.Error -> current.uri
            else -> ""
        }
        if (text == baseUri) return
        _uiState.value = ManualServerUiState.Idle(uri = text)
    }

    fun onAdd() {
        val uri = when (val s = _uiState.value) {
            is ManualServerUiState.Idle -> s.uri
            is ManualServerUiState.Error -> s.uri
            else -> return
        }.trim()
        if (uri.isEmpty()) {
            _uiState.value = ManualServerUiState.Error(reason = "пустой URI", uri = uri)
            return
        }
        _uiState.value = ManualServerUiState.Importing(uri)
        viewModelScope.launch {
            val result = runCatching { importer.import(uri) }
                .getOrElse { ServerImportService.ImportResult.Error(it.message ?: "import failed") }
            _uiState.value = when (result) {
                is ServerImportService.ImportResult.Ok ->
                    ManualServerUiState.Success(protocol = result.entity.protocol)
                is ServerImportService.ImportResult.Error ->
                    ManualServerUiState.Error(reason = result.reason, uri = uri)
            }
        }
    }

    fun onDismissResult() {
        _uiState.value = ManualServerUiState.Idle()
    }
}
