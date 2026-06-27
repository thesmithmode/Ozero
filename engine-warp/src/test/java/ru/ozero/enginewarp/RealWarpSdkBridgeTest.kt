package ru.ozero.enginewarp

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    fun `attachTun handle=0 валидный первый slot — Success (revert sentinel v0_1_5_1)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 0, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", tunFd = 5, iniConfig = validIni, uapiPath = "/x", protector = noopProtector)
        assertIs<WarpSdkBridge.AttachResult.Success>(r)
        assertTrue(
            b.isRunning(),
            "handle=0 = валидный первый tunnel slot (amnezia api.go:123-135: 'for i = 0; i < MaxInt32 " +
                "{ if !exists return i }', errors → return -1). Guard `handle <= 0` в v0.1.5.1 ломал каждый " +
                "чистый WARP старт. Корректный guard — handle < 0.",
        )
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
    fun `attachTun protect throws — Failed и turnOff rollback (AttachResult contract)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 5, socketV4 = 100, socketV6 = 0)
        val (b, _) = bridgeWith(rt)
        val throwingProtector = VpnSocketProtector { error("vpn-service-died") }
        val r = b.attachTun("n", 5, validIni, "/x", throwingProtector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(
            f.reason.contains("vpn-service-died"),
            "AttachResult.Failed.reason должен включать оригинальное сообщение исключения protect",
        )
        assertEquals(
            1,
            rt.turnOffCalls,
            "при throw из protect — обязан вызвать turnOff rollback, иначе AWG handle leak",
        )
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

    @Test
    fun `attachTun — version() читается до turnOn для логирования checkpoint`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100, version = "amneziawg-go-v0.0.20240124")
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
        assertTrue(rt.versionCalls >= 1, "version() должен запрашиваться для pre-JNI checkpoint лога")
    }

    @Test
    fun `attachTun — version() throws не должен ломать turnOn`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100, throwOnVersion = RuntimeException("ver-fail"))
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("n", 5, validIni, "/x", noopProtector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertEquals(1, rt.turnOnCalls)
    }

    @Test
    fun `attachTun дважды без detach — старый handle закрывается перед новым awgTurnOn (anti-leak)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 11, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
        assertEquals(1, rt.turnOnCalls)
        assertEquals(0, rt.turnOffCalls, "первый attach — turnOff не нужен")
        rt.returnHandle = 22
        b.attachTun("n", 7, validIni, "/x", noopProtector)
        assertEquals(2, rt.turnOnCalls, "второй turnOn должен пройти")
        assertEquals(
            1,
            rt.turnOffCalls,
            "перед вторым turnOn ОБЯЗАН быть turnOff старого handle (Go runtime leak guard)",
        )
        assertEquals(11, rt.lastTurnOffHandle, "именно старый handle=11 должен быть закрыт")
        assertTrue(b.isRunning())
    }

    @Test
    fun `attachTun дважды — старый turnOff бросает не должен ломать новый turnOn`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 11, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        b.attachTun("n", 5, validIni, "/x", noopProtector)
        rt.throwOnTurnOff = RuntimeException("stale turnOff blew up")
        rt.returnHandle = 22
        val r = b.attachTun("n", 7, validIni, "/x", noopProtector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertEquals(2, rt.turnOnCalls)
    }

    @Test
    fun `attachTun cycle ×10 без detach — turnOff = turnOn-1 (Nubia SIGABRT regression)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 1, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        repeat(10) { i ->
            rt.returnHandle = 100 + i
            b.attachTun("n", 5, validIni, "/x", noopProtector)
        }
        assertEquals(10, rt.turnOnCalls)
        assertEquals(
            9,
            rt.turnOffCalls,
            "каждый повторный attach закрывает предыдущий handle — нет накопления handles в Go runtime",
        )
        assertTrue(b.isRunning())
    }

    @Test
    fun `attachTun — реалистичный INI с длинным I1 hex blob идёт passthrough без потерь`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 42, socketV4 = 100)
        val (b, _) = bridgeWith(rt)
        val i1Blob = "<b 0xc70000000108ce1bf31eec7d93360000449e227e" +
            "de8c39f4f93b6a0d8c2f1e6b".repeat(50) + ">"
        val realisticIni = buildString {
            append("[Interface]\n")
            append("PrivateKey = yAnz5TF+lXXJte14tji3zlMNq+hd2rYUIgJBgB3fBmk=\n")
            append("Address = 172.16.0.2/32\n")
            append("Address = 2606:4700:110:8a36:df92:102a:9602:fa18/128\n")
            append("DNS = 1.1.1.1, 8.8.8.8\n")
            append("MTU = 1280\n")
            append("Jc = 5\nJmin = 100\nJmax = 200\nS1 = 0\nS2 = 0\n")
            append("H1 = 1\nH2 = 2\nH3 = 3\nH4 = 4\n")
            append("I1 = ").append(i1Blob).append('\n')
            append('\n')
            append("[Peer]\n")
            append("PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=\n")
            append("AllowedIPs = 0.0.0.0/0, ::/0\n")
            append("Endpoint = 162.159.195.1:500\n")
            append("PersistentKeepalive = 25\n")
        }
        val r = b.attachTun("ozero-warp", 7, realisticIni, "/data/uapi", noopProtector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertEquals(realisticIni, rt.lastIni, "реалистичный INI с длинным I1 blob — passthrough байт-в-байт")
        assertTrue(rt.lastIni!!.contains("I1 = <b 0x"), "I1 blob present")
        assertTrue(rt.lastIni!!.contains("Jc = 5"), "AWG поля present")
        assertTrue(rt.lastIni!!.length > 1000, "длинный I1 содержит >1000 байт")
    }

    private class FakeAwgRuntime(
        var returnHandle: Int = 1,
        var throwOnTurnOn: Throwable? = null,
        var throwOnTurnOff: Throwable? = null,
        var throwOnVersion: Throwable? = null,
        var socketV4: Int = 0,
        var socketV6: Int = 0,
        var version: String = "fake-runtime",
        var overrideCombined: Boolean = false,
    ) : AwgRuntime {
        var turnOnCalls: Int = 0
        var turnOffCalls: Int = 0
        var getConfigCalls: Int = 0
        var versionCalls: Int = 0
        var combinedCalls: Int = 0
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
        override fun version(): String {
            versionCalls++
            throwOnVersion?.let { throw it }
            return version
        }
        override fun getConfig(handle: Int): String? {
            getConfigCalls++
            return null
        }

        override fun turnOnAndGetSockets(
            name: String,
            tunFd: Int,
            ini: String,
            uapiPath: String,
        ): AwgTurnOnResult {
            combinedCalls++
            if (!overrideCombined) {
                return super.turnOnAndGetSockets(name, tunFd, ini, uapiPath)
            }
            lastName = name
            lastFd = tunFd
            lastIni = ini
            lastUapi = uapiPath
            throwOnTurnOn?.let { throw it }
            return AwgTurnOnResult(returnHandle, socketV4, socketV6)
        }
    }

    @Test
    fun `attachTun использует turnOnAndGetSockets — один объединённый вызов вместо trio (H1 anti-race)`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 9, socketV4 = 100, socketV6 = 200, overrideCombined = true)
        val (b, _) = bridgeWith(rt)
        val r = b.attachTun("ozero-warp", 5, validIni, "/x", noopProtector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertEquals(1, rt.combinedCalls, "должен вызываться объединённый turnOnAndGetSockets, не trio")
        assertEquals(0, rt.turnOnCalls, "отдельный turnOn НЕ должен вызываться при override combined")
    }

    @Test
    fun `attachTun — combined handle отрицательный → Failed без protect`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = -3, socketV4 = 0, socketV6 = 0, overrideCombined = true)
        val (b, _) = bridgeWith(rt)
        val protectedFds = mutableListOf<Int>()
        val protector = VpnSocketProtector { fd ->
            protectedFds.add(fd)
            true
        }
        val r = b.attachTun("n", 5, validIni, "/x", protector)
        val f = assertIs<WarpSdkBridge.AttachResult.Failed>(r)
        assertTrue(f.reason.contains("handle=-3"))
        assertTrue(protectedFds.isEmpty(), "protect НЕ вызывается при отрицательном handle")
    }

    @Test
    fun `attachTun combined success — protect для v4 и v6 sockets из одного AIDL вызова`() = runTest {
        val rt = FakeAwgRuntime(returnHandle = 7, socketV4 = 111, socketV6 = 222, overrideCombined = true)
        val (b, _) = bridgeWith(rt)
        val protectedFds = mutableListOf<Int>()
        val protector = VpnSocketProtector { fd ->
            protectedFds.add(fd)
            true
        }
        val r = b.attachTun("n", 5, validIni, "/x", protector)
        assertEquals(WarpSdkBridge.AttachResult.Success, r)
        assertTrue(protectedFds.contains(111), "v4 сокет защищён")
        assertTrue(protectedFds.contains(222), "v6 сокет защищён")
    }

    @Test
    fun `sentinel AWG_TEARDOWN_COOLDOWN_MS — cooldown после turnOff для Go goroutine cleanup`() {
        val f = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginewarp/RealWarpSdkBridge.kt",
        )
        assertTrue(f.exists(), "RealWarpSdkBridge.kt не найден: $f")
        val src = f.readText()
        assertTrue(
            src.contains("AWG_TEARDOWN_COOLDOWN_MS"),
            "RealWarpSdkBridge обязан содержать константу AWG_TEARDOWN_COOLDOWN_MS — " +
                "Go goroutines от предыдущего tunnel не останавливаются синхронно при turnOff. " +
                "Без cooldown следующий awgTurnOn (handle=0) получает handshake interference. " +
                "Симптом: lastHsAge=never после rapid config switches, лечится только рестартом приложения.",
        )
        assertTrue(
            src.contains("delay(AWG_TEARDOWN_COOLDOWN_MS)"),
            "detachTun обязан вызывать delay(AWG_TEARDOWN_COOLDOWN_MS) после awgTurnOff",
        )
    }

    @Test
    fun `lifecycle operations serialized before closeRuntimeIfIdle`() {
        val f = java.io.File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/enginewarp/RealWarpSdkBridge.kt",
        )
        assertTrue(f.exists(), "RealWarpSdkBridge.kt не найден: $f")
        val src = f.readText()
        assertTrue(
            src.contains("private val lifecycleLock = Mutex()") &&
                src.contains("lifecycleLock.withLock") &&
                src.contains("closeRuntimeIfIdle()"),
            "RealWarpSdkBridge обязан сериализовать attach/start/stop/detach вокруг closeRuntimeIfIdle, " +
                "иначе runtime можно закрыть одновременно с новым attach/start.",
        )
    }

    @Test
    fun `concurrent detachTun не вызывает double awgTurnOff на одном handle`() = runTest {
        val runtime = FakeAwgRuntime(returnHandle = 7, socketV4 = 100)
        val (bridge, _) = bridgeWith(runtime)
        val attached = bridge.attachTun(
            "wg-test",
            tunFd = 7,
            iniConfig = validIni,
            uapiPath = "/tmp",
            protector = noopProtector,
        )
        assertIs<WarpSdkBridge.AttachResult.Success>(attached)
        val beforeOff = runtime.turnOffCalls
        coroutineScope {
            val j1 = launch { bridge.detachTun() }
            val j2 = launch { bridge.detachTun() }
            j1.join()
            j2.join()
        }
        assertEquals(
            beforeOff + 1,
            runtime.turnOffCalls,
            "Параллельный detach должен вызвать awgTurnOff ровно один раз — AtomicInteger.getAndSet",
        )
        assertFalse(bridge.isRunning())
    }
}
