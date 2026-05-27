package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MasterDnsClientWrapperUpstreamTest {

    @Test
    fun `upstream socks flag appears when provided`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/lib/libmdnsvpn.so",
            configPath = "/c.toml",
            resolversPath = "/r.txt",
            logPath = null,
            upstreamSocksUrl = "socks5://127.0.0.1:49152",
        )
        assertTrue(args.containsInOrder("--socks5-proxy-url", "socks5://127.0.0.1:49152"))
    }

    @Test
    fun `upstream socks flag absent when null`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/lib/libmdnsvpn.so",
            configPath = "/c.toml",
            resolversPath = "/r.txt",
            logPath = null,
            upstreamSocksUrl = null,
        )
        assertFalse("--socks5-proxy-url" in args)
    }

    @Test
    fun `args length with upstream is 7`() {
        val args = MasterDnsClientWrapper.buildArgs("/b", "/c", "/r", null, "socks5://127.0.0.1:1080")
        assertEquals(7, args.size)
    }

    @Test
    fun `args length with upstream and log is 9`() {
        val args = MasterDnsClientWrapper.buildArgs("/b", "/c", "/r", "/l", "socks5://127.0.0.1:1080")
        assertEquals(9, args.size)
    }

    private fun List<String>.containsInOrder(flag: String, value: String): Boolean {
        val idx = indexOf(flag)
        return idx >= 0 && idx + 1 < size && this[idx + 1] == value
    }
}
