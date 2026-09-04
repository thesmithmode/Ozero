package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.ozero.enginewarp.DnsPresets
import ru.ozero.enginescore.EngineConfig
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginesingbox.SingboxPrefs
import ru.ozero.enginesingbox.prioritizeSingboxAutoProfiles
import ru.ozero.singboxconfig.BeanSupportDecision
import ru.ozero.singboxconfig.ConfigBuilder
import ru.ozero.singboxfmt.AbstractBean
import ru.ozero.singboxfmt.KryoSerializer
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
import ru.ozero.singboxsubscription.SubscriptionRefreshErrorCode
import ru.ozero.singboxsubscription.isSupportedSubscriptionUrl
import ru.ozero.singboxsubscription.parser.RawShareLinksParser
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

enum class SortOrder {
    BY_LATENCY,
    BY_NAME,
    ;

    companion object {
        fun fromOrdinal(value: Int): SortOrder = entries.getOrElse(value) { BY_LATENCY }
    }
}

data class SingboxSettingsUiState(
    val groups: List<SubscriptionGroup> = emptyList(),
    val allProfiles: List<ProxyProfile> = emptyList(),
    val expandedGroupId: Long? = null,
    val groupProfiles: Map<Long, List<ProxyProfile>> = emptyMap(),
    val selectedProfileId: Long? = null,
    val chainProfileIds: List<Long> = emptyList(),
    val isRefreshing: Set<Long> = emptySet(),
    val isPinging: Set<Long> = emptySet(),
    val testingProfileIds: Set<Long> = emptySet(),
    val isAutoSelecting: Boolean = false,
    val isAutoSelectMode: Boolean = false,
    val groupRefreshErrors: Map<Long, String> = emptyMap(),
    val sortOrder: SortOrder = SortOrder.BY_LATENCY,
    val isRestoringDefaults: Boolean = false,
    val dnsPresetId: String = DnsPresets.ALL.first().id,
    val dnsServers: List<String> = EngineConfig.Singbox.DEFAULT_DNS_SERVERS,
    val restoreError: String? = null,
    val showAddGroupDialog: Boolean = false,
    val addGroupName: String = "",
    val addGroupUrl: String = "",
    val addGroupError: String? = null,
    val showAddMenu: Boolean = false,
    val showAddManualLinksDialog: Boolean = false,
    val manualLinksInput: String = "",
    val manualLinksGroupName: String = "",
    val manualLinksError: String? = null,
    val pendingInsecureRefreshGroupId: Long? = null,
    val probeTimeoutSeconds: Int = SingboxProbeService.DEFAULT_PROBE_TIMEOUT_MS / 1_000,
)

@Suppress("TooManyFunctions")
@HiltViewModel
class SingboxEngineSettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    @SingboxPrefs private val dataStore: DataStore<Preferences>,
    private val groupDao: SubscriptionGroupDao,
    private val profileDao: ProxyProfileDao,
    private val proxyChainDao: ProxyChainDao,
    private val rawUpdater: RawUpdater,
    private val groupSeeder: GroupSeeder,
    private val probeService: SingboxProbeService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SingboxSettingsUiState())
    private val testingProfileCounts = ConcurrentHashMap<Long, AtomicInteger>()
    private val pendingInsecureRefreshes = ArrayDeque<InsecureRefreshConsent>()
    private val confirmedInsecureRefreshes = ArrayDeque<InsecureRefreshConsent>()
    private val pendingInsecureRefreshLock = Any()
    private val secureRefreshGenerations = mutableMapOf<Long, Long>()
    private val probeGeneration = AtomicLong()
    private var pingJob: Job? = null
    private var refreshJob: Job? = null
    private var insecureRetryJob: Job? = null

    val state: StateFlow<SingboxSettingsUiState> = combine(
        groupDao.getAllFlow(),
        profileDao.getAllLimitedFlow(MAX_VISIBLE_PROFILES),
        proxyChainDao.getAllFlow(),
        dataStore.data,
        _uiState,
    ) { groups, profiles, chainSteps, prefs, ui ->
        val sort = SortOrder.fromOrdinal(prefs[SORT_ORDER_KEY] ?: 0)
        val dnsServers = prefs[SINGBOX_DNS_SERVERS_KEY]?.toList()?.ifEmpty { null }
            ?: EngineConfig.Singbox.DEFAULT_DNS_SERVERS
        val dnsPresetId = DnsPresets.ALL.firstOrNull { it.servers.toSet() == dnsServers.toSet() }?.id
            ?: DnsPresets.ALL.first().id
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
            allProfiles = profiles,
            selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY],
            isAutoSelectMode = prefs[SingboxProbeService.SELECTED_PROFILE_KEY] == -1L,
            chainProfileIds = chainSteps.map { it.profileId },
            groupProfiles = sortedProfiles,
            sortOrder = sort,
            dnsPresetId = dnsPresetId,
            dnsServers = dnsServers,
            probeTimeoutSeconds = prefs[SingboxProbeService.PROBE_TIMEOUT_MS_KEY]
                .normalizedSingboxProbeTimeoutMs() / 1_000,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SingboxSettingsUiState())

    fun onGroupExpand(groupId: Long) {
        val isCollapsing = _uiState.value.expandedGroupId == groupId
        _uiState.update { it.copy(expandedGroupId = if (isCollapsing) null else groupId) }
        if (isCollapsing) return
        viewModelScope.launch {
            val profiles = profileDao.getByGroupIdLimited(groupId, MAX_VISIBLE_PROFILES)
            _uiState.update {
                it.copy(groupProfiles = it.groupProfiles + (groupId to profiles))
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
            PersistentLoggers.info(TAG, "manual selection persisted profileId=${profile.id}")
        }
    }

    fun onChainProfileAdd(profile: ProxyProfile) {
        viewModelScope.launch {
            val current = proxyChainDao.getAll().map { it.profileId }
            if (profile.id !in current) {
                proxyChainDao.replace(current + profile.id)
            }
        }
    }

    fun onChainProfileRemove(profileId: Long) {
        viewModelScope.launch {
            proxyChainDao.replace(proxyChainDao.getAll().map { it.profileId }.filterNot { it == profileId })
        }
    }

    fun onChainProfileMove(profileId: Long, delta: Int) {
        viewModelScope.launch {
            val current = proxyChainDao.getAll().map { it.profileId }.toMutableList()
            val from = current.indexOf(profileId)
            if (from < 0) return@launch
            val to = (from + delta).coerceIn(0, current.lastIndex)
            if (from != to) {
                current.removeAt(from)
                current.add(to, profileId)
                proxyChainDao.replace(current)
            }
        }
    }

    fun onProbeTimeoutSecondsChange(seconds: Int) {
        val normalizedSeconds = seconds.coerceIn(
            SingboxProbeService.MIN_PROBE_TIMEOUT_MS / 1_000,
            SingboxProbeService.MAX_PROBE_TIMEOUT_MS / 1_000,
        )
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SingboxProbeService.PROBE_TIMEOUT_MS_KEY] = normalizedSeconds * 1_000
            }
        }
    }

    fun onSetAutoSelect(enabled: Boolean) {
        if (!enabled) return
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SingboxProbeService.SELECTED_PROFILE_KEY] = -1L
                prefs.remove(SingboxProbeService.BEAN_KEY)
            }
        }
    }

    fun onRefresh(groupId: Long? = null) {
        val previousJob = refreshJob
        refreshJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            if (groupId != null) {
                refreshGroupInternal(groupId)
            } else {
                groupDao.getAll().map { group -> async { refreshGroupInternal(group.id) } }.awaitAll()
            }
        }
    }

    fun onConfirmInsecureRefresh(confirmed: Boolean) {
        val consent = consumePendingInsecureRefresh() ?: return
        if (!confirmed) return
        enqueueConfirmedInsecureRefresh(consent)
    }

    fun onCancel(ping: Boolean = false, refresh: Boolean = false) {
        if (ping) {
            probeGeneration.incrementAndGet()
            pingJob?.cancel()
            testingProfileCounts.clear()
            _uiState.update {
                it.copy(isPinging = emptySet(), testingProfileIds = emptySet(), isAutoSelecting = false)
            }
        }
        if (refresh) {
            refreshJob?.cancel()
            insecureRetryJob?.cancel()
            clearInsecureRefreshQueues()
            _uiState.update { it.copy(isRefreshing = emptySet()) }
        }
    }

    fun onPing(groupId: Long? = null) {
        val previousJob = pingJob
        pingJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            val generation = probeGeneration.incrementAndGet()
            testingProfileCounts.clear()
            _uiState.update { it.copy(isPinging = emptySet()) }
            val groupIds = if (groupId != null) {
                listOf(groupId)
            } else {
                groupDao.getAll().map { it.id }
            }
            var probeProfiles = emptyList<ProxyProfile>()
            try {
                probeProfiles = groupIds.flatMap { id -> profileDao.getByGroupId(id) }
                val profileGroupIds = probeProfiles.associate { it.id to it.groupId }
                val pendingByGroup = probeProfiles
                    .groupingBy { it.groupId }
                    .eachCount()
                    .mapValues { AtomicInteger(it.value) }
                val completedProfiles = ConcurrentHashMap.newKeySet<Long>()
                _uiState.update { it.copy(isPinging = it.isPinging + pendingByGroup.keys) }
                if (probeProfiles.isNotEmpty()) {
                    probeService.probeAndAutoSelect(
                        profiles = probeProfiles,
                        onProfileTestingChanged = { profileId, isTesting ->
                            onProfileTestingChanged(generation, profileId, isTesting)
                        },
                        onProfileCompleted = { profileId ->
                            if (completedProfiles.add(profileId)) {
                                profileGroupIds[profileId]?.let { completedGroupId ->
                                    val remaining = pendingByGroup[completedGroupId]?.decrementAndGet()
                                    if (remaining == 0) {
                                        _uiState.update {
                                            it.copy(isPinging = it.isPinging - completedGroupId)
                                        }
                                    }
                                }
                            }
                        },
                        updateManualSelection = false,
                    )
                }
            } finally {
                if (generation != probeGeneration.get()) return@launch
                val refreshed = groupIds.associateWith { id ->
                    profileDao.getByGroupIdLimited(id, MAX_VISIBLE_PROFILES)
                }
                _uiState.update {
                    it.copy(
                        isPinging = it.isPinging - groupIds.toSet(),
                        testingProfileIds = it.testingProfileIds -
                            probeProfiles.map { profile -> profile.id }.toSet(),
                        groupProfiles = it.groupProfiles + refreshed,
                    )
                }
            }
        }
    }

    fun onAutoSelectBest() {
        val previousJob = pingJob
        pingJob = viewModelScope.launch {
            previousJob?.cancelAndJoin()
            val generation = probeGeneration.incrementAndGet()
            testingProfileCounts.clear()
            _uiState.update { it.copy(isAutoSelecting = true) }
            try {
                val allProfiles = prioritizeSingboxAutoProfiles(
                    groupDao.getAll().flatMap { profileDao.getAutoCandidatesByGroupId(it.id, MAX_PROFILE_SCAN) },
                    MAX_PROFILE_SCAN,
                )
                if (allProfiles.isNotEmpty()) {
                    probeService.probeAndAutoSelect(
                        profiles = allProfiles,
                        onProfileTestingChanged = { profileId, isTesting ->
                            onProfileTestingChanged(generation, profileId, isTesting)
                        },
                    )
                    val groupIds = _uiState.value.groupProfiles.keys
                    val refreshed = groupIds.associateWith { gid ->
                        profileDao.getByGroupIdLimited(gid, MAX_VISIBLE_PROFILES)
                    }
                    _uiState.update { it.copy(groupProfiles = it.groupProfiles + refreshed) }
                }
            } finally {
                if (generation == probeGeneration.get()) {
                    _uiState.update { it.copy(isAutoSelecting = false, testingProfileIds = emptySet()) }
                }
            }
        }
    }

    private suspend fun refreshGroupInternal(groupId: Long, allowInsecureRetry: Boolean = false) {
        val group = groupDao.getById(groupId) ?: return
        val secureRefreshGeneration = if (allowInsecureRetry) null else beginSecureRefresh(groupId)
        _uiState.update {
            it.copy(
                isRefreshing = it.isRefreshing + groupId,
                groupRefreshErrors = it.groupRefreshErrors - groupId,
            )
        }
        var errorMsg: String? = null
        try {
            val result = rawUpdater.refresh(group, allowInsecureRetry)
            val code = result.exceptionOrNull()?.let { failure ->
                groupDao.getById(groupId)?.lastRefreshErrorCode ?: failure.message
            }
            if (secureRefreshGeneration != null) {
                updateInsecureRefreshEligibility(group, code, secureRefreshGeneration)
            }
            errorMsg = code
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            if (secureRefreshGeneration != null) {
                removeInsecureRefreshIfCurrent(groupId, secureRefreshGeneration)
            }
            errorMsg = t.message ?: "refresh failed"
        } finally {
            val visibleProfiles = profileDao.getByGroupIdLimited(groupId, MAX_VISIBLE_PROFILES)
            _uiState.update {
                it.copy(
                    isRefreshing = it.isRefreshing - groupId,
                    groupProfiles = it.groupProfiles + (groupId to visibleProfiles),
                    groupRefreshErrors = if (errorMsg != null) {
                        it.groupRefreshErrors + (groupId to errorMsg)
                    } else {
                        it.groupRefreshErrors - groupId
                    },
                )
            }
        }
    }

    private fun beginSecureRefresh(groupId: Long): Long = synchronized(pendingInsecureRefreshLock) {
        val generation = (secureRefreshGenerations[groupId] ?: 0L) + 1L
        secureRefreshGenerations[groupId] = generation
        removeInsecureRefreshGroupLocked(groupId)
        generation
    }

    private fun updateInsecureRefreshEligibility(
        group: SubscriptionGroup,
        errorCode: String?,
        generation: Long,
    ) = synchronized(pendingInsecureRefreshLock) {
        if (secureRefreshGenerations[group.id] != generation) return@synchronized
        if (!group.isBuiltin && errorCode == SubscriptionRefreshErrorCode.TLS_CERTIFICATE) {
            enqueuePendingInsecureRefreshLocked(InsecureRefreshConsent(group.id, generation))
        } else {
            removeInsecureRefreshGroupLocked(group.id)
        }
    }

    private fun removeInsecureRefreshIfCurrent(groupId: Long, generation: Long) {
        synchronized(pendingInsecureRefreshLock) {
            if (secureRefreshGenerations[groupId] == generation) {
                removeInsecureRefreshGroupLocked(groupId)
            }
        }
    }

    private fun enqueuePendingInsecureRefreshLocked(consent: InsecureRefreshConsent) {
        if (pendingInsecureRefreshes.none { it.groupId == consent.groupId }) {
            pendingInsecureRefreshes.addLast(consent)
        }
        if (_uiState.value.pendingInsecureRefreshGroupId == null) {
            _uiState.update {
                it.copy(pendingInsecureRefreshGroupId = pendingInsecureRefreshes.peekFirst()?.groupId)
            }
        }
    }

    private fun consumePendingInsecureRefresh(): InsecureRefreshConsent? = synchronized(pendingInsecureRefreshLock) {
        val current = _uiState.value.pendingInsecureRefreshGroupId ?: return@synchronized null
        val consent = pendingInsecureRefreshes.firstOrNull { it.groupId == current }
            ?: return@synchronized null
        pendingInsecureRefreshes.removeFirstOccurrence(consent)
        _uiState.update {
            it.copy(pendingInsecureRefreshGroupId = pendingInsecureRefreshes.peekFirst()?.groupId)
        }
        consent
    }

    private fun enqueueConfirmedInsecureRefresh(consent: InsecureRefreshConsent) {
        synchronized(pendingInsecureRefreshLock) {
            if (secureRefreshGenerations[consent.groupId] != consent.secureGeneration) return
            if (confirmedInsecureRefreshes.none { it.groupId == consent.groupId }) {
                confirmedInsecureRefreshes.addLast(consent)
            }
            if (insecureRetryJob?.isActive == true) return
            insecureRetryJob = viewModelScope.launch {
                while (true) {
                    val nextConsent = pollConfirmedInsecureRefresh() ?: return@launch
                    refreshGroupInternal(nextConsent.groupId, allowInsecureRetry = true)
                }
            }
        }
    }

    private fun pollConfirmedInsecureRefresh(): InsecureRefreshConsent? = synchronized(pendingInsecureRefreshLock) {
        while (true) {
            val next = confirmedInsecureRefreshes.pollFirst()
            if (next == null) {
                insecureRetryJob = null
                return@synchronized null
            }
            if (secureRefreshGenerations[next.groupId] == next.secureGeneration) {
                return@synchronized next
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    }

    private fun removeInsecureRefreshGroupLocked(groupId: Long) {
        pendingInsecureRefreshes.firstOrNull { it.groupId == groupId }?.let {
            pendingInsecureRefreshes.removeFirstOccurrence(it)
        }
        confirmedInsecureRefreshes.firstOrNull { it.groupId == groupId }?.let {
            confirmedInsecureRefreshes.removeFirstOccurrence(it)
        }
        if (_uiState.value.pendingInsecureRefreshGroupId == groupId) {
            _uiState.update {
                it.copy(pendingInsecureRefreshGroupId = pendingInsecureRefreshes.peekFirst()?.groupId)
            }
        }
    }

    private fun clearInsecureRefreshQueues() {
        synchronized(pendingInsecureRefreshLock) {
            pendingInsecureRefreshes.clear()
            confirmedInsecureRefreshes.clear()
            _uiState.update { it.copy(pendingInsecureRefreshGroupId = null) }
        }
    }

    fun onAddGroupDialog(show: Boolean) = _uiState.update {
        if (show) {
            it.copy(showAddGroupDialog = true)
        } else {
            it.copy(showAddGroupDialog = false, addGroupName = "", addGroupUrl = "", addGroupError = null)
        }
    }

    fun onAddGroupFieldChanged(name: String? = null, url: String? = null) = _uiState.update {
        it.copy(
            addGroupName = name ?: it.addGroupName,
            addGroupUrl = url ?: it.addGroupUrl,
            addGroupError = if (url != null) null else it.addGroupError,
        )
    }

    fun onAddGroupConfirm() {
        val url = _uiState.value.addGroupUrl.trim()
        if (url.isEmpty()) {
            _uiState.update { it.copy(addGroupError = "empty") }
            return
        }
        if (!isSupportedSubscriptionUrl(url)) {
            _uiState.update { it.copy(addGroupError = "invalid_url") }
            return
        }
        val rawName = _uiState.value.addGroupName.trim()
        val name = rawName.ifEmpty { "Ozero-${state.value.groups.size + 1}" }
        viewModelScope.launch {
            groupDao.insert(
                SubscriptionGroup(name = name, subscriptionUrl = url, userOrder = nextGroupOrder()),
            )
            _uiState.update {
                it.copy(showAddGroupDialog = false, addGroupName = "", addGroupUrl = "", addGroupError = null)
            }
        }
    }

    fun onDeleteGroup(group: SubscriptionGroup) {
        viewModelScope.launch {
            val profileIds = profileDao.getIdsByGroupId(group.id).toSet()
            dataStore.edit { prefs ->
                val selectedProfileId = prefs[SingboxProbeService.SELECTED_PROFILE_KEY]
                if (selectedProfileId != null && selectedProfileId in profileIds) {
                    prefs.remove(SingboxProbeService.SELECTED_PROFILE_KEY)
                    prefs.remove(SingboxProbeService.BEAN_KEY)
                }
            }
            proxyChainDao.replace(
                proxyChainDao.getAll().map { it.profileId }.filterNot { it in profileIds },
            )
            groupDao.delete(group)
        }
    }

    fun onAllowInsecureSubscriptionTls(group: SubscriptionGroup, enabled: Boolean) {
        if (group.isBuiltin) return
        viewModelScope.launch { groupDao.updateAllowInsecureTls(group.id, enabled) }
    }

    fun onShowAddMenu(show: Boolean) = _uiState.update { it.copy(showAddMenu = show) }

    fun onShowAddManualLinksDialog(show: Boolean) = _uiState.update {
        if (show) {
            it.copy(showAddManualLinksDialog = true, showAddMenu = false)
        } else {
            it.copy(
                showAddManualLinksDialog = false,
                manualLinksInput = "",
                manualLinksGroupName = "",
                manualLinksError = null,
            )
        }
    }

    fun onManualLinksFieldChanged(input: String? = null, groupName: String? = null) = _uiState.update {
        it.copy(
            manualLinksInput = input ?: it.manualLinksInput,
            manualLinksGroupName = groupName ?: it.manualLinksGroupName,
            manualLinksError = if (input != null) null else it.manualLinksError,
        )
    }

    fun onConfirmManualLinks() {
        val raw = _uiState.value.manualLinksInput.trim()
        if (raw.isEmpty()) {
            _uiState.update { it.copy(manualLinksError = "empty") }
            return
        }
        val parsed = RawShareLinksParser.parse(raw)
        if (parsed.isEmpty()) {
            _uiState.update { it.copy(manualLinksError = "parse") }
            return
        }
        val rawName = _uiState.value.manualLinksGroupName.trim()
        val name = rawName.ifEmpty { "Ozero-${state.value.groups.size + 1}" }
        viewModelScope.launch {
            val groupId = groupDao.insert(
                SubscriptionGroup(
                    name = name,
                    subscriptionUrl = "",
                    autoUpdate = false,
                    userOrder = nextGroupOrder(),
                ),
            )
            val profiles = parsed
                .filter { ConfigBuilder.supportDecision(it) is BeanSupportDecision.Supported }
                .take(MAX_IMPORT_PROFILES)
                .mapIndexed { idx, bean ->
                    ProxyProfile(
                        groupId = groupId,
                        name = bean.name.ifBlank { "Server ${idx + 1}" },
                        beanBlob = KryoSerializer.serialize(bean),
                        protocolType = protocolTypeOf(bean),
                        userOrder = idx,
                    )
                }
            if (profiles.isEmpty()) {
                val createdGroup = groupDao.getById(groupId)
                if (createdGroup != null) groupDao.delete(createdGroup)
                _uiState.update { it.copy(manualLinksError = "unsupported") }
                return@launch
            }
            profileDao.insertAll(profiles)
            _uiState.update {
                it.copy(
                    showAddManualLinksDialog = false,
                    manualLinksInput = "",
                    manualLinksGroupName = "",
                    manualLinksError = null,
                )
            }
        }
    }

    fun onImportFromFile(text: String, fileName: String?) {
        val parsed = RawShareLinksParser.parse(text)
        if (parsed.isEmpty()) {
            _uiState.update {
                it.copy(
                    showAddManualLinksDialog = true,
                    manualLinksInput = text.take(2000),
                    manualLinksGroupName = fileName?.substringBeforeLast(".") ?: "",
                    manualLinksError = "parse",
                )
            }
            return
        }
        val name = fileName?.substringBeforeLast(".") ?: "Ozero-${state.value.groups.size + 1}"
        viewModelScope.launch {
            val groupId = groupDao.insert(
                SubscriptionGroup(
                    name = name,
                    subscriptionUrl = "",
                    autoUpdate = false,
                    userOrder = nextGroupOrder(),
                ),
            )
            val profiles = parsed
                .filter { ConfigBuilder.supportDecision(it) is BeanSupportDecision.Supported }
                .take(MAX_IMPORT_PROFILES)
                .mapIndexed { idx, bean ->
                    ProxyProfile(
                        groupId = groupId,
                        name = bean.name.ifBlank { "Server ${idx + 1}" },
                        beanBlob = KryoSerializer.serialize(bean),
                        protocolType = protocolTypeOf(bean),
                        userOrder = idx,
                    )
                }
            if (profiles.isEmpty()) {
                val createdGroup = groupDao.getById(groupId)
                if (createdGroup != null) groupDao.delete(createdGroup)
                _uiState.update { it.copy(manualLinksError = "unsupported") }
                return@launch
            }
            profileDao.insertAll(profiles)
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SORT_ORDER_KEY] = order.ordinal }
        }
    }

    fun onDnsPresetChanged(presetId: String) {
        val preset = DnsPresets.ALL.firstOrNull { it.id == presetId } ?: return
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[SINGBOX_DNS_SERVERS_KEY] = preset.servers.toSet() }
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

    fun consumeRestoreError() {
        _uiState.update { it.copy(restoreError = null) }
    }

    companion object {
        private const val TAG = "SingboxEngineSettings"
        private val SORT_ORDER_KEY = intPreferencesKey("singbox_sort_order")
        private val SINGBOX_DNS_SERVERS_KEY = stringSetPreferencesKey("singbox_dns_servers")
        private const val MAX_IMPORT_PROFILES = 2_000
        private const val MAX_PROFILE_SCAN = 2_000
        private const val MAX_VISIBLE_PROFILES = MAX_PROFILE_SCAN
    }

    private fun onProfileTestingChanged(generation: Long, profileId: Long, isTesting: Boolean) {
        if (generation != probeGeneration.get()) return
        if (isTesting) {
            val count = testingProfileCounts.compute(profileId) { _, current ->
                (current ?: AtomicInteger(0)).apply { incrementAndGet() }
            }?.get() ?: 0
            if (count <= 0) return
            _uiState.update {
                if (profileId in it.testingProfileIds) {
                    it
                } else {
                    it.copy(testingProfileIds = it.testingProfileIds + profileId)
                }
            }
            return
        }
        val remaining = testingProfileCounts.computeIfPresent(profileId) { _, current ->
            if (current.decrementAndGet() <= 0) null else current
        }
        if (remaining != null) return
        _uiState.update {
            it.copy(testingProfileIds = it.testingProfileIds - profileId)
        }
    }

    private suspend fun nextGroupOrder(): Int =
        (groupDao.getAll().maxOfOrNull { it.userOrder } ?: -1) + 1

    private data class InsecureRefreshConsent(
        val groupId: Long,
        val secureGeneration: Long,
    )
}

private fun protocolTypeOf(bean: AbstractBean): Int = when (bean) {
    is VLESSBean -> 0
    is VMessBean -> 1
    is TrojanBean -> 2
    is ShadowsocksBean -> 3
    else -> 0
}
