package ru.ozero.app.ui.onboarding

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.settings.UserFlags
import ru.ozero.app.settings.UserFlagsRepository
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var flags: FakeUserFlags
    private lateinit var bootstrap: FakeBootstrap
    private lateinit var vm: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        flags = FakeUserFlags()
        bootstrap = FakeBootstrap()
        vm = OnboardingViewModel(flags, bootstrap)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is page 0 not completed`() = runTest {
        assertEquals(0, vm.state.value.pageIndex)
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun `onNext advances page index but stops at last page`() = runTest {
        vm.onNext()
        assertEquals(1, vm.state.value.pageIndex)
        vm.onNext()
        assertEquals(2, vm.state.value.pageIndex)
        vm.onNext()
        assertEquals(2, vm.state.value.pageIndex) // capped at TOTAL_PAGES - 1
    }

    @Test
    fun `onSkip marks completed and persists flag`() = runTest {
        vm.onSkip()
        advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertTrue(flags.markedCompleted)
        assertFalse(bootstrap.invoked, "skip не должен дёргать bootstrap")
    }

    @Test
    fun `onFinish marks completed persists flag and runs bootstrap`() = runTest {
        vm.onFinish()
        advanceUntilIdle()
        assertTrue(vm.state.value.completed)
        assertTrue(flags.markedCompleted)
        assertTrue(bootstrap.invoked)
    }

    private class FakeUserFlags : UserFlagsRepository {
        var markedCompleted = false
        var markedBattery = false
        override val flags: Flow<UserFlags> = flowOf(UserFlags(false, false))
        override suspend fun isBatteryPromptShown(): Boolean = markedBattery
        override suspend fun markBatteryPromptShown() {
            markedBattery = true
        }
        override suspend fun isOnboardingCompleted(): Boolean = markedCompleted
        override suspend fun markOnboardingCompleted() {
            markedCompleted = true
        }
    }

    private class FakeBootstrap : FirstRunBootstrap() {
        var invoked = false
        override fun runIfFirstStart() {
            invoked = true
        }
    }
}
