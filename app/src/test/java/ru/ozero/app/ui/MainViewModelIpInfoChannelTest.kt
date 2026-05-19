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
    fun `IP_INFO_WARMUP_MS короткий — IP-карточка показывается быстро после старта VPN (T5 fix)`() {
        val regex = Regex("IP_INFO_WARMUP_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("IP_INFO_WARMUP_MS не найден")
        val warmupMs = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            warmupMs in 100L..1_000L,
            "IP_INFO_WARMUP_MS должен быть в диапазоне [100..1000]ms — T5 fix: плашка обязана появиться " +
                "сразу после старта VPN, не через 2-3 секунды. URnetwork location reактивно обновляется " +
                "через URNETWORK_LOCATION_POLL_MS polling, поэтому первый fetch может быть стартовым stub. " +
                "Fact=$warmupMs",
        )
    }

    @Test
    fun `URNETWORK_LOCATION_POLL_MS определён для реактивного обновления выходного узла`() {
        val regex = Regex("URNETWORK_LOCATION_POLL_MS\\s*=\\s*(\\d[\\d_]*)L")
        val m = regex.find(source) ?: error("URNETWORK_LOCATION_POLL_MS не найден")
        val pollMs = m.groupValues[1].replace("_", "").toLong()
        assertTrue(
            pollMs in 2_000L..10_000L,
            "URNETWORK_LOCATION_POLL_MS обязан быть 2s–10s: реже — UX деградирует, " +
                "чаще — батарея. Fact=$pollMs",
        )
    }

    @Test
    fun `MainViewModel содержит polling-flow для URnetwork location через flatMapLatest + WhileSubscribed`() {
        assertTrue(
            source.contains("flatMapLatest"),
            "MainViewModel обязан использовать flatMapLatest для URnetwork location polling — " +
                "отменяет предыдущий цикл при смене движка.",
        )
        assertTrue(
            source.contains("URNETWORK_LOCATION_POLL_MS"),
            "Polling-цикл обязан использовать URNETWORK_LOCATION_POLL_MS.",
        )
        assertTrue(
            source.contains("WhileSubscribed"),
            "Polling обязан запускаться только при наличии подписчика — SharingStarted.WhileSubscribed. " +
                "Иначе while(true) в init блокирует runTest auto-advanceUntilIdle (CI hang).",
        )
        assertTrue(
            source.contains("urnetworkLocationOverride"),
            "Override-flow обязан называться urnetworkLocationOverride — комбинируется с _ipInfo.",
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
                "Engine сам решает IpProbeRoute (Default/Socks/StaticLocation/Unavailable).",
        )
    }

    @Test
    fun `MainViewModel не зависит от VpnNetworkLocator`() {
        assertFalse(
            source.contains("VpnNetworkLocator"),
            "MainViewModel обязан НЕ инъектить VpnNetworkLocator: bind на VPN network даёт EPERM. " +
                "IP-probe routing делегирован в plugin.ipProbeRoute() — engine выбирает стратегию.",
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
