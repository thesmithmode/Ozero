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
            "BinaryDownloader",
            "LockFileParser",
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

    @Test
    fun `coverage may exclude generated EngineWarp inner classes but not main implementation`() {
        assertThat(jacocoScript)
            .withFailMessage("Main EngineWarp implementation must stay in coverage gate.")
            .doesNotContain("\"**/EngineWarp*.*\"")
        assertThat(jacocoScript)
            .withFailMessage("Generated coroutine classes may be excluded only by explicit inner-class masks.")
            .contains("\"**/EngineWarp\\${'$'}awaitReady\\${'$'}*.*\"")
    }
}
