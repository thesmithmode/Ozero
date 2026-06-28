package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.ozero.app.R
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.DoHProvider
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigDuplicateException
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpEditDraft
import ru.ozero.enginewarp.WarpFileImporter
import ru.ozero.enginewarp.WarpIniBuilder
import ru.ozero.enginewarp.WarpSettingsUiState
import ru.ozero.enginewarp.buildNextWarpSlotName
import ru.ozero.enginewarp.draftFromSlot
import ru.ozero.enginewarp.hasRequiredFields
import ru.ozero.enginewarp.toWarpConfig
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@HiltViewModel
class WarpEngineSettingsViewModel @Inject constructor(
    private val store: WarpConfigSlotStore,
    private val autoConfig: WarpAutoConfig,
    private val fileImporter: WarpFileImporter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WarpSettingsUiState())
    val uiState: StateFlow<WarpSettingsUiState> = _uiState.asStateFlow()
    private var registerJob: Job? = null
    private val autoTriggered = AtomicBoolean(false)

    init {
        viewModelScope.launch {
            val migrationOk = runCatching { store.migrateIfNeeded() }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(errorMessage = t.message ?: "migration failed")
                }
                .isSuccess
            store.slots().collect { slots ->
                _uiState.value = _uiState.value.copy(
                    slots = slots,
                    activeSlotId = slots.firstOrNull { it.isActive }?.id,
                )
                val canAutoTrigger = migrationOk &&
                    slots.isEmpty() &&
                    !_uiState.value.isRegistering &&
                    autoConfig.remainingCooldownMs() == 0L
                if (canAutoTrigger && autoTriggered.compareAndSet(false, true)) {
                    onGenerate()
                }
            }
        }
    }

    val selectedDoHProvider: StateFlow<DoHProvider> = uiState.map {
        it.editDraft?.doHProvider ?: WarpConfig.DEFAULT_DOH_PROVIDER
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WarpConfig.DEFAULT_DOH_PROVIDER,
    )

    fun onSetDoHProvider(provider: DoHProvider) {
        _uiState.value = _uiState.value.copy(
            editDraft = _uiState.value.editDraft?.copy(doHProvider = provider),
        )
    }

    val cooldownRemainingMs: StateFlow<Long> = flow {
        while (true) {
            emit(autoConfig.remainingCooldownMs())
            delay(COOLDOWN_POLL_INTERVAL_MS)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(0),
        initialValue = 0L,
    )

    fun onGenerate() {
        if (_uiState.value.isRegistering) return
        _uiState.value = _uiState.value.copy(
            isRegistering = true,
            errorMessage = null,
            errorMessageRes = null,
            progressMirror = null,
        )
        registerJob = viewModelScope.launch {
            try {
                val result = autoConfig.register(onProgress = { progress ->
                    val parts = progress.split("/")
                    val current = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val total = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    _uiState.value = _uiState.value.copy(
                        progressMirror = progress,
                        progressCurrent = current,
                        progressTotal = total,
                    )
                })
                result.fold(
                    onSuccess = { registered ->
                        val generatedName = buildNextWarpSlotName(store.slots().first())
                        runCatching { store.addSlot(generatedName, registered.config, registered.rawIni) }
                            .onFailure { PersistentLoggers.warn(TAG, "generated addSlot failed: ${it.message}") }
                        _uiState.value = _uiState.value.copy(
                            isRegistering = false,
                            errorMessage = null,
                            errorMessageRes = null,
                            progressMirror = null,
                        )
                    },
                    onFailure = { t ->
                        PersistentLoggers.warn(TAG, "register all mirrors failed: ${t.message}")
                        _uiState.value = _uiState.value.copy(
                            isRegistering = false,
                            errorMessage = null,
                            errorMessageRes = R.string.warp_register_error_hint,
                            progressMirror = null,
                        )
                    },
                )
            } catch (ce: CancellationException) {
                _uiState.value = _uiState.value.copy(isRegistering = false, progressMirror = null)
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

    fun onImportFile(stream: InputStream, displayName: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = fileImporter.import(stream)
            result.fold(
                onSuccess = { imported ->
                    val name = importedSlotName(displayName, store.slots().first())
                    runCatching { store.addSlot(name, imported.config, imported.rawIni) }
                        .onSuccess { id ->
                            store.setActive(id)
                            _uiState.value = _uiState.value.copy(
                                errorMessage = null,
                                errorMessageRes = null,
                                importSuccessCount = _uiState.value.importSuccessCount + 1,
                            )
                        }
                        .onFailure { t ->
                            if (t is WarpConfigDuplicateException) {
                                _uiState.value = _uiState.value.copy(
                                    errorMessage = null,
                                    errorMessageRes = R.string.warp_duplicate_hint,
                                )
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    errorMessage = t.message ?: "import failed",
                                    errorMessageRes = null,
                                )
                            }
                        }
                },
                onFailure = { t ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = t.message ?: "import failed",
                        errorMessageRes = null,
                    )
                },
            )
        }
    }

    private fun importedSlotName(displayName: String?, existing: List<WarpConfigSlot>): String =
        displayName?.trim()?.takeIf { it.isNotBlank() } ?: buildNextWarpSlotName(existing)

    fun onClashYamlRejected() {
        _uiState.update { it.copy(errorMessage = "Формат Clash не поддерживается") }
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

    fun onStartEdit(id: String) {
        val slot = _uiState.value.slots.firstOrNull { it.id == id } ?: return
        _uiState.value = _uiState.value.copy(
            editDraft = draftFromSlot(slot),
        )
    }

    fun onEditDraftChange(draft: WarpEditDraft) {
        _uiState.value = _uiState.value.copy(editDraft = draft)
    }

    fun onSaveEdit() {
        val draft = _uiState.value.editDraft ?: return
        if (!hasRequiredFields(draft)) {
            _uiState.value = _uiState.value.copy(
                errorMessage = null,
                errorMessageRes = R.string.warp_validation_required_fields,
            )
            return
        }
        val existingSlot = _uiState.value.slots.firstOrNull { it.id == draft.slotId }
        val existingConfig = existingSlot?.config
        val basAwg = existingConfig?.awgParams ?: AwgParams()
        val config = draft.toWarpConfig(basAwg).copy(
            accountLicense = existingConfig?.accountLicense ?: "",
            allowedIps = existingConfig?.allowedIps ?: WarpConfig.DEFAULT_ALLOWED_IPS,
        )
        val slotId = draft.slotId
        val name = draft.name.trim().ifBlank { "WARP" }
        val rawIni = existingSlot?.rawIniOverride?.let {
            runCatching { WarpIniBuilder.build(config, it) }.getOrElse { WarpIniBuilder.build(config) }
        }
        val endpointList = if (existingConfig?.peerEndpoint?.trim() == config.peerEndpoint.trim()) {
            existingSlot?.endpointList ?: emptyList()
        } else {
            listOf(config.peerEndpoint)
        }
        viewModelScope.launch {
            runCatching { store.updateSlot(slotId, name, config, rawIni, endpointList) }
                .onSuccess {
                    _uiState.value = _uiState.value.copy(
                        editDraft = null,
                        errorMessage = null,
                        errorMessageRes = null,
                    )
                }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = t.message ?: "save failed",
                        errorMessageRes = null,
                    )
                }
        }
    }

    fun onEditCancel() {
        _uiState.value = _uiState.value.copy(editDraft = null)
    }

    private companion object {
        const val COOLDOWN_POLL_INTERVAL_MS = 1_000L
        const val TAG = "WarpSettingsVM"
    }
}
