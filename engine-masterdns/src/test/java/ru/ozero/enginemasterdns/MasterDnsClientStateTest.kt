package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MasterDnsClientStateTest {

    @Test
    fun `Running carries port`() {
        val s = MasterDnsClientState.Running(port = 18000)
        assertEquals(18000, s.port)
    }

    @Test
    fun `Error carries message`() {
        val s = MasterDnsClientState.Error("boom")
        assertEquals("boom", s.message)
    }

    @Test
    fun `Idle and Starting are singletons`() {
        assertEquals(MasterDnsClientState.Idle, MasterDnsClientState.Idle)
        assertEquals(MasterDnsClientState.Starting, MasterDnsClientState.Starting)
        assertNotEquals(
            MasterDnsClientState.Idle as MasterDnsClientState,
            MasterDnsClientState.Starting as MasterDnsClientState,
        )
    }

    @Test
    fun `Running with different ports not equal`() {
        assertNotEquals(
            MasterDnsClientState.Running(18000) as MasterDnsClientState,
            MasterDnsClientState.Running(18001) as MasterDnsClientState,
        )
    }

    @Test
    fun `Error with different messages not equal`() {
        assertNotEquals(
            MasterDnsClientState.Error("a") as MasterDnsClientState,
            MasterDnsClientState.Error("b") as MasterDnsClientState,
        )
    }
}
