package ru.ozero.engineopera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OperaProxyCommandTest {
    @Test
    fun `command binds only loopback and uses socks mode`() {
        val args = OperaProxyCommand.build(File("/native/libopera_proxy.so"), OperaProxyConfig(), 18080)
        assertEquals("/native/libopera_proxy.so", args.first())
        assertEquals("127.0.0.1:18080", args[args.indexOf("-bind-address") + 1])
        assertTrue("-socks-mode" in args)
        assertEquals("EU", args[args.indexOf("-country") + 1])
        assertEquals("fastest", args[args.indexOf("-server-selection") + 1])
        assertFalse("-api-proxy-file" in args)
        assertFalse("-fake-SNI" in args)
    }

    @Test
    fun `each supported region and selection maps to upstream flags`() {
        for (region in OperaRegion.entries) {
            for (selection in OperaServerSelection.entries) {
                val args = OperaProxyCommand.build(File("opera"), OperaProxyConfig(region, selection), 65535)
                assertEquals(region.name, args[args.indexOf("-country") + 1])
                assertEquals(selection.argument, args[args.indexOf("-server-selection") + 1])
            }
        }
    }

    @Test
    fun `invalid port fails before launch`() {
        for (port in listOf(-1, 0, 65536)) {
            assertThrows(IllegalArgumentException::class.java) {
                OperaProxyCommand.build(File("opera"), OperaProxyConfig(), port)
            }
        }
    }
}
