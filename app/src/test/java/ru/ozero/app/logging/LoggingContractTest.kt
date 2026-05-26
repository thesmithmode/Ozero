package ru.ozero.app.logging

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoggingContractTest {

    private val whitelist: Set<String> = setOf(
        "app/src/main/java/ru/ozero/app/logging/UnifiedLogger.kt",
        "app/src/main/java/ru/ozero/app/logging/LogcatReader.kt",
        "app/src/main/java/ru/ozero/app/data/CrashLogStore.kt",
        "app/src/main/java/ru/ozero/app/di/SelfUpdateModule.kt",
        "app/src/main/java/ru/ozero/app/selfupdate/ApkDownloader.kt",
        "app/src/main/java/ru/ozero/app/selfupdate/SilentPackageInstaller.kt",
        "app/src/main/java/ru/ozero/app/selfupdate/UpdateCoordinator.kt",
        "app/src/main/java/ru/ozero/app/selfupdate/UpdateInstallEventBus.kt",
        "app/src/main/java/ru/ozero/app/selfupdate/UpdateInstallResultReceiver.kt",
        "app/src/main/java/ru/ozero/app/ui/diag/DiagnosticsTester.kt",
        "app/src/main/java/ru/ozero/app/ui/diag/DiagnosticsViewModel.kt",
        "app/src/main/java/ru/ozero/app/BootReceiver.kt",
        "app/src/main/java/ru/ozero/app/warp/WarpEngineService.kt",
        "engines-core/src/main/java/ru/ozero/enginescore/PersistentLogger.kt",
        "engines-core/src/main/java/ru/ozero/enginescore/probe/Socks5HandshakeProbe.kt",
        "singbox-process/src/main/java/ru/ozero/singboxprocess/SingboxEngineService.kt",
        "singbox-subscription/src/main/java/ru/ozero/singboxsubscription/RawUpdater.kt",
    )

    private val rawLogPattern = Regex("""\bLog\.(e|w|wtf)\(""")

    private val skipDirs = setOf("build", ".git", ".gradle", ".memory", "node_modules", ".claude")

    @Test
    fun `production code uses PersistentLoggers for warn-error, raw android-util-Log only on whitelist`() {
        val repoRoot = locateRepoRoot()
        val mainKt = collectMainKt(repoRoot)
        assertTrue(mainKt.isNotEmpty(), "main-source .kt files не найдены под $repoRoot")

        val violations = mutableListOf<String>()
        for (file in mainKt) {
            val rel = file.relativeTo(repoRoot).path.replace('\\', '/')
            if (rel in whitelist) continue
            val content = file.readText()
            val hits = rawLogPattern.findAll(content).count()
            if (hits > 0) {
                violations += "$rel: $hits raw Log.(e|w|wtf) calls — must use PersistentLoggers"
            }
        }

        assertEquals(
            emptyList<String>(),
            violations,
            "Logging contract violation. Файлы вне whitelist обязаны использовать " +
                "PersistentLoggers.warn/error (UnifiedLogger → boot.log), не raw android.util.Log:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `whitelist entries actually exist on disk`() {
        val repoRoot = locateRepoRoot()
        val missing = whitelist.filterNot { File(repoRoot, it).isFile }
        assertEquals(
            emptyList(),
            missing,
            "Whitelist содержит несуществующие файлы (актуализируй после refactor):\n" +
                missing.joinToString("\n"),
        )
    }

    @Test
    fun `PersistentLoggers facade exists and exposes warn-error-info`() {
        val repoRoot = locateRepoRoot()
        val candidates = listOf(
            "engines-core/src/main/java/ru/ozero/enginescore/PersistentLoggers.kt",
            "engines-core/src/main/java/ru/ozero/enginescore/PersistentLogger.kt",
        )
        val source = candidates.firstNotNullOfOrNull { File(repoRoot, it).takeIf(File::isFile) }
            ?.readText()
            ?: error("PersistentLoggers facade не найден в engines-core")
        assertTrue(source.contains("fun trace"), "PersistentLoggers должен иметь fun trace")
        assertTrue(source.contains("fun debug"), "PersistentLoggers должен иметь fun debug")
        assertTrue(source.contains("fun info"), "PersistentLoggers должен иметь fun info")
        assertTrue(source.contains("fun warn"), "PersistentLoggers должен иметь fun warn")
        assertTrue(source.contains("fun error"), "PersistentLoggers должен иметь fun error")
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root (settings.gradle.kts) не найден от ${System.getProperty("user.dir")}")
    }

    private fun collectMainKt(repoRoot: File): List<File> {
        val result = mutableListOf<File>()
        repoRoot.walkTopDown()
            .onEnter { dir ->
                dir.name !in skipDirs
            }
            .forEach { f ->
                if (!f.isFile || !f.name.endsWith(".kt")) return@forEach
                val path = f.absolutePath.replace('\\', '/')
                if (path.contains("/src/main/")) result += f
            }
        return result
    }
}
