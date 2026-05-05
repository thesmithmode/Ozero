package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpFileImporter
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class WarpSettingsUiState(
    val slots: List<WarpConfigSlot> = emptyList(),
    val activeSlotId: String? = null,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
    val progressText: String? = null,
    val importSuccess: Boolean = false,
    val showRenameDialog: Boolean = false,
    val renamingSlotId: String? = null,
    val renameText: String = "",
)

@HiltViewModel
class WarpEngineSettingsViewModel @Inject constructor(
    private val store: WarpConfigSlotStore,
    private val autoConfig: WarpAutoConfig,
    private val fileImporter: WarpFileImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarpSettingsUiState())
    val uiState: StateFlow<WarpSettingsUiState> = _uiState.asStateFlow()
    private var registerJob: Job? = null

    init {
        viewModelScope.launch { store.migrateIfNeeded() }
        store.slots().onEach { slots ->
            _uiState.value = _uiState.value.copy(
                slots = slots,
                activeSlotId = slots.firstOrNull { it.isActive }?.id,
            )
        }.launchIn(viewModelScope)
    }

    fun onGenerate() {
        if (_uiState.value.isRegistering) return
        _uiState.value = _uiState.value.copy(isRegistering = true, errorMessage = null, progressText = null)
        registerJob = viewModelScope.launch {
            try {
                val result = autoConfig.register(onProgress = { progress ->
                    _uiState.value = _uiState.value.copy(progressText = "Зеркало $progress")
                })
                result.fold(
                    onSuccess = { cfg ->
                        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                        store.addSlot("WARP Auto $timestamp", cfg)
                        _uiState.value = _uiState.value.copy(
                            isRegistering = false,
                            errorMessage = null,
                            progressText = null,
                        )
                    },
                    onFailure = { t ->
                        _uiState.value = _uiState.value.copy(
                            isRegistering = false,
                            errorMessage = t.message ?: "register failed",
                            progressText = null,
                        )
                    },
                )
            } catch (ce: CancellationException) {
                _uiState.value = _uiState.value.copy(isRegistering = false, progressText = null)
                throw ce
            } finally {
                registerJob = null
            }
        }
    }

    fun onCancelGenerate() {
        registerJob?.cancel()
        registerJob = null
        _uiState.value = _uiState.value.copy(isRegistering = false)
    }

    fun onImportFile(stream: InputStream, suggestedName: String = "Imported") {
        viewModelScope.launch {
            val result = fileImporter.import(stream)
            result.fold(
                onSuccess = { cfg ->
                    store.addSlot(suggestedName, cfg)
                    _uiState.value = _uiState.value.copy(errorMessage = null, importSuccess = true)
                },
                onFailure = { t ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = t.message ?: "import failed",
                    )
                },
            )
        }
    }

    fun onSetActive(id: String) {
        viewModelScope.launch {
            store.setActive(id)
        }
    }

    fun onDeleteSlot(id: String) {
        viewModelScope.launch {
            store.delete(id)
        }
    }

    fun onStartRename(id: String) {
        val currentName = _uiState.value.slots.firstOrNull { it.id == id }?.name ?: ""
        _uiState.value = _uiState.value.copy(
            showRenameDialog = true,
            renamingSlotId = id,
            renameText = currentName,
        )
    }

    fun onRenameConfirm() {
        val id = _uiState.value.renamingSlotId ?: return
        val name = _uiState.value.renameText
        viewModelScope.launch {
            store.rename(id, name)
        }
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            renamingSlotId = null,
            renameText = "",
        )
    }

    fun onRenameCancel() {
        _uiState.value = _uiState.value.copy(
            showRenameDialog = false,
            renamingSlotId = null,
            renameText = "",
        )
    }

    fun onRenameTextChange(text: String) {
        _uiState.value = _uiState.value.copy(renameText = text)
    }

    fun onImportSuccessConsumed() {
        _uiState.value = _uiState.value.copy(importSuccess = false)
    }
}
