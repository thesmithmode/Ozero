package ru.ozero.security

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityWatchdogTest {

    @Test
    fun ticksPeriodicallyOnCleanVerdict() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Clean
        var compromisedCount = 0

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 1_000,
            initialDelayMs = 1_000,
            context = dispatcher,
            onCompromised = { compromisedCount++ },
        )

        watchdog.start(scope)
        scope.advanceTimeBy(3_500)
        scope.advanceUntilIdle()

        assertEquals(0, compromisedCount, "clean check не должен триггерить onCompromised")
        assertTrue(watchdog.isRunning(), "watchdog должен продолжать тикать пока clean")
        watchdog.stop()
    }

    @Test
    fun firesOnCompromisedAndStops() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Compromised(listOf("debugger"))
        var captured: List<String>? = null

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 1_000,
            initialDelayMs = 500,
            context = dispatcher,
            onCompromised = { captured = it },
        )

        watchdog.start(scope)
        scope.advanceTimeBy(700)
        scope.advanceUntilIdle()

        assertEquals(listOf("debugger"), captured)
        assertFalse(watchdog.isRunning(), "после Compromised watchdog не должен крутиться")
    }

    @Test
    fun doesNotFireBeforeInitialDelay() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Compromised(listOf("frida"))
        var captured: List<String>? = null

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 5_000,
            initialDelayMs = 5_000,
            context = dispatcher,
            onCompromised = { captured = it },
        )

        watchdog.start(scope)
        scope.advanceTimeBy(1_000)
        scope.advanceUntilIdle()

        assertEquals(null, captured, "до initialDelay onCompromised вызываться не должен")
        watchdog.stop()
    }

    @Test
    fun stopHaltsLoopBeforeNextTick() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Clean
        var compromisedCount = 0

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 1_000,
            initialDelayMs = 100,
            context = dispatcher,
            onCompromised = { compromisedCount++ },
        )

        watchdog.start(scope)
        scope.advanceTimeBy(150)
        watchdog.stop()
        scope.advanceTimeBy(5_000)
        scope.advanceUntilIdle()

        assertFalse(watchdog.isRunning())
        assertEquals(0, compromisedCount)
    }

    @Test
    fun doubleStartIsNoOp() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Clean

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 1_000,
            initialDelayMs = 1_000,
            context = dispatcher,
            onCompromised = {},
        )

        watchdog.start(scope)
        watchdog.start(scope) // второй вызов должен быть no-op (без второй корутины)
        assertTrue(watchdog.isRunning())
        watchdog.stop()
    }

    @Test
    fun callbackExceptionDoesNotCrashWatchdog() = runTest {
        val guard = mockk<SecurityGuard>()
        every { guard.check() } returns SecurityGuard.Verdict.Compromised(listOf("hook-framework"))

        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(dispatcher)
        val watchdog = SecurityWatchdog(
            guard = guard,
            intervalMs = 1_000,
            initialDelayMs = 100,
            context = dispatcher,
            onCompromised = { error("callback failed") },
        )

        watchdog.start(scope)
        scope.advanceTimeBy(200)
        scope.advanceUntilIdle()

        // Падение callback'а не должно убить scope.
        assertFalse(watchdog.isRunning(), "watchdog завершился штатно после Compromised")
        // Sanity: scope ещё активен и можно запускать новые корутины.
        var executed = false
        scope.launch(dispatcher) {
            delay(100)
            executed = true
        }
        scope.advanceTimeBy(200)
        scope.advanceUntilIdle()
        assertTrue(executed, "scope не должен быть отменён исключением callback")
    }
}
