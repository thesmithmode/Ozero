package ru.ozero.enginefptn

import org.junit.jupiter.api.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class FptnRuntimeFingerprintTest {

    @Test
    fun `fingerprint changes for runtime fields and does not expose token`() {
        val cfg = FptnConfig(
            token = "sample-fptn-token-a",
            selectedServerName = "France-1",
            bypassMethod = "SNI",
            sniDomain = "example.com",
        )

        assertNotEquals(cfg.runtimeFingerprint(), cfg.copy(sniDomain = "alt.example.com").runtimeFingerprint())
        assertNotEquals(cfg.runtimeFingerprint(), cfg.copy(token = "sample-fptn-token-b").runtimeFingerprint())
        assertTrue(cfg.runtimeFingerprint().toString().contains("sample-fptn-token-a").not())
        assertTrue(cfg.runtimeFingerprint().toString().contains("digest=***"))
    }

    @Test
    fun `fingerprint ignores reconnect settings not consumed by active native session`() {
        val cfg = FptnConfig(token = "sample-fptn-token-a")
        val changed = cfg.copy(
            reconnectOnNetworkChange = false,
            reconnectOnIpChange = true,
            maxReconnectAttempts = 9,
            reconnectPauseSeconds = 7,
            resetServerOnDisconnect = false,
        )

        assertTrue(
            cfg.runtimeFingerprint() == changed.runtimeFingerprint(),
            "FPTN runtime fingerprint must only include fields that affect current startup/native session.",
        )
    }

    @Test
    fun `fingerprint ignores selected server while auto select is enabled`() {
        val cfg = FptnConfig(
            token = "sample-fptn-token-a",
            selectedServerName = "France-1",
            autoSelect = true,
        )

        assertTrue(
            cfg.runtimeFingerprint() == cfg.copy(selectedServerName = "Germany-1").runtimeFingerprint(),
            "FPTN auto-select chooses from token servers and ignores selectedServerName at startup.",
        )
        assertNotEquals(
            cfg.copy(autoSelect = false).runtimeFingerprint(),
            cfg.copy(autoSelect = false, selectedServerName = "Germany-1").runtimeFingerprint(),
        )
    }
}
