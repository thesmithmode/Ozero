package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals

class SingboxAutoSelectTest {
    @Test
    fun `native auto candidate order does not depend on manual ping results`() {
        val profiles = listOf(
            profile(1L, SingboxLatency.LATENCY_FAILED),
            profile(2L, 8),
            profile(3L, SingboxLatency.LATENCY_UNTESTED),
            profile(4L, 1),
        )

        val selected = prioritizeSingboxAutoProfiles(profiles, limit = 3)

        assertEquals(listOf(1L, 2L, 3L), selected.map { it.id })
    }

    @Test
    fun `native auto respects config window without reordering candidates`() {
        val profiles = (1L..60L).map { id ->
            profile(id, latency = if (id % 2L == 0L) id.toInt() else SingboxLatency.LATENCY_FAILED)
        }

        val selected = prioritizeSingboxAutoProfiles(profiles, limit = 50)

        assertEquals((1L..50L).toList(), selected.map { it.id })
    }

    private fun profile(id: Long, latency: Int): ProxyProfile = ProxyProfile(
        id = id,
        groupId = 1L,
        name = "P$id",
        beanBlob = byteArrayOf(id.toByte()),
        protocolType = SingboxEngine.PROTOCOL_VLESS,
        userOrder = id.toInt(),
        latencyMs = latency,
    )
}
