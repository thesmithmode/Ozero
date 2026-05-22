package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class AmGoLoadCallSiteSentinelTest {

    private val whitelist = setOf(
        "app/src/main/java/ru/ozero/app/OzeroApp.kt",
        "app/src/main/java/ru/ozero/app/warp/WarpEngineService.kt",
    )

    private val moduleRoots = listOf(
        "app/src/main",
        "engine-byedpi/src/main",
        "engine-urnetwork/src/main",
    )

    @Test
    fun `libam-go загружается только в whitelist путях — другие движки не имеют права`() {
        val repoRoot = locateRepoRoot()
        val offenders = mutableListOf<String>()
        val pattern = Regex("""System\.loadLibrary\(\s*"am-go"\s*\)""")
        for (root in moduleRoots) {
            val dir = File(repoRoot, root)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    val rel = f.absolutePath
                        .removePrefix(repoRoot.absolutePath)
                        .trimStart('\\', '/')
                        .replace('\\', '/')
                    if (rel in whitelist) return@forEach
                    if (pattern.containsMatchIn(f.readText())) {
                        offenders.add(rel)
                    }
                }
        }
        assertTrue(
            offenders.isEmpty(),
            "libam-go (Go runtime AmneziaWG) грузить вне engine-warp процесса = SIGABRT v0.0.12. " +
                "Whitelist: $whitelist. Найдены лишние call-sites:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `ReLinker am-go fallback нигде не вызывается — System loadLibrary только`() {
        val repoRoot = locateRepoRoot()
        val offenders = mutableListOf<String>()
        val pattern = Regex("""ReLinker[^\n]*"am-go"""")
        for (root in moduleRoots + listOf("engine-warp/src/main")) {
            val dir = File(repoRoot, root)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { f ->
                    if (pattern.containsMatchIn(f.readText())) {
                        offenders.add(
                            f.absolutePath
                                .removePrefix(repoRoot.absolutePath)
                                .trimStart('\\', '/')
                                .replace('\\', '/'),
                        )
                    }
                }
        }
        assertTrue(
            offenders.isEmpty(),
            "ReLinker для am-go запрещён — Go runtime требует eager System.loadLibrary в нужном " +
                "процессе. ReLinker async/wrapper-load = race с JNI_OnLoad. Offenders:\n${offenders.joinToString("\n")}",
        )
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root (settings.gradle.kts) не найден")
    }
}
