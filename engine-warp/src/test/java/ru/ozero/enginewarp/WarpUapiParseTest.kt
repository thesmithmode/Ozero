package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarpUapiParseTest {

    @Test
    fun `readState returns null when uapi socket is absent`(@TempDir tmp: File) {
        assertNull(WarpUapi.readState(tmp.absolutePath, "ozero-warp"))
    }

    @Test
    fun `parseReply aggregates traffic and newest handshake age`() {
        val now = System.currentTimeMillis() / 1000L
        val state = parse(
            """
            public_key=peer1
            rx_bytes=10
            tx_bytes=20
            last_handshake_time_sec=${now - 5}
            public_key=peer2
            rx_bytes=30
            tx_bytes=40
            last_handshake_time_sec=${now - 2}

            ignored=tail
            """.trimIndent(),
        )

        assertEquals(2, state.peersSeen)
        assertEquals(40L, state.rxBytes)
        assertEquals(60L, state.txBytes)
        assertTrue(state.handshakeAgeSeconds in 0L..5L)
    }

    @Test
    fun `parseReply ignores malformed values and non key lines`() {
        val state = parse(
            """
            no-equals
            public_key=peer1
            rx_bytes=bad
            tx_bytes=7
            last_handshake_time_sec=-1

            """.trimIndent(),
        )

        assertEquals(1, state.peersSeen)
        assertEquals(0L, state.rxBytes)
        assertEquals(7L, state.txBytes)
        assertNull(state.handshakeAgeSeconds)
    }

    @Test
    fun `parseReply returns zero state for empty reply`() {
        val state = parse("")

        assertEquals(0, state.peersSeen)
        assertEquals(0L, state.rxBytes)
        assertEquals(0L, state.txBytes)
        assertNull(state.handshakeAgeSeconds)
    }

    private fun parse(text: String): WarpUapiState {
        val method = WarpUapi::class.java.getDeclaredMethod("parseReply", BufferedReader::class.java)
        method.isAccessible = true
        return method.invoke(WarpUapi, BufferedReader(StringReader(text))) as WarpUapiState
    }
}
