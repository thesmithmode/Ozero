package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class ProguardBroadKeepSentinelTest {

    private val broadKeepPattern = Regex(
        """-keep\s+class\s+ru\.ozero\.\w+\.\*\*\s*\{\s*\*;\s*}""",
    )

    private val allowedBroadKeeps = setOf(
        "ru.ozero.singboxfmt.**",
    )

    @Test
    fun `proguard-rules не содержит broad wildcard keep для ru ozero модулей`() {
        val repoRoot = locateRepoRoot()
        val proguardFile = File(repoRoot, "app/proguard-rules.pro")
        assertTrue(proguardFile.isFile, "app/proguard-rules.pro must exist")

        val offenders = mutableListOf<String>()
        proguardFile.readLines().forEachIndexed { idx, line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#") || trimmed.isEmpty()) return@forEachIndexed
            if (broadKeepPattern.containsMatchIn(trimmed)) {
                val isAllowed = allowedBroadKeeps.any { trimmed.contains(it) }
                if (!isAllowed) {
                    offenders.add("line ${idx + 1}: $trimmed")
                }
            }
        }
        assertTrue(
            offenders.isEmpty(),
            "Broad wildcard keep-правила убивают R8 обфускацию. " +
                "Используй точечные keep или consumer-rules.pro в модуле. " +
                "Нарушители:\n${offenders.joinToString("\n")}",
        )
    }

    @Test
    fun `proguard-rules содержит обфускационный словарь`() {
        val repoRoot = locateRepoRoot()
        val content = File(repoRoot, "app/proguard-rules.pro").readText()
        assertTrue(
            content.contains("-obfuscationdictionary") &&
                content.contains("-classobfuscationdictionary") &&
                content.contains("-packageobfuscationdictionary"),
            "proguard-rules.pro должен содержать директивы обфускационного словаря",
        )
    }

    @Test
    fun `обфускационный словарь существует и не пуст`() {
        val repoRoot = locateRepoRoot()
        val dictFile = File(repoRoot, "app/obfuscation-dict.txt")
        assertTrue(dictFile.isFile, "app/obfuscation-dict.txt must exist")
        val lines = dictFile.readLines().filter { it.isNotBlank() }
        assertTrue(
            lines.size >= 50,
            "Словарь должен содержать ≥50 записей, найдено: ${lines.size}",
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
