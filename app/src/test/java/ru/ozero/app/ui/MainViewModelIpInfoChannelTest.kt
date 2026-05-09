package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainViewModelIpInfoChannelTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/app/ui/MainViewModel.kt")
        assertTrue(f.exists(), "MainViewModel.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `IP_INFO_WARMUP_MS не меньше 8 секунд — URnetwork peer-search долгий`() {
        val regex = Regex("IP_INFO_WARMUP_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("IP_INFO_WARMUP_MS не найден")
        val warmupMs = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            warmupMs >= 8_000L,
            "IP_INFO_WARMUP_MS обязан быть >= 8000ms — иначе URnetwork не успевает поднять " +
                "P2P пиров и IP-detect возвращает ошибку до того как туннель готов. Fact=$warmupMs",
        )
    }

    @Test
    fun `IP_INFO_RETRY_ATTEMPTS не меньше 2`() {
        val regex = Regex("IP_INFO_RETRY_ATTEMPTS\\s*=\\s*(\\d+)")
        val m = regex.find(source) ?: error("IP_INFO_RETRY_ATTEMPTS не определён")
        val attempts = m.groupValues[1].toInt()
        assertTrue(
            attempts >= 2,
            "IP_INFO_RETRY_ATTEMPTS обязан быть >= 2 — flaky сеть требует retry. Fact=$attempts",
        )
    }

    @Test
    fun `fetchIpInfoViaEngine задерживает между retries`() {
        val body = source.substringAfter("private suspend fun fetchIpInfoViaEngine")
            .substringBefore("private suspend fun fetchOnce")
        assertTrue(
            body.contains("delay(IP_INFO_RETRY_DELAY_MS)"),
            "fetchIpInfoViaEngine обязан задерживать между retries.",
        )
    }

    @Test
    fun `fetchOnce роутит SOCKS-engine через 127_0_0_1 + fetchVia`() {
        val body = source.substringAfter("private suspend fun fetchOnce")
            .substringBefore("private fun supportsIpProbe")
        assertTrue(
            body.contains("socksPort > 0"),
            "fetchOnce обязан различать SOCKS-engine по socksPort > 0. Body:\n$body",
        )
        assertTrue(
            body.contains("BYEDPI_LOOPBACK") || body.contains("\"127.0.0.1\""),
            "SOCKS proxy host должен быть 127.0.0.1 (константа или литерал). Body:\n$body",
        )
        assertTrue(
            body.contains("ipInfoProvider.fetchVia("),
            "SOCKS-engine обязан использовать fetchVia(host, port) — иначе IP-fetch " +
                "идёт мимо SOCKS прокси.",
        )
    }

    @Test
    fun `supportsIpProbe — BYEDPI всегда поддерживается, остальные только при socksPort больше 0`() {
        val body = source.substringAfter("private fun supportsIpProbe")
            .substringBefore("fun onConnectClick")
        assertTrue(
            body.contains("EngineId.BYEDPI"),
            "supportsIpProbe обязан явно whitelist'ить BYEDPI " +
                "(работает через SOCKS даже если порт=0 на промежуточных state). Body:\n$body",
        )
        assertTrue(
            body.contains("socksPort > 0"),
            "supportsIpProbe обязан считать любой engine с активным SOCKS-портом " +
                "пробируемым. Body:\n$body",
        )
    }

    @Test
    fun `fetchOnce не использует fetchViaSocketFactory — Network_bindSocketToNetwork даёт EPERM на VPN net`() {
        val body = source.substringAfter("private suspend fun fetchOnce")
            .substringBefore("fun onConnectClick")
        assertFalse(
            body.contains("fetchViaSocketFactory"),
            "fetchOnce обязан НЕ использовать fetchViaSocketFactory: " +
                "Network.socketFactory.createSocket() для VPN-network вызывает bindSocketToNetwork " +
                "и получает EPERM (Operation not permitted) — system-only привилегия. " +
                "Self-traffic роутится через TUN автоматически, " +
                "т.к. TunBuilderConfigurator более не excludeSelf по умолчанию.",
        )
    }

    @Test
    fun `MainViewModel не зависит от VpnNetworkLocator`() {
        assertFalse(
            source.contains("VpnNetworkLocator"),
            "MainViewModel обязан НЕ инъектить VpnNetworkLocator: bind на VPN network даёт EPERM, " +
                "fix — не excludeSelf в TunBuilder, тогда self-traffic роутится через TUN автоматически.",
        )
    }
}
