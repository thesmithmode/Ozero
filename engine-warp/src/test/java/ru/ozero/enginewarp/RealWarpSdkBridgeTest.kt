package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealWarpSdkBridgeTest {

    private fun bridgeWith(
        runtime: AwgRuntime = FakeAwgRuntime(),
    ): Pair<RealWarpSdkBridge, AwgRuntime> = RealWarpSdkBridge(runtime) to runtime

    @Test
    fun `attachTun валидный fd → Success и сохраняет handle`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("ozero-warp", tunFd = 5, iniConfig = "[Interface]\nPrivateKey=k", uapiPath = "/x")
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertTrue(b.isRunning())
        assertEquals(5, rt.lastFd)
        assertEquals("ozero-warp", rt.lastName)
        assertEquals("/x", rt.lastUapi)
        assertTrue(rt.lastIni!!.contains("PrivateKey"))
    }

    @Test
    fun `attachTun negative fd → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = -1, iniConfig = "ini", uapiPath = "/x")
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun blank ini → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 1, iniConfig = "", uapiPath = "/x")
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun blank uapi → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 1, iniConfig = "ini", uapiPath = "")
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun negative handle от runtime → Failed`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = -2)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 5, iniConfig = "ini", uapiPath = "/x")
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("handle=-2"))
        assertFalse(b.isRunning())
    }

    @Test
    fun `attachTun runtime бросает → Failed с текстом ошибки`() = runTest {
        val rt = FakeAwgRuntime(throwOnTurnOn = RuntimeException("native crash"))
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 5, iniConfig = "ini", uapiPath = "/x")
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("native crash"))
    }

    @Test
    fun `detachTun без attach — no-op`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        b.detachTun()
        assertEquals(0, rt.turnOffCalls)
    }

    @Test
    fun `detachTun после attach зовёт turnOff с handle`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 11)
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, "ini", "/x")
        b.detachTun()
        assertEquals(1, rt.turnOffCalls)
        assertEquals(11, rt.lastTurnOffHandle)
        assertFalse(b.isRunning())
    }

    @Test
    fun `detachTun runtime throws — не пробрасывает наружу`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, throwOnTurnOff = RuntimeException("boom"))
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, "ini", "/x")
        b.detachTun()
        assertFalse(b.isRunning())
    }

    @Test
    fun `isRunning false до attachTun`() {
        val (b, _) = bridgeWith()
        assertFalse(b.isRunning())
    }

    private class FakeAwgRuntime(
        var returnHandle: Int = 1,
        var throwOnTurnOn: Throwable? = null,
        var throwOnTurnOff: Throwable? = null,
    ) : AwgRuntime {
        var turnOnCalls: Int = 0
        var turnOffCalls: Int = 0
        var lastName: String? = null
        var lastFd: Int = -1
        var lastIni: String? = null
        var lastUapi: String? = null
        var lastTurnOffHandle: Int = -1

        override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
            turnOnCalls++
            lastName = name
            lastFd = tunFd
            lastIni = ini
            lastUapi = uapiPath
            throwOnTurnOn?.let { throw it }
            return returnHandle
        }

        override fun turnOff(handle: Int) {
            turnOffCalls++
            lastTurnOffHandle = handle
            throwOnTurnOff?.let { throw it }
        }
    }
}
