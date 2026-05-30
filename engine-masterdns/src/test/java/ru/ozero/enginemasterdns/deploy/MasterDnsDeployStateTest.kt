package ru.ozero.enginemasterdns.deploy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasterDnsDeployStateTest {

    @Test
    fun `progressPercent is in 0 to 100 range for every state`() {
        val states = listOf(
            MasterDnsDeployState.Idle,
            MasterDnsDeployState.Connecting,
            MasterDnsDeployState.CheckingPreflight,
            MasterDnsDeployState.AmneziaDnsConflict("udp", "0.0.0.0"),
            MasterDnsDeployState.InstallingDocker,
            MasterDnsDeployState.BuildingImage,
            MasterDnsDeployState.StartingContainer,
            MasterDnsDeployState.ExtractingKey,
            MasterDnsDeployState.Removing,
            MasterDnsDeployState.Removed,
            MasterDnsDeployState.Done("toml"),
            MasterDnsDeployState.PortBusy("udp", "0.0.0.0:53", "named"),
            MasterDnsDeployState.Error("x"),
        )
        states.forEach { s ->
            assertTrue(
                s.progressPercent in 0..100,
                "${s::class.simpleName} progressPercent=${s.progressPercent} вне 0..100",
            )
        }
    }

    @Test
    fun `progressPercent monotonic across happy-path deploy sequence`() {
        val sequence = listOf(
            MasterDnsDeployState.Connecting.progressPercent,
            MasterDnsDeployState.CheckingPreflight.progressPercent,
            MasterDnsDeployState.InstallingDocker.progressPercent,
            MasterDnsDeployState.BuildingImage.progressPercent,
            MasterDnsDeployState.StartingContainer.progressPercent,
            MasterDnsDeployState.ExtractingKey.progressPercent,
            MasterDnsDeployState.Done("toml").progressPercent,
        )
        sequence.windowed(2).forEach { (a, b) ->
            assertTrue(
                a < b,
                "Progress не монотонен: $a → $b (UI прогресс-бар откатится назад)",
            )
        }
    }

    @Test
    fun `Done progressPercent is exactly 100`() {
        assertEquals(100, MasterDnsDeployState.Done("toml").progressPercent)
    }

    @Test
    fun `Removed progressPercent is exactly 100`() {
        assertEquals(100, MasterDnsDeployState.Removed.progressPercent)
    }

    @Test
    fun `Idle and Error progressPercent are 0 (no progress)`() {
        assertEquals(0, MasterDnsDeployState.Idle.progressPercent)
        assertEquals(0, MasterDnsDeployState.PortBusy("udp", "0.0.0.0:53", "named").progressPercent)
        assertEquals(0, MasterDnsDeployState.Error("x").progressPercent)
    }
}
