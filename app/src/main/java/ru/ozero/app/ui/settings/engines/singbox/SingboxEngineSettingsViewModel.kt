package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import ru.ozero.singboxsubscription.RawUpdater
import javax.inject.Inject

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
    val groupRefreshErrors: Map<Long, String> = emptyMap(),
    val showAddGroupDialog: Boolean = false,
    val addGroupName: String = "",
    val addGroupUrl: String = "",
    val addGroupError: String? = null,
)

@HiltViewModel
class SingboxEngineSettingsViewModel @Inject constructor(
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val groupDao: SubscriptionGroupDao,
    private val profileDao: ProxyProfileDao,
    private val rawUpdater: RawUpdater,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SingboxSettingsUiState())

    val state: StateFlow<SingboxSettingsUiState> = combine(
        groupDao.getAllFlow(),
        dataStore.data,
        _uiState,
    ) { groups, prefs, ui ->
        ui.copy(
            groups = groups.sortedBy { it.userOrder },
            selectedProfileId = prefs[SELECTED_PROFILE_KEY],
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SingboxSettingsUiState())

    fun onGroupExpand(groupId: Long) {
        val current = _uiState.value.expandedGroupId
        if (current == groupId) {
            _uiState.update { it.copy(expandedGroupId = null) }
            return
        }
        _uiState.update { it.copy(expandedGroupId = groupId) }
        viewModelScope.launch {
            val profiles = profileDao.getByGroupId(groupId)
            _uiState.update { it.copy(groupProfiles = it.groupProfiles + (groupId to profiles)) }
            if (profiles.isEmpty()) {
                refreshGroupInternal(groupId)
            }
        }
    }

    fun onProfileSelect(profile: ProxyProfile) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SELECTED_PROFILE_KEY] = profile.id
                prefs[BEAN_KEY] = profile.beanBlob
            }
            _uiState.update { it.copy(customLinkSaved = false) }
        }
    }

    fun onRefreshGroup(groupId: Long) {
        viewModelScope.launch { refreshGroupInternal(groupId) }
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
        _uiState.update {
            val errorMsg = if (result.isFailure && profiles.isEmpty()) {
                result.exceptionOrNull()?.message ?: "error"
            } else {
                null
            }
            it.copy(
                isRefreshing = it.isRefreshing - groupId,
                groupProfiles = it.groupProfiles + (groupId to profiles),
                groupRefreshErrors = if (errorMsg != null) {
                    it.groupRefreshErrors + (groupId to errorMsg)
                } else {
                    it.groupRefreshErrors - groupId
                },
            )
        }
    }

    fun onAddGroupDialogOpen() = _uiState.update { it.copy(showAddGroupDialog = true) }

    fun onAddGroupDialogDismiss() = _uiState.update {
        it.copy(showAddGroupDialog = false, addGroupName = "", addGroupUrl = "", addGroupError = null)
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
                prefs[BEAN_KEY] = blob
                prefs.remove(SELECTED_PROFILE_KEY)
            }
            _uiState.update { it.copy(customLinkSaved = true, customLinkError = null) }
        }
    }

    companion object {
        private val BEAN_KEY = byteArrayPreferencesKey("singbox_vless_bean")
        private val SELECTED_PROFILE_KEY = longPreferencesKey("singbox_selected_profile_id")
    }
}
