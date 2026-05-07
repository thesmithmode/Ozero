package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import ru.ozero.enginescore.VpnSocketProtector
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RealWarpSdkBridgeTest {

    private val noopProtector = VpnSocketProtector { true }

    private val validIni = """
        [Interface]
        PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
        Address = 10.0.0.2/32

        [Peer]
        PublicKey = xTIBA9rboUyYM73OZE9hQNwY9DYBJ7cT/AKO1S6jPBI=
        AllowedIPs = 0.0.0.0/0
        Endpoint = 1.2.3.4:51820
    """.trimIndent()

    private fun bridgeWith(
        runtime: AwgRuntime = FakeAwgRuntime(),
    ): Pair<RealWarpSdkBridge, AwgRuntime> = RealWarpSdkBridge(runtime) to runtime

    @Test
    fun `attachTun валидный fd → Success и сохраняет handle`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun(
            tunnelName = "ozero-warp",
            tunFd = 5,
            iniConfig = validIni,
            uapiPath = "/x",
            protector = noopProtector,
        )
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertTrue(b.isRunning())
        assertEquals(5, rt.lastFd)
        assertEquals("ozero-warp", rt.lastName)
        assertEquals("/x", rt.lastUapi)
        val passed = rt.lastIni!!
        val piIdx = passed.indexOf("PrivateKey")
        val peerIdx = passed.indexOf("[Peer]")
        assertTrue(piIdx in 0 until peerIdx, "PrivateKey должен идти ПЕРЕД [Peer] (canonical Interface)")
        assertTrue(piIdx > passed.indexOf("Address"), "canonical: PrivateKey ПОСЛЕ Address — am-go parser ожидает этот порядок")
    }

    @Test
    fun `attachTun raw INI с PrivateKey впереди → канонизирован через Config_toAwgQuickString`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        val rawIniWithPrivateKeyFirst = """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.0.0.2/32
            MTU = 1280
            Jc = 5
            Jmin = 100
            Jmax = 200

            [Peer]
            PublicKey = xTIBA9rboUyYM73OZE9hQNwY9DYBJ7cT/AKO1S6jPBI=
            AllowedIPs = 0.0.0.0/0
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        val r = b.attachTun("ozero-warp", 5, rawIniWithPrivateKeyFirst, "/x", noopProtector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        val canonical = rt.lastIni!!
        val pkIdx = canonical.indexOf("PrivateKey")
        val jcIdx = canonical.indexOf("Jc =")
        assertTrue(jcIdx in 0 until pkIdx, "canonical INI: AWG-поля (Jc) должны идти ПЕРЕД PrivateKey")
    }

    @Test
    fun `attachTun INI с битым ключом → BadConfigException → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val brokenIni = "[Interface]\nPrivateKey = NOT_A_VALID_BASE64_KEY!!!\n\n[Peer]\nPublicKey = also_broken\n"
        val r = b.attachTun("n", 5, brokenIni, "/x", noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("INI parse"), "reason='${f.reason}' — должно содержать 'INI parse'")
        assertEquals(0, rt.turnOnCalls, "runtime НЕ вызывается при невалидном INI — иначе SIGSEGV в am-go native")
    }

    @Test
    fun `attachTun negative fd → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = -1, iniConfig = validIni, uapiPath = "/x", protector = noopProtector)
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun blank ini → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 1, iniConfig = "", uapiPath = "/x", protector = noopProtector)
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun blank uapi → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 1, iniConfig = validIni, uapiPath = "", protector = noopProtector)
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(0, rt.turnOnCalls)
    }

    @Test
    fun `attachTun negative handle от runtime → Failed`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = -2)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 5, iniConfig = validIni, uapiPath = "/x", protector = noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("handle=-2"))
        assertFalse(b.isRunning())
    }

    @Test
    fun `attachTun runtime бросает → Failed с текстом ошибки`() = runTest {
        val rt = FakeAwgRuntime(throwOnTurnOn = RuntimeException("native crash"))
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 5, iniConfig = validIni, uapiPath = "/x", protector = noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("native crash"))
    }

    @Test
    fun `attachTun success — protect зовётся для v4 и v6 sockets`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100, socketV6 = 200)
        val (b, _) = bridgeWith(rt)
        val protectedFds = mutableListOf<Int>()
        val protector = VpnSocketProtector { fd ->
            protectedFds.add(fd)
            true
        }
        b.attachTun("n", 5, validIni, "/x", protector)
        assertTrue(protectedFds.contains(100))
        assertTrue(protectedFds.contains(200))
    }

    @Test
    fun `attachTun — оба сокета невалидны → Failed и turnOff вызван (anti-loop guard)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 0, socketV6 = 0)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", 5, validIni, "/x", noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("protect"))
        assertEquals(1, rt.turnOffCalls)
        assertFalse(b.isRunning())
    }

    @Test
    fun `attachTun — protect v4 false → Failed и turnOff (rollback)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100, socketV6 = 0)
        val (b, _) = bridgeWith(rt)
        val protector = VpnSocketProtector { false }
        val r = b.attachTun("n", 5, validIni, "/x", protector)
        assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertEquals(1, rt.turnOffCalls)
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
        val rt = FakeAwgRuntime(returnHandle = 11, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
        b.detachTun()
        assertEquals(1, rt.turnOffCalls)
        assertEquals(11, rt.lastTurnOffHandle)
        assertFalse(b.isRunning())
    }

    @Test
    fun `detachTun runtime throws — не пробрасывает наружу`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100, throwOnTurnOff = RuntimeException("boom"))
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
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
        var socketV4: Int = 0,
        var socketV6: Int = 0,
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

        override fun getSocketV4(handle: Int): Int = socketV4
        override fun getSocketV6(handle: Int): Int = socketV6
    }
}
