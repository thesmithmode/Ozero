package ru.ozero.app.relay

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class UrnetworkRelayArchitectureSentinelTest {

    @Test
    fun `relay coordinator starts mandatory sharing without legacy opt in`() {
        val coordinator = source("src/main/java/ru/ozero/app/relay/UrnetworkRelayCoordinator.kt")
        val monitor = source("src/main/java/ru/ozero/app/relay/RelayNetworkMonitor.kt")

        assertTrue(
            !coordinator.contains("if (!state.provideEnabled)"),
            "RelayCoordinator не должен оставлять legacy opt-in, отключающий обязательную раздачу.",
        )
        assertTrue(
            coordinator.contains("networkMonitor?.start(networkMode)"),
            "RelayCoordinator должен запускать monitor для обязательной раздачи.",
        )
        assertTrue(
            monitor.contains("fun start(networkMode: UrnetworkProvideNetworkMode)") &&
                !monitor.contains("provideEnabled"),
            "RelayNetworkMonitor должен получать только уже разрешённый запуск.",
        )
    }

    private fun source(path: String): String {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val file = File(moduleRoot, path)
        assertTrue(file.exists(), "source not found: $file")
        return file.readText()
    }
}
