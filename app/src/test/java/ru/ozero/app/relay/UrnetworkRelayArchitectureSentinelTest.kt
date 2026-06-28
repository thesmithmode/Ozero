package ru.ozero.app.relay

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkRelayArchitectureSentinelTest {

    @Test
    fun `relay coordinator gates monitor and lock on provide setting`() {
        val coordinator = source("src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt")
        val monitor = source("src/main/java/ru/ozero/app/relay/RelayNetworkMonitor.kt")

        assertTrue(
            coordinator.contains("if (provideConfig.provideEnabled)") &&
                coordinator.contains("networkMonitor?.start(networkMode)"),
            "RelayCoordinator обязан запускать RelayNetworkMonitor только при enabled provide state.",
        )
        assertTrue(
            coordinator.contains("relayLockManager?.acquire()"),
            "RelayCoordinator обязан брать relay lock при enabled provide state.",
        )
        assertTrue(
            monitor.contains("fun start(networkMode: UrnetworkProvideNetworkMode)"),
            "RelayNetworkMonitor должен оставаться ответственным только за network mode runtime pause.",
        )
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
