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
            "Base58",
            "UrnetworkConfig",
            "UrnetworkContractStatusObserver",
            "UrnetworkDeviceIdentity",
            "UrnetworkPreflight",
            "ChainOrchestrator",
            "Placeholder",
            "UrnetworkLocationSelection",
            "UrnetworkCachedLocation",
            "MasterDnsClientWrapper",
            "MasterDnsClientWrapperContract",
            "MasterDnsPortAllocator",
            "MasterDnsServerDeployer",
            "MasterDnsConfigWriter",
            "MasterDnsResolversCache",
            "SshjMasterDnsDeployer",
            "UrnetworkAuthService",
            "InMemoryUrnetworkConfigStore",
            "AppBackupSerializer",
            "BackupSettingsSerializer",
            "BackupStrategySerializer",
            "BackupWarpSerializer",
            "BackupCategory",
            "BackupWarpSlot",
            "BackupSavedStrategy",
            "BackupJsonExtensionsKt",
            "BackupUrnetworkLocation",
            "RawUpdater",
            "HttpUrlConnectionClient",
        ).forEach { className ->
            assertThat(jacocoScript)
                .withFailMessage("$className is deterministic production logic and must stay in coverage gate.")
                .doesNotContain(className)
        }
    }

    @Test
    fun `coverage excludes do not hide EngineWarp implementation`() {
        assertThat(jacocoScript)
            .withFailMessage("Main EngineWarp implementation must stay in coverage gate.")
            .doesNotContain("\"**/EngineWarp*.*\"")
        assertThat(jacocoScript)
            .withFailMessage("EngineWarp no longer uses a generated-class coverage mask in this revision.")
            .doesNotContain("\"**/EngineWarp\\${'$'}awaitReady\\${'$'}*.*\"")
    }

    @Test
    fun `coverage thresholds stay at project policy minimum`() {
        listOf("0.74", "0.59", "0.75", "0.64", "0.89", "0.68", "0.90", "0.88").forEach { threshold ->
            assertThat(jacocoScript)
                .withFailMessage("coverage threshold $threshold is below the repo minimum policy.")
                .doesNotContain(threshold)
        }
        assertThat(jacocoScript).contains("0.95")
    }
}
