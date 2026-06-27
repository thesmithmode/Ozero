package ru.ozero.singboxroom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ozero.singboxroom.entity.ProxyProfile

class ProxyProfileEntityTest {

    @Test
    fun `equals uses byte array content`() {
        val left = profile(beanBlob = byteArrayOf(1, 2, 3))
        val right = profile(beanBlob = byteArrayOf(1, 2, 3))

        assertEquals(left, right)
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `equals rejects different fields`() {
        val base = profile()

        listOf(
            base.copy(id = 2),
            base.copy(groupId = 2),
            base.copy(name = "other"),
            base.copy(beanBlob = byteArrayOf(9)),
            base.copy(protocolType = 2),
            base.copy(userOrder = 2),
            base.copy(latencyMs = 5),
            base.copy(probeError = "timeout"),
            base.copy(lastProbeAt = 123L),
        ).forEach { changed ->
            assertNotEquals(base, changed)
        }
    }

    @Test
    fun `equals handles identity null and other type`() {
        val profile = profile()

        assertEquals(profile, profile)
        assertNotEquals(profile, null)
        assertNotEquals(profile, "profile")
    }

    @Test
    fun `hashCode changes when byte content changes`() {
        val first = profile(beanBlob = byteArrayOf(1, 2, 3))
        val second = profile(beanBlob = byteArrayOf(1, 2, 4))

        assertTrue(first.hashCode() != second.hashCode())
    }

    private fun profile(
        id: Long = 1,
        groupId: Long = 1,
        name: String = "Server",
        beanBlob: ByteArray = byteArrayOf(1),
        protocolType: Int = 1,
        userOrder: Int = 0,
        latencyMs: Int = -1,
        probeError: String? = null,
        lastProbeAt: Long = 0L,
    ) = ProxyProfile(
        id = id,
        groupId = groupId,
        name = name,
        beanBlob = beanBlob,
        protocolType = protocolType,
        userOrder = userOrder,
        latencyMs = latencyMs,
        probeError = probeError,
        lastProbeAt = lastProbeAt,
    )
}
