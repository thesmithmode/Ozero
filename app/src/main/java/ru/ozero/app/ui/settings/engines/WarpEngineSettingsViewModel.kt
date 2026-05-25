package ru.ozero.app.ui.settings.engines

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import ru.ozero.enginewarp.ClashYamlParser
import ru.ozero.enginewarp.DoHProvider
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigDuplicateException
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpEndpointProber
import ru.ozero.enginewarp.WarpFileImporter
import ru.ozero.enginewarp.WarpIniBuilder
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class WarpEditDraft(
    val slotId: String,
    val name: String,
    val endpoint: String,
    val privateKey: String,
    val publicKey: String,
    val peerPublicKey: String,
    val addressV4: String,
    val addressV6: String,
    val dns: String,
    val mtu: String,
    val keepalive: String,
    val jc: String,
    val jmin: String,
    val jmax: String,
    val s1: String,
    val s2: String,
    val h1: String,
    val h2: String,
    val h3: String,
    val h4: String,
    val doHProvider: DoHProvider = DoHProvider.SYSTEM,
)

data class WarpSettingsUiState(
    val slots: List<WarpConfigSlot> = emptyList(),
    val activeSlotId: String? = null,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
    @StringRes val errorMessageRes: Int? = null,
    val progressMirror: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val importSuccessCount: Int = 0,
    val editDraft: WarpEditDraft? = null,
    val isProvingEndpoints: Boolean = false,
    val provingProgress: String = "",
)

@HiltViewModel
class WarpEngineSettingsViewModel @Inject constructor(
    private val store: WarpConfigSlotStore,
    private val autoConfig: WarpAutoConfig,
    private val fileImporter: WarpFileImporter,
    private val endpointProber: WarpEndpointProber,
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
        it.editDraft?.doHProvider ?: DoHProvider.SYSTEM
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = DoHProvider.SYSTEM,
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
                        val generatedName = buildOzeroName(store.slots().first())
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

    fun onImportFile(stream: InputStream) {
        viewModelScope.launch {
            val result = fileImporter.import(stream)
            result.fold(
                onSuccess = { imported ->
                    val name = buildOzeroName(store.slots().first())
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
        val cfg = slot.config
        val awg = cfg.awgParams
        _uiState.value = _uiState.value.copy(
            editDraft = WarpEditDraft(
                slotId = id,
                name = slot.name,
                endpoint = cfg.peerEndpoint,
                privateKey = cfg.privateKey,
                publicKey = cfg.publicKey,
                peerPublicKey = cfg.peerPublicKey,
                addressV4 = cfg.interfaceAddressV4,
                addressV6 = cfg.interfaceAddressV6,
                dns = cfg.dnsServers.joinToString(", "),
                mtu = cfg.mtu.toString(),
                keepalive = cfg.keepaliveSeconds.toString(),
                jc = awg.junkPacketCount.toString(),
                jmin = awg.junkPacketMinSize.toString(),
                jmax = awg.junkPacketMaxSize.toString(),
                s1 = awg.initPacketJunkSize.toString(),
                s2 = awg.responsePacketJunkSize.toString(),
                h1 = awg.initPacketMagicHeader.toString(),
                h2 = awg.responsePacketMagicHeader.toString(),
                h3 = awg.cookieReplyMagicHeader.toString(),
                h4 = awg.transportMagicHeader.toString(),
                doHProvider = cfg.doHProvider,
            ),
        )
    }

    fun onEditDraftChange(draft: WarpEditDraft) {
        _uiState.value = _uiState.value.copy(editDraft = draft)
    }

    fun onSaveEdit() {
        val draft = _uiState.value.editDraft ?: return
        val requiredFields = listOf(draft.privateKey, draft.peerPublicKey, draft.endpoint, draft.addressV4)
        if (requiredFields.any { it.isBlank() }) {
            _uiState.value = _uiState.value.copy(
                errorMessage = null,
                errorMessageRes = R.string.warp_validation_required_fields,
            )
            return
        }
        val mtu = draft.mtu.toIntOrNull() ?: WarpConfig.DEFAULT_MTU
        val keepalive = draft.keepalive.toIntOrNull() ?: WarpConfig.DEFAULT_KEEPALIVE
        val dns = draft.dns.split(",").map { it.trim() }.filter { it.isNotBlank() }
            .ifEmpty { WarpConfig.DEFAULT_DNS }
        val jc = draft.jc.toIntOrNull() ?: AwgParams.DEFAULT_JC
        val jmin = draft.jmin.toIntOrNull() ?: AwgParams.DEFAULT_JMIN
        val jmax = draft.jmax.toIntOrNull() ?: AwgParams.DEFAULT_JMAX
        val s1 = draft.s1.toIntOrNull() ?: AwgParams.DEFAULT_S1
        val s2 = draft.s2.toIntOrNull() ?: AwgParams.DEFAULT_S2
        val h1 = draft.h1.toLongOrNull() ?: AwgParams.DEFAULT_H1
        val h2 = draft.h2.toLongOrNull() ?: AwgParams.DEFAULT_H2
        val h3 = draft.h3.toLongOrNull() ?: AwgParams.DEFAULT_H3
        val h4 = draft.h4.toLongOrNull() ?: AwgParams.DEFAULT_H4
        val existingSlot = _uiState.value.slots.firstOrNull { it.id == draft.slotId }
        val basAwg = existingSlot?.config?.awgParams ?: AwgParams()
        val config = WarpConfig(
            privateKey = draft.privateKey.trim(),
            publicKey = draft.publicKey.trim(),
            peerPublicKey = draft.peerPublicKey.trim(),
            peerEndpoint = draft.endpoint.trim(),
            interfaceAddressV4 = draft.addressV4.trim(),
            interfaceAddressV6 = draft.addressV6.trim(),
            mtu = mtu,
            dnsServers = dns,
            keepaliveSeconds = keepalive,
            awgParams = basAwg.copy(
                junkPacketCount = jc,
                junkPacketMinSize = minOf(jmin, jmax),
                junkPacketMaxSize = maxOf(jmin, jmax),
                initPacketJunkSize = s1,
                responsePacketJunkSize = s2,
                initPacketMagicHeader = h1,
                responsePacketMagicHeader = h2,
                cookieReplyMagicHeader = h3,
                transportMagicHeader = h4,
            ),
            doHProvider = draft.doHProvider,
        )
        val slotId = draft.slotId
        val name = draft.name.trim().ifBlank { "WARP" }
        viewModelScope.launch {
            runCatching { store.updateSlot(slotId, name, config, existingSlot?.rawIniOverride) }
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

    fun onStartTweaks() {
        val slot = _uiState.value.slots.firstOrNull { it.isActive }
            ?: _uiState.value.slots.firstOrNull() ?: return
        onStartEdit(slot.id)
        _uiState.value = _uiState.value.copy(showTweaks = true)
    }

    fun onEditCancel() {
        _uiState.value = _uiState.value.copy(editDraft = null, showTweaks = false)
    }

    private companion object {
        const val COOLDOWN_POLL_INTERVAL_MS = 1_000L
        const val TAG = "WarpSettingsVM"

        fun buildOzeroName(existing: List<WarpConfigSlot>): String {
            val used = existing.mapNotNull { it.name.removePrefix("Ozero-").toIntOrNull() }.toSet()
            var n = 1
            while (n in used) n++
            return "Ozero-$n"
        }
    }
}
