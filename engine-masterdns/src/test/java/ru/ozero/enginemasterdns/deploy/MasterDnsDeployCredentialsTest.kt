package ru.ozero.enginemasterdns.deploy

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MasterDnsDeployCredentialsTest {

    @Test
    fun `equals and hashCode compare password content`() {
        val first = credentials(password = "secret")
        val second = credentials(password = "secret")
        val differentPassword = credentials(password = "other")

        assertEquals(first, first)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertNotEquals(first, differentPassword)
        assertNotEquals(first.hashCode(), differentPassword.hashCode())
    }

    @Test
    fun `equals rejects different fields and unrelated types`() {
        val base = credentials()

        assertFalse(base.equals("not credentials"))
        assertNotEquals(base, credentials(host = "10.0.0.2"))
        assertNotEquals(base, credentials(port = 2022))
        assertNotEquals(base, credentials(login = "admin"))
    }

    @Test
    fun `clear wipes password and changes equality`() {
        val base = credentials(password = "secret")
        val sameBeforeClear = credentials(password = "secret")

        base.clear()

        assertTrue(base.password.all { it == '\u0000' })
        assertNotEquals(base, sameBeforeClear)
    }

    private fun credentials(
        host: String = "10.0.0.1",
        port: Int = 22,
        login: String = "root",
        password: String = "secret",
    ) = MasterDnsDeployCredentials(
        host = host,
        port = port,
        login = login,
        password = password.toCharArray(),
    )
}
