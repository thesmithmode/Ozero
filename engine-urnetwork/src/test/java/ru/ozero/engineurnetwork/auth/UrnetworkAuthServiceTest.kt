package ru.ozero.engineurnetwork.auth

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UrnetworkAuthServiceTest {

    @Test
    fun `GuestJwtResult Success carries jwt`() {
        val r = GuestJwtResult.Success(byJwt = "eyJabc.def.ghi")
        assertEquals("eyJabc.def.ghi", r.byJwt)
        assertTrue(r.byJwt.isNotBlank())
    }

    @Test
    fun `GuestJwtResult Error carries message`() {
        val r = GuestJwtResult.Error("rate limited")
        assertEquals("rate limited", r.message)
    }

    @Test
    fun `Fake acquireGuestJwt без error возвращает Success с jwt`() = runTest {
        val service = FakeUrnetworkAuthService(jwt = "guest.tok.x")
        val r = service.acquireGuestJwt()
        val ok = assertIs<GuestJwtResult.Success>(r)
        assertEquals("guest.tok.x", ok.byJwt)
    }

    @Test
    fun `Fake acquireGuestJwt с error возвращает Error`() = runTest {
        val service = FakeUrnetworkAuthService(error = "throttled")
        val r = service.acquireGuestJwt()
        val err = assertIs<GuestJwtResult.Error>(r)
        assertEquals("throttled", err.message)
    }

    @Test
    fun `Fake acquireGuestJwt с пустым jwt возвращает Error`() = runTest {
        val service = FakeUrnetworkAuthService(jwt = "")
        val r = service.acquireGuestJwt()
        val err = assertIs<GuestJwtResult.Error>(r)
        assertTrue(err.message.isNotBlank())
    }

    private class FakeUrnetworkAuthService(
        private val jwt: String = "fake.jwt",
        private val error: String? = null,
    ) : UrnetworkAuthService {
        override suspend fun acquireGuestJwt(): GuestJwtResult = when {
            error != null -> GuestJwtResult.Error(error)
            jwt.isBlank() -> GuestJwtResult.Error("empty jwt from server")
            else -> GuestJwtResult.Success(byJwt = jwt)
        }
    }
}
