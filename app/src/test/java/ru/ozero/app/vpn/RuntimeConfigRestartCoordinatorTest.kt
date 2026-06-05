package ru.ozero.app.vpn

import android.content.Context
import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import io.mockk.isA
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelController
import ru.ozero.enginescore.EngineId
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeConfigRestartCoordinatorTest {

    @Test
    fun `restart queue clears after exception and accepts the next restart`() = runTest {
        val context = mockk<Context>()
        var startServiceCalls = 0
        every { context.startService(isA<Intent>()) } answers {
            startServiceCalls += 1
            throw IllegalStateException("boom")
        }
        val tunnelController = TunnelController()
        tunnelController.onConnecting(EngineId.WARP)
        val coordinator = RuntimeConfigRestartCoordinator(
            context = context,
            observer = EngineRuntimeConfigRestartObserver(emptySet()),
            tunnelController = tunnelController,
        )

        coordinator.restartQueue().addLast("queued-before")

        runCatching { coordinator.invokeRestart("current-request") }
        assertEquals(1, startServiceCalls)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
        assertTrue(tunnelController.switching.value == null)

        runCatching { coordinator.invokeRestart("second-request") }
        assertEquals(2, startServiceCalls)
        assertTrue(coordinator.restartQueue().isEmpty())
        assertFalse(coordinator.restartInProgress())
    }

    private suspend fun RuntimeConfigRestartCoordinator.invokeRestart(reason: String): Boolean =
        suspendCoroutine { cont ->
            val method = javaClass.getDeclaredMethod(
                "restartVpnIfRunning",
                String::class.java,
                Continuation::class.java,
            )
            method.isAccessible = true
            try {
                val returned = method.invoke(
                    this,
                    reason,
                    object : Continuation<Boolean> {
                        override val context = coroutineContext
                        override fun resumeWith(result: Result<Boolean>) {
                            cont.resumeWith(result)
                        }
                    },
                )
                if (returned !== COROUTINE_SUSPENDED) {
                    @Suppress("UNCHECKED_CAST")
                    cont.resumeWith(Result.success(returned as Boolean))
                }
            } catch (e: InvocationTargetException) {
                cont.resumeWith(Result.failure(e.cause ?: e))
            } catch (t: Throwable) {
                cont.resumeWith(Result.failure(t))
            }
        }

    private fun RuntimeConfigRestartCoordinator.restartQueue(): ArrayDeque<String> {
        val field = javaClass.getDeclaredField("restartQueue")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(this) as ArrayDeque<String>
    }

    private fun RuntimeConfigRestartCoordinator.restartInProgress(): Boolean {
        val field = javaClass.getDeclaredField("restartInProgress")
        field.isAccessible = true
        return field.getBoolean(this)
    }
}
