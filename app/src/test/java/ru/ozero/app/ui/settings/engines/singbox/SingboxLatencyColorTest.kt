package ru.ozero.app.ui.settings.engines.singbox

import kotlin.test.Test
import kotlin.test.assertTrue

class SingboxLatencyColorTest {
    @Test
    fun `latency color moves from green to black as latency grows`() {
        val fast = singboxLatencyColor(50)
        val mid = singboxLatencyColor(750)
        val slow = singboxLatencyColor(1_500)
        val deadSlow = singboxLatencyColor(2_500)

        assertTrue(fast.green > fast.red)
        assertTrue(mid.red > 0.5f && mid.green > 0.5f)
        assertTrue(slow.red > slow.green)
        assertTrue(deadSlow.red < slow.red && deadSlow.green < slow.green)
    }
}
