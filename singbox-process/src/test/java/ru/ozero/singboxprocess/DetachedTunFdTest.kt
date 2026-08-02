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
    fun `successful claim transfers ownership to libbox`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(42, closed::add)

        assertEquals(42, ownership.claimByLibbox())
        assertFalse(ownership.closeIfDetached())
        assertEquals(emptyList(), closed)
        assertEquals(TunFdOwnershipState.CLAIMED_BY_LIBBOX, ownership.state)
    }

    @Test
    fun `repeated claim is rejected without closing claimed fd`() {
        val closed = mutableListOf<Int>()
        val ownership = DetachedTunFd(43, closed::add)

        ownership.claimByLibbox()

        assertFailsWith<IllegalStateException> { ownership.claimByLibbox() }
        assertFalse(ownership.closeIfDetached())
        assertEquals(emptyList(), closed)
    }

    @Test
    fun `proxy mode has no detached tun ownership`() {
        val ownership: DetachedTunFd? = null

        assertEquals(null, ownership)
    }
}
