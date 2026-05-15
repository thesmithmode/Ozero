package ru.ozero.app.i18n

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class I18nKeyParityTest {

    private val repoRoot by lazy { locateRepoRoot() }

    private val baseline by lazy { keysFor("values") }

    @Test
    fun `en имеет полный parity с ru baseline`() {
        assertParity("values-en")
    }

    @Test
    fun `es имеет полный parity с ru baseline`() {
        assertParity("values-es")
    }

    @Test
    fun `pt имеет полный parity с ru baseline`() {
        assertParity("values-pt")
    }

    @Test
    fun `ru baseline имеет минимум 100 ключей — sanity на работающий regex`() {
        assertTrue(
            baseline.size >= 100,
            "ru baseline keys=${baseline.size} (<100). Regex name=\"...\" не нашёл, " +
                "или strings.xml пустой — тесты parity дают ложное passing.",
        )
    }

    private fun assertParity(localeDir: String) {
        val localeKeys = keysFor(localeDir)
        val missing = (baseline - localeKeys).sorted()
        val extra = (localeKeys - baseline).sorted()
        assertTrue(
            missing.isEmpty() && extra.isEmpty(),
            "$localeDir parity broken vs ru baseline (W9.2 локали обязаны иметь полный parity). " +
                "Missing keys (${missing.size}): ${missing.take(20)}${if (missing.size > 20) "…" else ""}. " +
                "Extra keys (${extra.size}): ${extra.take(20)}${if (extra.size > 20) "…" else ""}. " +
                "Stale локали (ar/de/fr/hi/ja/zh-rCN) намеренно не покрываются (T-30 decision).",
        )
    }

    private fun keysFor(localeDir: String): Set<String> {
        val file = File(repoRoot, "app/src/main/res/$localeDir/strings.xml")
        check(file.isFile) { "strings.xml не найден: ${file.absolutePath}" }
        return Regex("""name="([^"]+)"""")
            .findAll(file.readText(Charsets.UTF_8))
            .map { it.groupValues[1] }
            .toSet()
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
