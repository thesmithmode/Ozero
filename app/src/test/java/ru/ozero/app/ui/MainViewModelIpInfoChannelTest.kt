package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainViewModelIpInfoChannelTest {

    private val mainSource by lazy {
        source("src/main/java/ru/ozero/app/ui/MainViewModel.kt")
    }

    private val resolverSource by lazy {
        source("src/main/java/ru/ozero/app/ui/ExitNodeResolver.kt")
    }

    @Test
    fun `IP_INFO_WARMUP_MS stays short`() {
        val regex = Regex("IP_INFO_WARMUP_MS\\s*=\\s*(\\d[\\d_]*)L")
        val ms = regex.find(mainSource)?.groupValues?.get(1)?.replace("_", "")?.toLong()
            ?: error("IP_INFO_WARMUP_MS not found")
        assertTrue(ms in 100L..1_000L, "IP card must resolve shortly after connect. Fact=$ms")
    }

    @Test
    fun `URnetwork location polling stays subscribed and bounded`() {
        val regex = Regex("URNETWORK_LOCATION_POLL_MS\\s*=\\s*(\\d[\\d_]*)L")
        val ms = regex.find(mainSource)?.groupValues?.get(1)?.replace("_", "")?.toLong()
            ?: error("URNETWORK_LOCATION_POLL_MS not found")
        assertTrue(ms in 2_000L..10_000L, "URnetwork location poll must stay in 2s..10s. Fact=$ms")
        assertTrue(mainSource.contains("flatMapLatest"))
        assertTrue(mainSource.contains("SharingStarted.WhileSubscribed"))
        assertTrue(mainSource.contains("urnetworkLocationOverride"))
    }

    @Test
    fun `MainViewModel delegates exit-node policy to engines and resolver`() {
        assertTrue(mainSource.contains("Set<@JvmSuppressWildcards EnginePlugin>"))
        assertTrue(mainSource.contains("plugin.exitNodeStrategy("))
        assertTrue(mainSource.contains("exitNodeResolver.resolve(strategy)"))
        assertFalse(mainSource.contains("IpProbeRoute"), "MainViewModel must not know route internals")
    }

    @Test
    fun `resolve retry keeps delay between attempts`() {
        val body = mainSource.substringAfter("private suspend fun resolveIpInfoWithRetry")
            .substringBefore("private suspend fun resolveOnce")
        assertTrue(body.contains("delay(IP_INFO_RETRY_DELAY_MS)"))
    }

    @Test
    fun `ExitNodeResolver handles all strategies`() {
        assertTrue(resolverSource.contains("ExitNodeStrategy.DirectHttp"))
        assertTrue(resolverSource.contains("ExitNodeStrategy.ViaSocks"))
        assertTrue(resolverSource.contains("ExitNodeStrategy.LocationOnly"))
        assertTrue(resolverSource.contains("ExitNodeStrategy.ProviderLabel"))
        assertTrue(resolverSource.contains("ExitNodeStrategy.AutoSelected"))
        assertTrue(resolverSource.contains("ExitNodeStrategy.Unavailable"))
        assertTrue(resolverSource.contains("ipInfoProvider.fetchVia("))
    }

    @Test
    fun `ViaSocks failure does not fall back to direct device IP`() {
        val body = resolverSource.substringAfter("is ExitNodeStrategy.ViaSocks ->")
            .substringBefore("is ExitNodeStrategy.LocationOnly")
        assertTrue(body.contains("fetchVia("), "ViaSocks must call fetchVia(host, port)")
        assertFalse(
            body.contains("ipInfoProvider.fetch()"),
            "ViaSocks failure must become Error, not direct fetch with real device IP",
        )
    }

    @Test
    fun `resolver rethrows cancellation and avoids VPN socketFactory binding`() {
        assertTrue(
            resolverSource.contains("if (it is kotlinx.coroutines.CancellationException) throw it") ||
                resolverSource.contains("if (it is CancellationException) throw it"),
        )
        assertFalse(mainSource.contains("fetchViaSocketFactory"))
        assertFalse(resolverSource.contains("fetchViaSocketFactory"))
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
