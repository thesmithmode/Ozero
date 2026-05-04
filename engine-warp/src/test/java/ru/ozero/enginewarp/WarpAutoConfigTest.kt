package ru.ozero.enginewarp

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WarpAutoConfigTest {

    private val sampleConf = """
        [Interface]
        PrivateKey = duLmWkD6Pz6fqd+5/Wsh+aDwyaT8w+5ofxDZ3Z3l1c0=
        Address = 172.16.0.2, 2606:4700:110:8af5:7421:75f0:3c0f:f366
        DNS = 1.1.1.1, 2606:4700:4700::1111
        MTU = 1280

        [Peer]
        PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
        AllowedIPs = 0.0.0.0/0, ::/0
        Endpoint = engage.cloudflareclient.com:4500
        PersistentKeepalive = 25
    """.trimIndent()

    @Test
    fun `register parses raw WireGuard conf body to WarpConfig`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isSuccess, "expected success, got ${result.exceptionOrNull()?.message}")
        val cfg = result.getOrThrow()
        assertEquals("duLmWkD6Pz6fqd+5/Wsh+aDwyaT8w+5ofxDZ3Z3l1c0=", cfg.privateKey)
        assertEquals(
            "bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=",
            cfg.peerPublicKey,
        )
        assertEquals("engage.cloudflareclient.com:4500", cfg.peerEndpoint)
        assertTrue(cfg.interfaceAddressV4.startsWith("172.16.0.2"))
        assertTrue(cfg.interfaceAddressV6.startsWith("2606:4700:110"))
        assertEquals(1280, cfg.mtu)
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), cfg.dnsServers)
        assertEquals(25, cfg.keepaliveSeconds)
        assertEquals("", cfg.accountLicense, "proxy mirrors не возвращают Cloudflare license")
        assertEquals("", cfg.publicKey, "proxy mirrors не возвращают public key (priv достаточен)")
    }

    @Test
    fun `register accepts JSON wrapper success_data with conf string`() = runTest {
        val wrapped = """{"success":true,"data":${escape(sampleConf)}}"""
        val http = FakeHttpClient(Result.success(wrapped))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isSuccess, "JSON-wrapped success ответ должен парситься")
    }

    @Test
    fun `register accepts JSON wrapper configs map first value`() = runTest {
        val wrapped = """{"configs":{"Текущая страна":${escape(sampleConf)}}}"""
        val http = FakeHttpClient(Result.success(wrapped))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isSuccess, "Map<country, conf> JSON ответ должен парситься")
    }

    @Test
    fun `register treats success_false as mirror failure (proxy перегружен)`() = runTest {
        val overloaded =
            """{"success":false,"message":"Слишком много желающих — попробуй через 10 секунд"}"""
        val http = FakeHttpClient(Result.success(overloaded))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isFailure, "success=false из mirror — это failure, не успех")
    }

    @Test
    fun `register sends fixed JSON body without client public key`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = singleMirrorConfig(http)

        auto.register()

        val call = assertNotNull(http.lastCall)
        assertTrue(call.body.contains("\"selectedServices\":[]"))
        assertTrue(call.body.contains("\"siteMode\":\"all\""))
        assertTrue(call.body.contains("\"deviceType\":\"computer\""))
        assertTrue(call.body.contains("\"endpoint\":\"162.159.195.1:500\""))
        assertTrue(
            !call.body.contains("\"key\""),
            "Proxy-зеркала генерируют ключ серверной стороной — клиент не отправляет",
        )
    }

    @Test
    fun `register hits one of provided mirrors`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = ProxyWarpAutoConfig(
            httpClient = http,
            mirrors = listOf("https://only-mirror.example/api/warp"),
            concurrency = 1,
            shuffler = { it },
        )

        auto.register()

        assertEquals("https://only-mirror.example/api/warp", http.lastCall?.url)
    }

    @Test
    fun `register network failure returns Result_failure`() = runTest {
        val http = FakeHttpClient(Result.failure(IOException("boom")))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isFailure)
    }

    @Test
    fun `register garbage body without Interface section fails`() = runTest {
        val http = FakeHttpClient(Result.success("Payment required\nDEPLOYMENT_DISABLED"))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Interface", ignoreCase = true) == true,
        )
    }

    @Test
    fun `register conf без PrivateKey возвращает failure`() = runTest {
        val malformed = """
            [Interface]
            Address = 1.2.3.4
            [Peer]
            PublicKey = abc
            Endpoint = h:1
        """.trimIndent()
        val http = FakeHttpClient(Result.success(malformed))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("PrivateKey") == true)
    }

    @Test
    fun `register conf без Peer endpoint возвращает failure`() = runTest {
        val malformed = """
            [Interface]
            PrivateKey = priv
            Address = 1.2.3.4
            [Peer]
            PublicKey = pub
        """.trimIndent()
        val http = FakeHttpClient(Result.success(malformed))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Endpoint") == true)
    }

    @Test
    fun `register пустой список зеркал возвращает failure`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = ProxyWarpAutoConfig(
            httpClient = http,
            mirrors = emptyList(),
            concurrency = 1,
        )

        val result = auto.register()

        assertTrue(result.isFailure)
    }

    @Test
    fun `register пробует все зеркала когда первые fail`() = runTest {
        val responses = ArrayDeque(
            listOf(
                Result.failure<String>(IOException("503")),
                Result.failure<String>(IOException("read timeout")),
                Result.success(sampleConf),
            ),
        )
        val http = QueueHttpClient(responses)
        val auto = ProxyWarpAutoConfig(
            httpClient = http,
            mirrors = listOf("https://m1/api", "https://m2/api", "https://m3/api"),
            concurrency = 1,
            shuffler = { it },
        )

        val result = auto.register()

        assertTrue(result.isSuccess, "Должен fallthrough на третье зеркало после 2 fail")
        assertEquals(3, http.callCount)
    }

    @Test
    fun `parses configBase64 response from real mirror format`() = runTest {
        val b64 = Base64.getEncoder().encodeToString(sampleConf.toByteArray())
        val body = """{"success":true,"content":{"configBase64":"$b64","qrCodeBase64":"abc"}}"""
        val http = FakeHttpClient(Result.success(body))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertTrue(result.isSuccess, "configBase64 ответ обязан декодироваться")
        val cfg = result.getOrThrow()
        assertEquals("duLmWkD6Pz6fqd+5/Wsh+aDwyaT8w+5ofxDZ3Z3l1c0=", cfg.privateKey)
        assertEquals("bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=", cfg.peerPublicKey)
        assertEquals("engage.cloudflareclient.com:4500", cfg.peerEndpoint)
    }

    @Test
    fun `register парсит AWG параметры когда присутствуют в conf`() = runTest {
        val awgConf = """
            [Interface]
            PrivateKey = duLmWkD6Pz6fqd+5/Wsh+aDwyaT8w+5ofxDZ3Z3l1c0=
            Address = 172.16.0.2, 2606:4700:110:8af5:7421:75f0:3c0f:f366
            DNS = 1.1.1.1, 2606:4700:4700::1111
            MTU = 1280
            Jc = 7
            Jmin = 50
            Jmax = 150
            S1 = 10
            S2 = 20
            H1 = 100
            H2 = 200
            H3 = 300
            H4 = 400

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = engage.cloudflareclient.com:4500
            PersistentKeepalive = 25
        """.trimIndent()
        val http = FakeHttpClient(Result.success(awgConf))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        val cfg = result.getOrThrow()
        val p = cfg.awgParams
        assertEquals(7, p.junkPacketCount)
        assertEquals(50, p.junkPacketMinSize)
        assertEquals(150, p.junkPacketMaxSize)
        assertEquals(10, p.initPacketJunkSize)
        assertEquals(20, p.responsePacketJunkSize)
        assertEquals(100L, p.initPacketMagicHeader)
        assertEquals(200L, p.responsePacketMagicHeader)
        assertEquals(300L, p.cookieReplyMagicHeader)
        assertEquals(400L, p.transportMagicHeader)
    }

    @Test
    fun `register использует AwgParams defaults когда AWG строки отсутствуют`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertEquals(AwgParams(), result.getOrThrow().awgParams)
    }

    @Test
    fun `register fallback на default если AWG значение невалидно`() = runTest {
        val badJcConf = sampleConf.replace("[Interface]", "[Interface]\nJc = notanumber")
        val http = FakeHttpClient(Result.success(badJcConf))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertEquals(AwgParams.DEFAULT_JC, result.getOrThrow().awgParams.junkPacketCount)
    }

    @Test
    fun `AWG параметры выживают configBase64 roundtrip`() = runTest {
        val awgConf = """
            [Interface]
            PrivateKey = duLmWkD6Pz6fqd+5/Wsh+aDwyaT8w+5ofxDZ3Z3l1c0=
            Address = 172.16.0.2, 2606:4700:110:8af5:7421:75f0:3c0f:f366
            Jc = 9
            H1 = 999

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            Endpoint = engage.cloudflareclient.com:4500
        """.trimIndent()
        val b64 = Base64.getEncoder().encodeToString(awgConf.toByteArray())
        val body = """{"success":true,"content":{"configBase64":"$b64"}}"""
        val http = FakeHttpClient(Result.success(body))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        val p = result.getOrThrow().awgParams
        assertEquals(9, p.junkPacketCount)
        assertEquals(999L, p.initPacketMagicHeader)
    }

    @Test
    fun `register парсит DNS из conf строки`() = runTest {
        val confWithDns = sampleConf
        val http = FakeHttpClient(Result.success(confWithDns))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        val cfg = result.getOrThrow()
        assertEquals(listOf("1.1.1.1", "2606:4700:4700::1111"), cfg.dnsServers)
    }

    @Test
    fun `register использует DEFAULT_DNS если DNS отсутствует в conf`() = runTest {
        val confWithoutDns = sampleConf.lines().filterNot { it.trim().startsWith("DNS") }.joinToString("\n")
        val http = FakeHttpClient(Result.success(confWithoutDns))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        val cfg = result.getOrThrow()
        assertEquals(WarpConfig.DEFAULT_DNS, cfg.dnsServers)
    }

    @Test
    fun `register парсит PersistentKeepalive из conf`() = runTest {
        val http = FakeHttpClient(Result.success(sampleConf))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertEquals(25, result.getOrThrow().keepaliveSeconds)
    }

    @Test
    fun `register использует DEFAULT_KEEPALIVE если PersistentKeepalive отсутствует`() = runTest {
        val confWithoutKeepalive = sampleConf.lines()
            .filterNot { it.trim().startsWith("PersistentKeepalive") }
            .joinToString("\n")
        val http = FakeHttpClient(Result.success(confWithoutKeepalive))
        val auto = singleMirrorConfig(http)

        val result = auto.register()

        assertEquals(WarpConfig.DEFAULT_KEEPALIVE, result.getOrThrow().keepaliveSeconds)
    }

    @Test
    fun `default mirrors список содержит CYBERPORTAL_X URLs (sentinel против регрессии endpoint)`() {
        val urls = ProxyWarpAutoConfig.DEFAULT_MIRRORS
        assertTrue(urls.size >= 70, "Список зеркал из CYBERPORTAL_X-1.0.2 содержит 78 URL")
        assertTrue(
            urls.none { it.contains("api.cloudflareclient.com") },
            "api.cloudflareclient.com блокируется ТСПУ в РФ — НЕ должен быть в списке зеркал",
        )
        assertTrue(
            urls.all { it.endsWith("/api/warp") },
            "Все зеркала используют /api/warp endpoint",
        )
        assertTrue(
            urls.any { it.contains("netlify.app") },
            "Часть зеркал hosted на Netlify",
        )
        assertTrue(
            urls.any { it.contains("vercel.app") },
            "Часть зеркал hosted на Vercel",
        )
        assertTrue(
            urls.any { it.contains("cyberportal.workers.dev") },
            "Часть зеркал на Cloudflare Workers",
        )
    }

    @Test
    fun `request body schema sentinel — поля совпадают с CYBERPORTAL_X`() {
        val body = ProxyWarpAutoConfig.REQUEST_BODY
        assertTrue(body.contains("\"selectedServices\":[]"))
        assertTrue(body.contains("\"siteMode\":\"all\""))
        assertTrue(body.contains("\"deviceType\":\"computer\""))
        assertTrue(body.contains("\"endpoint\":\"162.159.195.1:500\""))
        assertTrue(
            !body.contains("\"key\""),
            "Поле \"key\" клиентского pubkey запрещено — proxy-зеркала генерируют ключ сервером",
        )
    }

    private fun singleMirrorConfig(http: HttpClient): ProxyWarpAutoConfig =
        ProxyWarpAutoConfig(
            httpClient = http,
            mirrors = listOf("https://test-mirror/api/warp"),
            concurrency = 1,
            shuffler = { it },
        )

    private fun escape(s: String): String {
        val sb = StringBuilder("\"")
        s.forEach { c ->
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private class FakeHttpClient(
        private val response: Result<String>,
    ) : HttpClient {
        data class Call(val url: String, val body: String, val userAgent: String)

        var lastCall: Call? = null
            private set

        override suspend fun postJson(
            url: String,
            body: String,
            userAgent: String,
        ): Result<String> {
            lastCall = Call(url, body, userAgent)
            return response
        }
    }

    private class QueueHttpClient(
        private val queue: ArrayDeque<Result<String>>,
    ) : HttpClient {
        var callCount: Int = 0
            private set

        override suspend fun postJson(
            url: String,
            body: String,
            userAgent: String,
        ): Result<String> {
            callCount++
            return if (queue.isEmpty()) {
                Result.failure(IOException("queue exhausted"))
            } else {
                queue.removeFirst()
            }
        }
    }
}
