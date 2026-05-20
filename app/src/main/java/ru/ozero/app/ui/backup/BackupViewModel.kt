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
import ru.ozero.corebackup.AppBackupData
import ru.ozero.corebackup.AppBackupManager
import ru.ozero.corebackup.AppBackupSerializer
import ru.ozero.corebackup.BackupCategory
import ru.ozero.enginescore.PersistentLoggers
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: AppBackupManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var pendingImport: AppBackupData? = null

    fun export(context: Context, uri: Uri, categories: Set<BackupCategory>) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.InProgress
            runCatching {
                val data = backupManager.export(categories)
                val bytes = AppBackupSerializer.serializeEncrypted(data)
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(bytes)
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

    fun beginImport(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = BackupUiState.InProgress
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IOException("Cannot open input stream for $uri")
                AppBackupSerializer.deserializeAuto(bytes)
            }.fold(
                onSuccess = { data ->
                    pendingImport = data
                    _uiState.value = BackupUiState.PendingImport(BackupCategory.availableIn(data))
                },
                onFailure = { e ->
                    PersistentLoggers.error(TAG, "import parse failed: ${e.message}")
                    _uiState.value = BackupUiState.Error(e.message ?: "Unknown error")
                },
            )
        }
    }

    fun confirmImport(categories: Set<BackupCategory>) {
        val data = pendingImport ?: return
        viewModelScope.launch {
            _uiState.value = BackupUiState.InProgress
            runCatching {
                backupManager.import(data, categories)
            }.fold(
                onSuccess = {
                    pendingImport = null
                    _uiState.value = BackupUiState.ImportSuccess
                },
                onFailure = { e ->
                    PersistentLoggers.error(TAG, "import failed: ${e.message}")
                    pendingImport = null
                    _uiState.value = BackupUiState.Error(e.message ?: "Unknown error")
                },
            )
        }
    }

    fun cancelImport() {
        pendingImport = null
        _uiState.value = BackupUiState.Idle
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
    data class PendingImport(val availableCategories: Set<BackupCategory>) : BackupUiState
    data class Error(val message: String) : BackupUiState
}
