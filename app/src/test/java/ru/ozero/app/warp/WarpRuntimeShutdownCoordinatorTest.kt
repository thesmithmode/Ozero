package ru.ozero.app.warp

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test

class WarpRuntimeShutdownCoordinatorTest {
    @Test
    fun `successful cleanup disarms watchdog`() {
        var terminated = false
        val tasks = mutableMapOf<String, () -> Unit>()
        val coordinator = coordinator(
            cleanup = { true },
            terminate = { terminated = true },
            tasks = tasks,
        )

        coordinator.request()
        tasks.cleanup().invoke()
        tasks.watchdog().invoke()

        assertFalse(terminated)
    }

    @Test
    fun `watchdog terminates process while native cleanup is still blocked`() {
        var terminateCalls = 0
        val tasks = mutableMapOf<String, () -> Unit>()
        val coordinator = coordinator(
            cleanup = { true },
            terminate = { terminateCalls++ },
            tasks = tasks,
        )

        coordinator.request()
        tasks.watchdog().invoke()
        tasks.cleanup().invoke()

        assertEquals(1, terminateCalls)
    }

    @Test
    fun `failed cleanup terminates process exactly once`() {
        var terminateCalls = 0
        val tasks = mutableMapOf<String, () -> Unit>()
        val coordinator = coordinator(
            cleanup = { false },
            terminate = { terminateCalls++ },
            tasks = tasks,
        )

        coordinator.request()
        tasks.cleanup().invoke()
        tasks.watchdog().invoke()

        assertEquals(1, terminateCalls)
    }

    @Test
    fun `repeated shutdown requests launch one cleanup pair`() {
        val launched = mutableListOf<String>()
        val coordinator = WarpRuntimeShutdownCoordinator(
            cleanup = { true },
            terminateProcess = {},
            waitForTimeout = {},
            launchTask = { name, _ -> launched += name },
        )

        coordinator.request()
        coordinator.request()

        assertEquals(2, launched.size)
        assertEquals(2, launched.toSet().size)
    }

    private fun coordinator(
        cleanup: () -> Boolean,
        terminate: () -> Unit,
        tasks: MutableMap<String, () -> Unit>,
    ) = WarpRuntimeShutdownCoordinator(
        cleanup = cleanup,
        terminateProcess = terminate,
        waitForTimeout = {},
        launchTask = { name, task -> tasks[name] = task },
    )

    private fun Map<String, () -> Unit>.cleanup(): (() -> Unit) =
        assertNotNull(entries.singleOrNull { it.key.contains("cleanup") }?.value)

    private fun Map<String, () -> Unit>.watchdog(): (() -> Unit) =
        assertNotNull(entries.singleOrNull { it.key.contains("watchdog") }?.value)
}
