package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
import ru.ozero.singboxsubscription.GroupSeeder
import ru.ozero.singboxsubscription.RawUpdater
import ru.ozero.singboxsubscription.SubscriptionRefreshErrorCode
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SingboxInsecureRetrySecurityTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `builtin TLS failure never offers insecure retry`() = runTest {
        val builtin = group(1L).copy(isBuiltin = true, allowInsecureTls = true)
        val groupsFlow = MutableStateFlow(listOf(builtin))
        val rawUpdater = mockk<RawUpdater>()
        coEvery { rawUpdater.refresh(match { it.id == builtin.id }, false) } returns
            Result.failure(IllegalStateException(SubscriptionRefreshErrorCode.TLS_CERTIFICATE))

        val viewModel = viewModel(groupsFlow, rawUpdater)
        backgroundScope.launch(Dispatchers.Main) { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.onRefresh(builtin.id)
        advanceUntilIdle()

        assertNull(viewModel.state.value.pendingInsecureRefreshGroupId)
        viewModel.onConfirmInsecureRefresh(true)
        advanceUntilIdle()
        coVerify(exactly = 0) { rawUpdater.refresh(match { it.id == builtin.id }, true) }
    }

    @Test
    fun `new secure refresh invalidates stale insecure consent`() = runTest {
        val group = group(2L)
        val groupsFlow = MutableStateFlow(listOf(group))
        val rawUpdater = mockk<RawUpdater>()
        var secureAttempts = 0
        coEvery { rawUpdater.refresh(match { it.id == group.id }, false) } answers {
            secureAttempts++
            if (secureAttempts == 1) {
                Result.failure(IllegalStateException(SubscriptionRefreshErrorCode.TLS_CERTIFICATE))
            } else {
                Result.success(1)
            }
        }

        val viewModel = viewModel(groupsFlow, rawUpdater)
        backgroundScope.launch(Dispatchers.Main) { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.onRefresh(group.id)
        advanceUntilIdle()
        assertEquals(group.id, viewModel.state.value.pendingInsecureRefreshGroupId)

        viewModel.onRefresh(group.id)
        advanceUntilIdle()

        assertEquals(2, secureAttempts)
        assertNull(viewModel.state.value.pendingInsecureRefreshGroupId)
        viewModel.onConfirmInsecureRefresh(true)
        advanceUntilIdle()
        coVerify(exactly = 0) { rawUpdater.refresh(match { it.id == group.id }, true) }
    }

    @Test
    fun `queued consent is dropped when newer secure refresh starts`() = runTest {
        val first = group(3L)
        val second = group(4L)
        val groupsFlow = MutableStateFlow(listOf(first, second))
        val rawUpdater = mockk<RawUpdater>()
        coEvery { rawUpdater.refresh(match { it.id == first.id }, false) } returns
            Result.failure(IllegalStateException(SubscriptionRefreshErrorCode.TLS_CERTIFICATE))
        var secondSecureAttempts = 0
        coEvery { rawUpdater.refresh(match { it.id == second.id }, false) } answers {
            secondSecureAttempts++
            if (secondSecureAttempts == 1) {
                Result.failure(IllegalStateException(SubscriptionRefreshErrorCode.TLS_CERTIFICATE))
            } else {
                Result.success(1)
            }
        }
        val firstRetryStarted = CompletableDeferred<Unit>()
        val releaseFirstRetry = CompletableDeferred<Unit>()
        coEvery { rawUpdater.refresh(match { it.id == first.id }, true) } coAnswers {
            firstRetryStarted.complete(Unit)
            releaseFirstRetry.await()
            Result.success(1)
        }
        coEvery { rawUpdater.refresh(match { it.id == second.id }, true) } returns Result.success(1)

        val viewModel = viewModel(groupsFlow, rawUpdater)
        backgroundScope.launch(Dispatchers.Main) { viewModel.state.collect { } }
        advanceUntilIdle()

        viewModel.onRefresh(first.id)
        advanceUntilIdle()
        viewModel.onRefresh(second.id)
        advanceUntilIdle()
        assertEquals(first.id, viewModel.state.value.pendingInsecureRefreshGroupId)

        viewModel.onConfirmInsecureRefresh(true)
        runCurrent()
        firstRetryStarted.await()
        assertEquals(second.id, viewModel.state.value.pendingInsecureRefreshGroupId)

        viewModel.onConfirmInsecureRefresh(true)
        runCurrent()
        viewModel.onRefresh(second.id)
        advanceUntilIdle()
        assertNull(viewModel.state.value.pendingInsecureRefreshGroupId)

        releaseFirstRetry.complete(Unit)
        advanceUntilIdle()

        assertEquals(2, secondSecureAttempts)
        coVerify(exactly = 0) { rawUpdater.refresh(match { it.id == second.id }, true) }
    }

    private fun viewModel(
        groupsFlow: MutableStateFlow<List<SubscriptionGroup>>,
        rawUpdater: RawUpdater,
    ): SingboxEngineSettingsViewModel {
        val groupDao = mockk<SubscriptionGroupDao>()
        every { groupDao.getAllFlow() } returns groupsFlow
        groupsFlow.value.forEach { group ->
            coEvery { groupDao.getById(group.id) } returns group
        }

        val profileDao = mockk<ProxyProfileDao>()
        every { profileDao.getAllLimitedFlow(any()) } returns MutableStateFlow<List<ProxyProfile>>(emptyList())
        coEvery { profileDao.getByGroupIdLimited(any(), any()) } returns emptyList()

        val chainDao = mockk<ProxyChainDao>()
        every { chainDao.getAllFlow() } returns MutableStateFlow<List<ProxyChainStep>>(emptyList())

        val prefsFlow = MutableStateFlow<Preferences>(mutablePreferencesOf())
        val dataStore = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = prefsFlow

            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                return updated
            }
        }

        return SingboxEngineSettingsViewModel(
            appContext = mockk<Context>(relaxed = true),
            dataStore = dataStore,
            groupDao = groupDao,
            profileDao = profileDao,
            proxyChainDao = chainDao,
            rawUpdater = rawUpdater,
            groupSeeder = mockk<GroupSeeder>(relaxed = true),
            probeService = mockk<SingboxProbeService>(relaxed = true),
        )
    }

    private fun group(id: Long): SubscriptionGroup = SubscriptionGroup(
        id = id,
        name = "Group $id",
        subscriptionUrl = "https://example.com/$id",
        userOrder = id.toInt(),
    )
}
