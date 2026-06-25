package ru.ozero.app.relay

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkRelayArchitectureSentinelTest {

    @Test
    fun `relay coordinator always starts monitor with enabled sharing`() {
        val coordinator = source("src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt")
        val monitor = source("src/main/java/ru/ozero/app/relay/RelayNetworkMonitor.kt")

        assertTrue(
            coordinator.contains("networkMonitor?.start(networkMode)"),
            "RelayCoordinator обязан запускать RelayNetworkMonitor без legacy provideEnabled state.",
        )
        assertTrue(
            coordinator.contains("relayLockManager?.acquire()") && !coordinator.contains("if (provideEnabled)"),
            "RelayCoordinator обязан брать relay lock без legacy provideEnabled branch.",
        )
        assertTrue(
            monitor.contains("fun start(networkMode: UrnetworkProvideNetworkMode)") &&
                !monitor.contains("provideEnabled"),
            "RelayNetworkMonitor не должен принимать legacy provideEnabled state.",
        )
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
