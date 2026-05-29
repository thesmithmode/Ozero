package ru.ozero.app.relay

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkRelayArchitectureSentinelTest {

    @Test
    fun `network monitor cannot unpause relay when sharing disabled`() {
        val coordinator = source("src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt")
        val monitor = source("src/main/java/ru/ozero/app/relay/RelayNetworkMonitor.kt")

        assertTrue(
            coordinator.contains("networkMonitor?.start(networkMode, provideEnabled)"),
            "RelayCoordinator обязан передавать provideEnabled в RelayNetworkMonitor, иначе network callback " +
                "может снова включить provide при выключенной раздаче.",
        )
        assertTrue(
            monitor.contains("fun start(networkMode: UrnetworkProvideNetworkMode, provideEnabled: Boolean)"),
            "RelayNetworkMonitor обязан принимать provideEnabled как часть lifecycle boundary.",
        )
        assertTrue(
            monitor.contains("if (!provideEnabled)") &&
                monitor.contains("bridge.setProvidePaused(true)") &&
                monitor.contains("return"),
            "RelayNetworkMonitor обязан оставить providePaused=true и не регистрировать network callback, " +
                "когда раздача отключена пользователем.",
        )
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
