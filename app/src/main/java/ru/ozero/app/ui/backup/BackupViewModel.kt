package ru.ozero.app.ui.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.ozero.corebackup.AppBackupManager
import ru.ozero.corebackup.AppBackupSerializer
import ru.ozero.enginescore.PersistentLoggers
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: AppBackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    fun export(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.InProgress
            runCatching {
                val data = backupManager.export()
                val json = AppBackupSerializer.serialize(data)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw IOException("Cannot open output stream for $uri")
            }.fold(
                onSuccess = { _uiState.value = BackupUiState.ExportSuccess },
                onFailure = { e ->
                    PersistentLoggers.error(TAG, "export failed: ${e.message}")
                    _uiState.value = BackupUiState.Error(e.message ?: "Unknown error")
                },
            )
        }
    }

    fun import(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.InProgress
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw IOException("Cannot open input stream for $uri")
                val data = AppBackupSerializer.deserialize(json)
                backupManager.import(data)
            }.fold(
                onSuccess = { _uiState.value = BackupUiState.ImportSuccess },
                onFailure = { e ->
                    PersistentLoggers.error(TAG, "import failed: ${e.message}")
                    _uiState.value = BackupUiState.Error(e.message ?: "Unknown error")
                },
            )
        }
    }

    fun dismissResult() {
        _uiState.value = BackupUiState.Idle
    }

    private companion object {
        const val TAG = "BackupViewModel"
    }
}

sealed interface BackupUiState {
    data object Idle : BackupUiState
    data object InProgress : BackupUiState
    data object ExportSuccess : BackupUiState
    data object ImportSuccess : BackupUiState
    data class Error(val message: String) : BackupUiState
}
