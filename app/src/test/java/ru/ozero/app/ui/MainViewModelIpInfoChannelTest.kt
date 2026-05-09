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
    fun `resolveIpInfoWithRetry задерживает между retries`() {
        val body = source.substringAfter("private suspend fun resolveIpInfoWithRetry")
            .substringBefore("private suspend fun resolveOnce")
        assertTrue(
            body.contains("delay(IP_INFO_RETRY_DELAY_MS)"),
            "resolveIpInfoWithRetry обязан задерживать между retries.",
        )
    }

    @Test
    fun `resolveOnce роутит SOCKS-engine через IpProbeRoute_Socks + fetchVia`() {
        val body = source.substringAfter("private suspend fun resolveOnce")
            .substringBefore("private fun Result<IpInfo>.toState")
        assertTrue(
            body.contains("IpProbeRoute.Socks"),
            "resolveOnce обязан различать SOCKS-engine через IpProbeRoute.Socks. Body:\n$body",
        )
        assertTrue(
            body.contains("ipInfoProvider.fetchVia("),
            "SOCKS-route обязан использовать fetchVia(host, port) — иначе IP-fetch " +
                "идёт мимо SOCKS прокси. Body:\n$body",
        )
        assertTrue(
            body.contains("ipInfoProvider.fetch().toState()") ||
                body.contains("ipInfoProvider.fetch()"),
            "Default-route обязан использовать fetch() напрямую (WARP full-tun). Body:\n$body",
        )
    }

    @Test
    fun `resolveOnce обрабатывает все четыре варианта IpProbeRoute`() {
        val body = source.substringAfter("private suspend fun resolveOnce")
            .substringBefore("private fun Result<IpInfo>.toState")
        assertTrue(
            body.contains("IpProbeRoute.Default"),
            "resolveOnce обязан явно обрабатывать IpProbeRoute.Default → fetch(). Body:\n$body",
        )
        assertTrue(
            body.contains("IpProbeRoute.Socks"),
            "resolveOnce обязан явно обрабатывать IpProbeRoute.Socks → fetchVia(). Body:\n$body",
        )
        assertTrue(
            body.contains("IpProbeRoute.StaticLocation"),
            "resolveOnce обязан явно обрабатывать IpProbeRoute.StaticLocation — " +
                "URnetwork даёт страну без HTTP probe. Body:\n$body",
        )
        assertTrue(
            body.contains("IpProbeRoute.Unavailable"),
            "resolveOnce обязан явно обрабатывать IpProbeRoute.Unavailable → IpInfoState.Error. " +
                "Body:\n$body",
        )
    }

    @Test
    fun `resolveOnce не использует fetchViaSocketFactory — bindSocketToNetwork даёт EPERM на VPN net`() {
        val body = source.substringAfter("private suspend fun resolveOnce")
            .substringBefore("private fun Result<IpInfo>.toState")
        assertFalse(
            body.contains("fetchViaSocketFactory"),
            "resolveOnce обязан НЕ использовать fetchViaSocketFactory: " +
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

    @Test
    fun `MainViewModel принимает Set EnginePlugin через DI`() {
        assertTrue(
            source.contains("Set<@JvmSuppressWildcards EnginePlugin>") ||
                source.contains("Set<EnginePlugin>"),
            "MainViewModel обязан получать Set<EnginePlugin> через @Inject — " +
                "IP-routing делегируется в plugin.ipProbeRoute(). " +
                "Без @JvmSuppressWildcards Hilt не свяжет multibinding с Kotlin Set.",
        )
        assertTrue(
            source.contains("plugin.ipProbeRoute(") ||
                source.contains(".ipProbeRoute("),
            "MainViewModel обязан звать plugin.ipProbeRoute(socksPort) — " +
                "engine сам решает Default/Socks/StaticLocation/Unavailable.",
        )
    }
}
