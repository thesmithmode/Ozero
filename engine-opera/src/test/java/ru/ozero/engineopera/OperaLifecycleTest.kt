package ru.ozero.engineopera

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OperaLifecycleTest {
    @Test
    fun `stop cancels the current operation and rejects queued startup during cleanup`() = runTest {
        val lifecycle = OperaLifecycle()
        val entered = CompletableDeferred<Unit>()
        val running = launch {
            lifecycle.run(false) {
                entered.complete(Unit)
                awaitCancellation()
            }
        }
        entered.await()
        val queued = launch {
            assertEquals("rejected", lifecycle.run("rejected") { error("must not start during stop") })
        }
        runCurrent()
        var stopped = false
        lifecycle.stop { stopped = true }
        running.join()
        queued.join()
        assertTrue(running.isCancelled)
        assertTrue(stopped)
        assertEquals("started", lifecycle.run("rejected") { "started" })
    }

    @Test
    fun `stop without startup performs cleanup and allows next operation`() = runTest {
        val lifecycle = OperaLifecycle()
        var stops = 0
        lifecycle.stop { stops++ }
        assertEquals(1, stops)
        assertEquals(42, lifecycle.run(-1) { 42 })
        lifecycle.stop { stops++ }
        assertEquals(2, stops)
    }
}
