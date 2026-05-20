package ru.ozero.enginemasterdns

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.EnginePreflight
import ru.ozero.enginescore.SocketProtector

class MasterDnsPreflightTest {

    @Test
    fun `fallback target is 8 8 8 8 colon 53 when resolvers empty`() {
        val pf = MasterDnsPreflight(resolversProvider = { emptyList() })
        val (host, port) = pf.resolveTarget()
        assertEquals("8.8.8.8", host)
        assertEquals(53, port)
    }

    @Test
    fun `first non-blank resolver chosen as target host`() {
        val pf = MasterDnsPreflight(resolversProvider = { listOf("", "  ", "1.1.1.1", "9.9.9.9") })
        val (host, port) = pf.resolveTarget()
        assertEquals("1.1.1.1", host)
        assertEquals(53, port)
    }

    @Test
    fun `resolver value trimmed`() {
        val pf = MasterDnsPreflight(resolversProvider = { listOf("  9.9.9.9 ") })
        assertEquals("9.9.9.9", pf.resolveTarget().first)
    }

    @Test
    fun `protect refused yields Fail`() = runTest {
        val pf = MasterDnsPreflight(resolversProvider = { listOf("127.0.0.1") })
        val result = pf.probe(protector = SocketProtector { false })
        assertTrue(result is EnginePreflight.Result.Fail) { "got=$result" }
        val reason = (result as EnginePreflight.Result.Fail).reason
        assertTrue(reason.contains("protect", ignoreCase = true))
    }

    @Test
    fun `unreachable port yields Fail`() = runTest {
        val pf = MasterDnsPreflight(resolversProvider = { listOf("127.0.0.1") })
        val result = pf.probe(protector = SocketProtector { true })
        assertTrue(result is EnginePreflight.Result.Fail) { "got=$result" }
    }

    @Test
    fun `implements EnginePreflight contract`() {
        val pf: EnginePreflight = MasterDnsPreflight()
        assertTrue(pf is MasterDnsPreflight)
    }
}
