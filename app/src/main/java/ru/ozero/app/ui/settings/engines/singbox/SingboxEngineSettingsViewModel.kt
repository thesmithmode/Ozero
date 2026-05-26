package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.V2RayFmt
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
import javax.inject.Inject

enum class SortOrder {
    BY_LATENCY,
    BY_NAME,
    ;

    companion object {
        fun fromOrdinal(value: Int): SortOrder = entries.getOrElse(value) { BY_LATENCY }
    }
}

sealed class CustomLinkError {
    data object Empty : CustomLinkError()
    data class ParseFailed(val cause: String) : CustomLinkError()
    data class SaveFailed(val cause: String) : CustomLinkError()
}

data class SingboxSettingsUiState(
    val groups: List<SubscriptionGroup> = emptyList(),
    val expandedGroupId: Long? = null,
    val groupProfiles: Map<Long, List<ProxyProfile>> = emptyMap(),
    val selectedProfileId: Long? = null,
    val customLinkInput: String = "",
    val customLinkSaved: Boolean = false,
    val customLinkError: CustomLinkError? = null,
    val isRefreshing: Set<Long> = emptySet(),
    val isPinging: Set<Long> = emptySet(),
    val isAutoSelecting: Boolean = false,
    val isAutoSelectMode: Boolean = false,
    val groupRefreshErrors: Map<Long, String> = emptyMap(),
    val sortOrder: SortOrder = SortOrder.BY_LATENCY,
    val isRestoringDefaults: Boolean = false,
    val restoreError: String? = null,
    val showAddGroupDialog: Boolean = false,
    val addGroupName: String = "",
    val addGroupUrl: String = "",
    val addGroupError: String? = null,
)

@HiltViewModel
class SingboxEngineSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val groupDao: SubscriptionGroupDao,
    private val profileDao: ProxyProfileDao,
    private val rawUpdater: RawUpdater,
    private val groupSeeder: GroupSeeder,
    private val probeService: SingboxProbeService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SingboxSettingsUiState())
    private var pingJob: Job? = null

    val state: StateFlow<SingboxSettingsUiState> = combine(
        groupDao.getAllFlow(),
        dataStore.data,
        _uiState,
    ) { groups, prefs, ui ->
        val sort = SortOrder.fromOrdinal(prefs[SORT_ORDER_KEY] ?: 0)
        val sortedProfiles = ui.groupProfiles.mapValues { (_, profiles) ->
            when (sort) {
                SortOrder.BY_LATENCY -> profiles.sortedWith(
                    compareBy { if (it.latencyMs < 0) Int.MAX_VALUE else it.latencyMs },
                )
                SortOrder.BY_NAME -> profiles.sortedBy { it.name.lowercase() }
            }
        }
        ui.copy(
            groups = groups.sortedBy { it.userOrder },
            selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY],
            isAutoSelectMode = prefs[SingboxProbeService.SELECTED_PROFILE_KEY] == -1L,
            groupProfiles = sortedProfiles,
            sortOrder = sort,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SingboxSettingsUiState())

    fun onGroupExpand(groupId: Long) {
        if (_uiState.value.expandedGroupId == groupId) {
            _uiState.update { it.copy(expandedGroupId = null) }
            return
        }
        viewModelScope.launch {
            val profiles = profileDao.getByGroupId(groupId)
            _uiState.update {
                it.copy(
                    expandedGroupId = groupId,
                    groupProfiles = it.groupProfiles + (groupId to profiles),
                )
            }
            if (profiles.isEmpty()) {
                refreshGroupInternal(groupId)
            }
        }
    }

    fun onProfileSelect(profile: ProxyProfile) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SingboxProbeService.SELECTED_PROFILE_KEY] = profile.id
                prefs[SingboxProbeService.BEAN_KEY] = profile.beanBlob
            }
            _uiState.update { it.copy(customLinkSaved = false) }
        }
    }

    fun onSetAutoSelect(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                if (enabled) {
                    prefs[SingboxProbeService.SELECTED_PROFILE_KEY] = -1L
                } else {
                    prefs.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
                }
                prefs.remove(SingboxProbeService.BEAN_KEY)
            }
        }
    }

    fun onRefreshGroup(groupId: Long) {
        viewModelScope.launch { refreshGroupInternal(groupId) }
    }

    fun onRefreshAll() {
        viewModelScope.launch {
            val groups = groupDao.getAll()
            groups.map { group -> async { refreshGroupInternal(group.id) } }.awaitAll()
        }
    }

    fun onPingAll() {
        pingJob?.cancel()
        _uiState.update { it.copy(isPinging = emptySet()) }
        pingJob = viewModelScope.launch {
            val groups = groupDao.getAll()
            groups.map { group ->
                async {
                    val profiles = profileDao.getByGroupId(group.id)
                    if (profiles.isEmpty()) return@async
                    _uiState.update { it.copy(isPinging = it.isPinging + group.id) }
                    probeService.probeAndAutoSelect(profiles, viewModelScope)
                    val updated = profileDao.getByGroupId(group.id)
                    _uiState.update {
                        it.copy(
                            isPinging = it.isPinging - group.id,
                            groupProfiles = it.groupProfiles + (group.id to updated),
                        )
                    }
                }
            }.awaitAll()
        }
    }

    fun onCancelPing() {
        pingJob?.cancel()
        pingJob = null
        _uiState.update { it.copy(isPinging = emptySet()) }
    }

    fun onAutoSelectBest() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAutoSelecting = true) }
            val allProfiles = groupDao.getAll().flatMap { profileDao.getByGroupId(it.id) }
            if (allProfiles.isNotEmpty()) {
                probeService.probeAndAutoSelect(allProfiles, viewModelScope)
                val groupIds = _uiState.value.groupProfiles.keys
                val refreshed = groupIds.associateWith { gid -> profileDao.getByGroupId(gid) }
                _uiState.update { it.copy(groupProfiles = it.groupProfiles + refreshed) }
            }
            _uiState.update { it.copy(isAutoSelecting = false) }
        }
    }

    fun onPingGroup(groupId: Long) {
        pingJob?.cancel()
        _uiState.update { it.copy(isPinging = emptySet()) }
        pingJob = viewModelScope.launch {
            val profiles = profileDao.getByGroupId(groupId)
            if (profiles.isEmpty()) return@launch
            _uiState.update { it.copy(isPinging = it.isPinging + groupId) }
            probeService.probeAndAutoSelect(profiles, viewModelScope)
            val updated = profileDao.getByGroupId(groupId)
            _uiState.update {
                it.copy(
                    isPinging = it.isPinging - groupId,
                    groupProfiles = it.groupProfiles + (groupId to updated),
                )
            }
        }
    }

    private suspend fun refreshGroupInternal(groupId: Long) {
        val group = groupDao.getById(groupId) ?: return
        _uiState.update {
            it.copy(
                isRefreshing = it.isRefreshing + groupId,
                groupRefreshErrors = it.groupRefreshErrors - groupId,
            )
        }
        val result = rawUpdater.refresh(group)
        val profiles = profileDao.getByGroupId(groupId)
        if (result.isSuccess && profiles.isNotEmpty()) {
            probeService.probeAndAutoSelect(profiles, viewModelScope)
        }
        _uiState.update {
            val errorMsg = if (result.isFailure && profiles.isEmpty()) {
                result.exceptionOrNull()?.message ?: "error"
            } else {
                null
            }
            it.copy(
                isRefreshing = it.isRefreshing - groupId,
                groupProfiles = it.groupProfiles + (groupId to profileDao.getByGroupId(groupId)),
                groupRefreshErrors = if (errorMsg != null) {
                    it.groupRefreshErrors + (groupId to errorMsg)
                } else {
                    it.groupRefreshErrors - groupId
                },
            )
        }
    }

    fun onAddGroupDialog(show: Boolean) = _uiState.update {
        if (show) {
            it.copy(showAddGroupDialog = true)
        } else {
            it.copy(showAddGroupDialog = false, addGroupName = "", addGroupUrl = "", addGroupError = null)
        }
    }

    fun onAddGroupNameChanged(name: String) = _uiState.update { it.copy(addGroupName = name) }

    fun onAddGroupUrlChanged(url: String) = _uiState.update { it.copy(addGroupUrl = url) }

    fun onAddGroupConfirm() {
        val name = _uiState.value.addGroupName.trim()
        val url = _uiState.value.addGroupUrl.trim()
        if (name.isEmpty() || url.isEmpty()) {
            _uiState.update { it.copy(addGroupError = "empty") }
            return
        }
        viewModelScope.launch {
            groupDao.insert(SubscriptionGroup(name = name, subscriptionUrl = url, userOrder = state.value.groups.size))
            _uiState.update {
                it.copy(showAddGroupDialog = false, addGroupName = "", addGroupUrl = "", addGroupError = null)
            }
        }
    }

    fun onDeleteGroup(group: SubscriptionGroup) {
        viewModelScope.launch { groupDao.delete(group) }
    }

    fun onCustomLinkChanged(text: String) {
        _uiState.update { it.copy(customLinkInput = text, customLinkError = null, customLinkSaved = false) }
    }

    fun onSaveCustomLink() {
        val raw = _uiState.value.customLinkInput.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(customLinkError = CustomLinkError.Empty) }
            return
        }
        val bean = runCatching { V2RayFmt.parseVLESS(raw) }
            .getOrElse { e ->
                _uiState.update { it.copy(customLinkError = CustomLinkError.ParseFailed(e.message ?: "")) }
                return
            }
        val blob = runCatching { KryoSerializer.serialize(bean) }
            .getOrElse { e ->
                _uiState.update { it.copy(customLinkError = CustomLinkError.SaveFailed(e.message ?: "")) }
                return
            }
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SingboxProbeService.BEAN_KEY] = blob
                prefs.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
            }
            _uiState.update { it.copy(customLinkSaved = true, customLinkError = null) }
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SORT_ORDER_KEY] = order.ordinal }
        }
    }

    fun onRestoreDefaults() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoringDefaults = true, restoreError = null) }
            val error = runCatching {
                val json = appContext.assets.open("singbox/preset_groups.json").bufferedReader().readText()
                val obj = org.json.JSONObject(json)
                val arr = obj.getJSONArray("groups")
                val presets = (0 until arr.length()).map { i ->
                    val g = arr.getJSONObject(i)
                    GroupSeeder.PresetGroup(name = g.getString("name"), url = g.getString("url"))
                }
                groupSeeder.seedPresets(presets)
            }.exceptionOrNull()
            _uiState.update {
                it.copy(
                    isRestoringDefaults = false,
                    restoreError = error?.let { e -> e.message ?: e.javaClass.simpleName },
                )
            }
        }
    }

    companion object {
        private val SORT_ORDER_KEY = intPreferencesKey("singbox_sort_order")
    }
}
