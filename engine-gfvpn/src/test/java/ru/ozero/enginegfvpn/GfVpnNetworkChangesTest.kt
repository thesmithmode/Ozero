package ru.ozero.enginegfvpn

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GfVpnNetworkChangesTest {
    @Test
    fun `burst during refresh becomes exactly one subsequent refresh`() = runBlocking {
        var calls = 0
        val firstEntered = CompletableDeferred<Unit>()
        val firstRelease = CompletableDeferred<Unit>()
        val secondEntered = CompletableDeferred<Unit>()
        val secondRelease = CompletableDeferred<Unit>()
        val changes = GfVpnNetworkChanges(this) {
            when (++calls) {
                1 -> {
                    firstEntered.complete(Unit)
                    firstRelease.await()
                }
                2 -> {
                    secondEntered.complete(Unit)
                    secondRelease.await()
                }
                else -> error("Unbounded refresh queue")
            }
        }
        try {
            withTimeout(5_000) {
                assertTrue(changes.offer())
                firstEntered.await()
                repeat(1000) { assertTrue(changes.offer()) }
                firstRelease.complete(Unit)
                secondEntered.await()
                assertEquals(2, calls)
            }
        } finally {
            changes.close()
            secondRelease.complete(Unit)
        }
        assertFalse(changes.offer())
    }

    @Test
    fun `closed session discards pending and late events`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        var calls = 0
        val changes = GfVpnNetworkChanges(this) {
            calls++
            entered.complete(Unit)
            try {
                blocked.await()
            } finally {
                cancelled.complete(Unit)
            }
        }
        try {
            withTimeout(5_000) {
                assertTrue(changes.offer())
                entered.await()
                assertTrue(changes.offer())
                changes.close()
                cancelled.await()
                assertFalse(changes.offer())
                assertEquals(1, calls)
            }
        } finally {
            changes.close()
        }
    }
}
