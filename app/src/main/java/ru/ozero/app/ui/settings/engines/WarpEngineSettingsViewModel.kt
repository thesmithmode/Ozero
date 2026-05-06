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
import ru.ozero.enginewarp.AwgParams
import ru.ozero.enginewarp.WarpAutoConfig
import ru.ozero.enginewarp.WarpConfig
import ru.ozero.enginewarp.WarpConfigSlot
import ru.ozero.enginewarp.WarpConfigSlotStore
import ru.ozero.enginewarp.WarpFileImporter
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
)

data class WarpSettingsUiState(
    val slots: List<WarpConfigSlot> = emptyList(),
    val activeSlotId: String? = null,
    val isRegistering: Boolean = false,
    val errorMessage: String? = null,
    val progressText: String? = null,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val importSuccess: Boolean = false,
    val editDraft: WarpEditDraft? = null,
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
        viewModelScope.launch {
            runCatching { store.migrateIfNeeded() }.onFailure { t ->
                _uiState.value = _uiState.value.copy(errorMessage = t.message ?: "migration failed")
            }
        }
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
                    val parts = progress.split("/")
                    val current = parts.getOrNull(0)?.toIntOrNull() ?: 0
                    val total = parts.getOrNull(1)?.toIntOrNull() ?: 0
                    _uiState.value = _uiState.value.copy(
                        progressText = "Зеркало $progress",
                        progressCurrent = current,
                        progressTotal = total,
                    )
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
                    val id = store.addSlot(suggestedName, cfg)
                    store.setActive(id)
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
            _uiState.value = _uiState.value.copy(errorMessage = VALIDATION_REQUIRED_FIELDS)
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
            awgParams = AwgParams(
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
        )
        val slotId = draft.slotId
        val name = draft.name.trim().ifBlank { "WARP" }
        viewModelScope.launch {
            runCatching { store.updateSlot(slotId, name, config) }
                .onSuccess { _uiState.value = _uiState.value.copy(editDraft = null) }
                .onFailure { t ->
                    _uiState.value = _uiState.value.copy(errorMessage = t.message ?: "save failed")
                }
        }
    }

    fun onEditCancel() {
        _uiState.value = _uiState.value.copy(editDraft = null)
    }

    fun onImportSuccessConsumed() {
        _uiState.value = _uiState.value.copy(importSuccess = false)
    }

    private companion object {
        const val VALIDATION_REQUIRED_FIELDS = "PrivateKey, Endpoint, PublicKey (Peer), Address обязательны"
    }
}
