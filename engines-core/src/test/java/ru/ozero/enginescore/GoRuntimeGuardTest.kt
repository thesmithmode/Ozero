package ru.ozero.enginescore

import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class GoRuntimeGuardTest {

    @Test
    fun `acquire дважды одним owner возвращает Granted`() {
        resetGuard()
        val r1 = GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.AMNEZIA_WG)
        val r2 = GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.AMNEZIA_WG)
        assertEquals(GoRuntimeGuard.Result.Granted, r1)
        assertEquals(GoRuntimeGuard.Result.Granted, r2)
    }

    @Test
    fun `acquire разными owners — second возвращает Conflict с указанием активного`() {
        resetGuard()
        val r1 = GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.URNETWORK)
        val r2 = GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.AMNEZIA_WG)
        assertEquals(GoRuntimeGuard.Result.Granted, r1)
        val conflict = assertIs<GoRuntimeGuard.Result.Conflict>(r2)
        assertEquals(GoRuntimeGuard.Owner.URNETWORK, conflict.activeOwner)
    }

    @Test
    fun `acquire AMNEZIA_WG потом URNETWORK — Conflict`() {
        resetGuard()
        GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.AMNEZIA_WG)
        val r = GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.URNETWORK)
        val conflict = assertIs<GoRuntimeGuard.Result.Conflict>(r)
        assertEquals(GoRuntimeGuard.Owner.AMNEZIA_WG, conflict.activeOwner)
    }

    @Test
    fun `current null когда нет acquire`() {
        resetGuard()
        assertEquals(null, GoRuntimeGuard.current())
    }

    @Test
    fun `current возвращает active owner после acquire`() {
        resetGuard()
        GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.URNETWORK)
        assertEquals(GoRuntimeGuard.Owner.URNETWORK, GoRuntimeGuard.current())
    }

    @Test
    fun `Owner enum содержит AMNEZIA_WG и URNETWORK`() {
        val names = GoRuntimeGuard.Owner.entries.map { it.name }
        assertEquals(setOf("AMNEZIA_WG", "URNETWORK"), names.toSet())
    }

    @Test
    fun `release освобождает acquired чтобы другой owner мог acquire`() {
        resetGuard()
        assertEquals(GoRuntimeGuard.Result.Granted, GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.AMNEZIA_WG))
        GoRuntimeGuard.release(GoRuntimeGuard.Owner.AMNEZIA_WG)
        assertEquals(null, GoRuntimeGuard.current())
        assertEquals(
            GoRuntimeGuard.Result.Granted,
            GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.URNETWORK),
            "После release(AMNEZIA_WG) URNETWORK обязан получить Granted — иначе WARP→URnetwork " +
                "switch заблокирован навсегда (CLAUDE.md: requires resident coexistence + clean release).",
        )
    }

    @Test
    fun `release не очищает acquired если owner отличается`() {
        resetGuard()
        GoRuntimeGuard.acquire(GoRuntimeGuard.Owner.URNETWORK)
        GoRuntimeGuard.release(GoRuntimeGuard.Owner.AMNEZIA_WG)
        assertEquals(GoRuntimeGuard.Owner.URNETWORK, GoRuntimeGuard.current())
    }

    @Test
    fun `release без acquire — noop`() {
        resetGuard()
        GoRuntimeGuard.release(GoRuntimeGuard.Owner.AMNEZIA_WG)
        assertEquals(null, GoRuntimeGuard.current())
    }

    private fun resetGuard() {
        val field = GoRuntimeGuard::class.java.getDeclaredField("acquired")
        field.isAccessible = true
        assertNotNull(field, "acquired field must exist on GoRuntimeGuard")
        @Suppress("UNCHECKED_CAST")
        val ref = field.get(GoRuntimeGuard) as java.util.concurrent.atomic.AtomicReference<Any?>
        ref.set(null)
        check(Modifier.isStatic(field.modifiers).not() || GoRuntimeGuard::class.java.declaredFields.isNotEmpty())
    }
}
