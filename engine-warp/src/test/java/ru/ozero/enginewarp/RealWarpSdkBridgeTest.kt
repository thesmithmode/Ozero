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
        assertEquals(validIni, rt.lastIni, "INI должен передаваться runtime БЕЗ модификаций (passthrough)")
    }

    @Test
    fun `attachTun raw INI с PrivateKey впереди — passthrough как есть`() = runTest {
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
        assertEquals(rawIniWithPrivateKeyFirst, rt.lastIni, "passthrough — никакой канонизации, никаких потерь полей")
    }

    @Test
    fun `attachTun INI с I1 — passthrough сохраняет I1 ровно как в исходнике`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        val iniWithI1 = """
            [Interface]
            PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=
            Address = 10.0.0.2/32
            Jc = 5
            Jmin = 100
            Jmax = 200
            S1 = 0
            S2 = 0
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0xc70000000108ce1bf31eec7d93360000449e227e>

            [Peer]
            PublicKey = xTIBA9rboUyYM73OZE9hQNwY9DYBJ7cT/AKO1S6jPBI=
            AllowedIPs = 0.0.0.0/0
            Endpoint = 1.2.3.4:51820
        """.trimIndent()
        b.attachTun("ozero-warp", 5, iniWithI1, "/x", noopProtector)
        val passed = rt.lastIni!!
        assertTrue(
            passed.contains("I1 = <b 0xc70000000108ce1bf31eec7d93360000449e227e>"),
            "I1 должен присутствовать в передаваемом am-go INI как есть — без потерь полей",
        )
        assertEquals(iniWithI1, passed, "INI passthrough — ноль модификаций")
    }

    @Test
    fun `attachTun INI без секции Interface → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val brokenIni = "[Peer]\nPublicKey = abc\nEndpoint = 1.2.3.4:5\n"
        val r = b.attachTun("n", 5, brokenIni, "/x", noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("Interface"), "reason='${f.reason}' должно содержать 'Interface'")
        assertEquals(0, rt.turnOnCalls, "runtime НЕ вызывается без [Interface]")
    }

    @Test
    fun `attachTun INI без секции Peer → Failed без вызова runtime`() = runTest {
        val rt = FakeAwgRuntime()
        val (b, _) = bridgeWith(rt)
        val brokenIni = "[Interface]\nPrivateKey = abc\n"
        val r = b.attachTun("n", 5, brokenIni, "/x", noopProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("Peer"))
        assertEquals(0, rt.turnOnCalls)
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
    fun `attachTun success — getConfig НЕ вызывается (нет watchdog poll)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
        assertEquals(
            0,
            rt.getConfigCalls,
            "PORTAL_WG не делает poll awgGetConfig — наш bridge тоже не должен (потенциальный SIGSEGV)",
        )
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
        var getConfigCalls: Int = 0
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
        override fun getConfig(handle: Int): String? {
            getConfigCalls++
            return null
        }
    }
}
