package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
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
    fun `fetchIpInfoViaEngine использует fetchVia с engine-aware proxy`() {
        val body = source.substringAfter("private suspend fun fetchIpInfoViaEngine")
            .substringBefore("private fun engineSocksProxy")
        assertTrue(
            body.contains("ipInfoProvider.fetchVia("),
            "fetchIpInfoViaEngine обязан использовать fetchVia (engine-aware), не fetch() — " +
                "иначе для ByeDPI запрос идёт мимо SOCKS proxy и определяет IP до туннеля.",
        )
        assertTrue(
            body.contains("delay(IP_INFO_RETRY_DELAY_MS)"),
            "fetchIpInfoViaEngine обязан задерживать между retries.",
        )
    }

    @Test
    fun `engineSocksProxy возвращает SOCKS только для BYEDPI`() {
        val body = source.substringAfter("private fun engineSocksProxy")
            .substringBefore("private companion object")
        assertTrue(
            body.contains("EngineId.BYEDPI"),
            "engineSocksProxy обязан выделять BYEDPI как socks-engine. Body:\n$body",
        )
        assertTrue(
            body.contains("\"127.0.0.1\""),
            "ByeDPI socks proxy host = 127.0.0.1. Body:\n$body",
        )
    }
}
