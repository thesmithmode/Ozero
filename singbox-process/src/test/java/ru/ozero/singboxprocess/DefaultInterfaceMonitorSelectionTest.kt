package ru.ozero.singboxprocess

import android.net.Network
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultInterfaceMonitorSelectionTest {

    @Test
    fun `active vpn is skipped and wifi is selected`() {
        val vpn = mockk<Network>()
        val wifi = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(vpn, eligible = false, active = true, validated = true, NetworkCandidateSource.ACTIVE),
                candidate(vpn, eligible = false, active = true, validated = true, NetworkCandidateSource.ALL),
                candidate(wifi, eligible = true, active = false, validated = true, NetworkCandidateSource.ALL),
            ),
        )

        assertEquals(wifi, selected)
    }

    @Test
    fun `lost network is excluded even if still eligible`() {
        val wifi = mockk<Network>()
        val cellular = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(wifi, eligible = true, active = true, validated = true, NetworkCandidateSource.LAST),
                candidate(cellular, eligible = true, active = false, validated = false, NetworkCandidateSource.ALL),
            ),
            exclude = wifi,
        )

        assertEquals(cellular, selected)
    }

    @Test
    fun `validated network is preferred among all networks`() {
        val captive = mockk<Network>()
        val validated = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(captive, eligible = true, active = false, validated = false, NetworkCandidateSource.ALL),
                candidate(validated, eligible = true, active = false, validated = true, NetworkCandidateSource.ALL),
            ),
        )

        assertEquals(validated, selected)
    }

    @Test
    fun `active validated network wins over cached unvalidated last`() {
        val wifi = mockk<Network>()
        val cellular = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(wifi, eligible = true, active = false, validated = false, NetworkCandidateSource.LAST),
                candidate(cellular, eligible = true, active = true, validated = true, NetworkCandidateSource.ACTIVE),
            ),
        )

        assertEquals(cellular, selected)
    }

    @Test
    fun `validated non active network wins over cached unvalidated last`() {
        val wifi = mockk<Network>()
        val cellular = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(wifi, eligible = true, active = false, validated = false, NetworkCandidateSource.LAST),
                candidate(cellular, eligible = true, active = false, validated = true, NetworkCandidateSource.ALL),
            ),
        )

        assertEquals(cellular, selected)
    }

    @Test
    fun `last is only final tie breaker for equal networks`() {
        val callback = mockk<Network>()
        val last = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(last, eligible = true, active = false, validated = false, NetworkCandidateSource.LAST),
                candidate(callback, eligible = true, active = false, validated = false, NetworkCandidateSource.ALL),
            ),
        )

        assertEquals(last, selected)
    }

    @Test
    fun `restricted internet network is not eligible`() {
        val restricted = mockk<Network>()

        val selected = selectDefaultNetwork(
            listOf(
                candidate(restricted, eligible = false, active = true, validated = true, NetworkCandidateSource.ACTIVE),
            ),
        )

        assertNull(selected)
    }

    private fun candidate(
        network: Network,
        eligible: Boolean,
        active: Boolean,
        validated: Boolean,
        source: NetworkCandidateSource,
    ): NetworkCandidate = NetworkCandidate(network, eligible, active, validated, source)
}
