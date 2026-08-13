package ru.ozero.singboxprocess

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DetachedTunFdTest {
    @Test
    fun `failure before claim closes fd exactly once`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(41, closed::add)

        assertTrue(ownership.closeIfDetached())
        assertFalse(ownership.closeIfDetached())
        assertEquals(listOf(41), closed)
        assertEquals(TunFdOwnershipState.CLOSED, ownership.state)
    }

    @Test
    fun `host close also handles fd before it is provided`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(40, closed::add)

        assertTrue(ownership.closeOwnedByHost())
        assertFalse(ownership.closeIfDetached())
        assertEquals(listOf(40), closed)
    }

    @Test
    fun `providing fd to libbox retains host close responsibility`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(42, closed::add)

        assertEquals(42, ownership.provideToLibbox())
        assertFalse(ownership.closeIfDetached())
        assertTrue(ownership.closeOwnedByHost())
        assertFalse(ownership.closeOwnedByHost())
        assertEquals(listOf(42), closed)
        assertEquals(TunFdOwnershipState.CLOSED, ownership.state)
    }

    @Test
    fun `repeated provide is rejected before host closes fd`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(43, closed::add)

        ownership.provideToLibbox()

        assertFailsWith<IllegalStateException> { ownership.provideToLibbox() }
        assertFalse(ownership.closeIfDetached())
        assertTrue(ownership.closeOwnedByHost())
        assertEquals(listOf(43), closed)
    }

    @Test
    fun `proxy mode has no detached tun ownership`() {
        val ownership: DetachedTunFd? = null

        assertEquals(null, ownership)
    }
}
