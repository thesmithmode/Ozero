package ru.ozero.enginesingbox

import org.junit.jupiter.api.Test
import ru.ozero.singboxroom.entity.ProxyProfile
import kotlin.test.assertEquals

class SingboxAutoSelectTest {
    @Test
    fun `prioritizes low latency profiles and reserves room for untested`() {
        val available = (1L..60L).map { id -> profile(id, latency = id.toInt()) }
        val untested = (101L..120L).map { id -> profile(id, latency = SingboxLatency.LATENCY_UNTESTED) }

        val selected = prioritizeSingboxAutoProfiles(available + untested, limit = 50)

        assertEquals((1L..40L).toList() + (101L..110L).toList(), selected.map { it.id })
    }

    @Test
    fun `uses untested profiles before stale failures when no working profile fills window`() {
        val failed = (1L..5L).map { id -> profile(id, latency = SingboxLatency.LATENCY_FAILED) }
        val untested = (10L..12L).map { id -> profile(id, latency = SingboxLatency.LATENCY_UNTESTED) }

        val selected = prioritizeSingboxAutoProfiles(failed + untested, limit = 5)

        assertEquals(listOf(10L, 11L, 12L, 1L, 2L), selected.map { it.id })
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
