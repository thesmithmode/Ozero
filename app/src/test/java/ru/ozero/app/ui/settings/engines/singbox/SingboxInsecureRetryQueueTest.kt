package ru.ozero.app.ui.settings.engines.singbox

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.firstArg
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
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.test.assertEquals

class SingboxInsecureRetryQueueTest {
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
    fun `confirmed insecure retries are serialized without cancelling previous retry`() = runTest {
        val first = group(1L)
        val second = group(2L)
        val groupsFlow = MutableStateFlow(listOf(first, second))
        val rawUpdater = mockk<RawUpdater>()
        val firstRetryStarted = CompletableDeferred<Unit>()
        val releaseFirstRetry = CompletableDeferred<Unit>()
        val retryOrder = ConcurrentLinkedQueue<Long>()

        coEvery { rawUpdater.refresh(any(), false) } answers {
            Result.failure(IllegalStateException(SubscriptionRefreshErrorCode.TLS_CERTIFICATE))
        }
        coEvery { rawUpdater.refresh(match { it.id == first.id }, true) } coAnswers {
            retryOrder.add(first.id)
            firstRetryStarted.complete(Unit)
            releaseFirstRetry.await()
            Result.success(1)
        }
        coEvery { rawUpdater.refresh(match { it.id == second.id }, true) } coAnswers {
            retryOrder.add(second.id)
            Result.success(1)
        }

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
        coVerify(exactly = 0) { rawUpdater.refresh(match { it.id == second.id }, true) }

        releaseFirstRetry.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(first.id, second.id), retryOrder.toList())
        coVerify(exactly = 1) { rawUpdater.refresh(match { it.id == first.id }, true) }
        coVerify(exactly = 1) { rawUpdater.refresh(match { it.id == second.id }, true) }
    }

    private fun viewModel(
        groupsFlow: MutableStateFlow<List<SubscriptionGroup>>,
        rawUpdater: RawUpdater,
    ): SingboxEngineSettingsViewModel {
        val groupDao = mockk<SubscriptionGroupDao>()
        every { groupDao.getAllFlow() } returns groupsFlow
        coEvery { groupDao.getById(any()) } answers {
            val id = firstArg<Long>()
            groupsFlow.value.find { it.id == id }
        }

        val profilesFlow = MutableStateFlow<List<ProxyProfile>>(emptyList())
        val profileDao = mockk<ProxyProfileDao>()
        every { profileDao.getAllLimitedFlow(any()) } returns profilesFlow
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
