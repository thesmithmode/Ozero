package binaries

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class JacocoExcludesContractTest {

    private val jacocoScript by lazy {
        val file = File("src/main/kotlin/ozero.jacoco.gradle.kts")
        assertThat(file.exists()).isTrue()
        file.readText()
    }

    @Test
    fun `coverage excludes do not hide broad testable production layers`() {
        listOf(
            "\"**/*Service*.*\"",
            "\"**/*Runtime*.*\"",
            "\"**/*Bridge*.*\"",
            "\"**/*Proxy*.*\"",
            "\"**/*Gateway*.*\"",
            "\"**/*Native*.*\"",
            "\"**/*Api*.*\"",
            "\"**/*Task*.*\"",
        ).forEach { mask ->
            assertThat(jacocoScript)
                .withFailMessage("$mask hides testable production code from coverage.")
                .doesNotContain(mask)
        }
    }

    @Test
    fun `coverage excludes do not hide deterministic business logic`() {
        listOf(
            "StartSequenceCoordinator",
            "ShutdownCoordinator",
            "EngineWatchdogCoordinator",
            "RuntimeFailureRouter",
            "EngineSettingsRestartObserver",
            "EngineRuntimeConfigRestartObserver",
            "ConfigBuilder",
            "RawShareLinksParser",
            "V2RayFmt",
            "V2RayFmtUtils",
            "UriCompat",
            "Ed25519Verifier",
            "SubscriptionVerifier",
            "LogSanitizer",
            "PersistentLoggers",
            "WarpConfParser",
            "WarpIniBuilder",
            "AwgParams",
            "BytesFormatter",
            "TunnelController",
            "HealthMonitor",
            "NativeHevTunnelGateway",
            "DownloadBinaryTask",
        ).forEach { className ->
            assertThat(jacocoScript)
                .withFailMessage("$className is deterministic production logic and must stay in coverage gate.")
                .doesNotContain(className)
        }
    }
}
