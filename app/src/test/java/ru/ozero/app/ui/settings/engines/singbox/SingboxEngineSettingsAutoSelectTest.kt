package ru.ozero.app.ui.settings.engines.singbox

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.dao.ProxyChainDao
import ru.ozero.singboxroom.dao.ProxyProfileDao
import ru.ozero.singboxroom.dao.SubscriptionGroupDao
import ru.ozero.singboxroom.entity.ProxyChainStep
import ru.ozero.singboxroom.entity.ProxyProfile
import ru.ozero.singboxroom.entity.SubscriptionGroup
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingboxEngineSettingsAutoSelectTest {

    private val beanKey = byteArrayPreferencesKey("singbox_vless_bean")
    private val selectedProfileKey = longPreferencesKey("singbox_selected_profile_id")
    private val dispatcher = StandardTestDispatcher()

    private lateinit var prefsFlow: MutableStateFlow<Preferences>
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var groupsFlow: MutableStateFlow<List<SubscriptionGroup>>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        prefsFlow = MutableStateFlow(mutablePreferencesOf())
        dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                return updated
            }
        }
        groupsFlow = MutableStateFlow(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): SingboxEngineSettingsViewModel {
        val groupDao = object : SubscriptionGroupDao {
            override fun getAllFlow(): Flow<List<SubscriptionGroup>> = groupsFlow
            override suspend fun getAll(): List<SubscriptionGroup> = groupsFlow.value
            override suspend fun getById(id: Long): SubscriptionGroup? = groupsFlow.value.find { it.id == id }
            override suspend fun getByUrl(url: String): SubscriptionGroup? = null
            override suspend fun getBuiltins(): List<SubscriptionGroup> = emptyList()
            override suspend fun count(): Int = groupsFlow.value.size
            override suspend fun insert(group: SubscriptionGroup): Long = group.id
            override suspend fun update(group: SubscriptionGroup) {}
            override suspend fun delete(group: SubscriptionGroup) {}
        }
        val profileDao = object : ProxyProfileDao {
            override fun getAllFlow(): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override fun getByGroupIdFlow(groupId: Long): Flow<List<ProxyProfile>> = MutableStateFlow(emptyList())
            override suspend fun getByGroupId(groupId: Long): List<ProxyProfile> = emptyList()
            override suspend fun getById(id: Long): ProxyProfile? = null
            override suspend fun insert(profile: ProxyProfile): Long = profile.id
            override suspend fun insertAll(profiles: List<ProxyProfile>) {}
            override suspend fun deleteByGroupId(groupId: Long) {}
            override suspend fun updateLatency(id: Long, latency: Int) {}
            override suspend fun countByGroupId(groupId: Long): Int = 0
            override suspend fun update(profile: ProxyProfile) {}
            override suspend fun delete(profile: ProxyProfile) {}
        }
        val proxyChainDao = object : ProxyChainDao {
            private val chainStepsFlow = MutableStateFlow(emptyList<ProxyChainStep>())

            override fun getAllFlow(): Flow<List<ProxyChainStep>> = chainStepsFlow
            override suspend fun getAll(): List<ProxyChainStep> = chainStepsFlow.value
            override suspend fun clear() {
                chainStepsFlow.value = emptyList()
            }
            override suspend fun insertAll(steps: List<ProxyChainStep>) {
                chainStepsFlow.value = steps
            }
        }
        return SingboxEngineSettingsViewModel(
            appContext = mockk(relaxed = true),
            dataStore = dataStore,
            groupDao = groupDao,
            profileDao = profileDao,
            proxyChainDao = proxyChainDao,
            rawUpdater = mockk(relaxed = true),
            groupSeeder = mockk(relaxed = true),
            probeService = mockk(relaxed = true),
        )
    }

    @Test
    fun `onSetAutoSelect true stores selectedProfileId as -1L in DataStore`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        vm.onSetAutoSelect(true)
        advanceUntilIdle()

        val storedId = prefsFlow.value[selectedProfileKey]
        assertNotNull(storedId)
        assertTrue(storedId == -1L)
    }

    @Test
    fun `onSetAutoSelect true clears beanKey`() = runTest {
        prefsFlow.value = mutablePreferencesOf(
            beanKey to byteArrayOf(1, 2, 3),
            selectedProfileKey to 5L,
        )
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        vm.onSetAutoSelect(true)
        advanceUntilIdle()

        assertNull(prefsFlow.value[beanKey])
    }

    @Test
    fun `onSetAutoSelect false removes selectedProfileId from DataStore`() = runTest {
        prefsFlow.value = mutablePreferencesOf(selectedProfileKey to -1L)
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        vm.onSetAutoSelect(false)
        advanceUntilIdle()

        assertNull(prefsFlow.value[selectedProfileKey])
    }

    @Test
    fun `isAutoSelectMode is true when DataStore has selectedProfileId of -1L`() = runTest {
        prefsFlow.value = mutablePreferencesOf(selectedProfileKey to -1L)
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        assertTrue(vm.state.value.isAutoSelectMode)
    }

    @Test
    fun `isAutoSelectMode is false when DataStore has normal selectedProfileId`() = runTest {
        prefsFlow.value = mutablePreferencesOf(selectedProfileKey to 42L)
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.isAutoSelectMode)
    }

    @Test
    fun `isAutoSelectMode is false when DataStore has no selectedProfileId`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        assertFalse(vm.state.value.isAutoSelectMode)
    }

    @Test
    fun `onSetAutoSelect true then state reflects isAutoSelectMode true`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        vm.onSetAutoSelect(true)
        advanceUntilIdle()

        assertTrue(vm.state.value.isAutoSelectMode)
    }

    @Test
    fun `onSetAutoSelect false after enable then state reflects isAutoSelectMode false`() = runTest {
        val vm = buildViewModel()
        backgroundScope.launch(Dispatchers.Main) { vm.state.collect {} }
        advanceUntilIdle()

        vm.onSetAutoSelect(true)
        advanceUntilIdle()
        vm.onSetAutoSelect(false)
        advanceUntilIdle()

        assertFalse(vm.state.value.isAutoSelectMode)
    }
}
