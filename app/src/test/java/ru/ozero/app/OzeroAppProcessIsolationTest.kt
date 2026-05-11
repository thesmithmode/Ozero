package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroAppProcessIsolationTest {

    @Test
    fun `OzeroApp onCreate грузит am-go только в engine_warp процессе`() {
        val source = locateOzeroApp().readText()
        val onCreate = extractOnCreateBlock(source)

        val amGoCount = countOccurrences(onCreate, "System.loadLibrary(\"am-go\")")
        val gojniCount = countOccurrences(onCreate, "System.loadLibrary(\"gojni\")")

        assertTrue(
            amGoCount == 1,
            "am-go loadLibrary должен встречаться ровно один раз в onCreate, найдено $amGoCount",
        )
        assertTrue(
            gojniCount == 1,
            "gojni loadLibrary должен встречаться ровно один раз в onCreate, найдено $gojniCount",
        )

        val amGoPos = onCreate.indexOf("System.loadLibrary(\"am-go\")")
        val gojniPos = onCreate.indexOf("System.loadLibrary(\"gojni\")")
        val warpGuardPos = onCreate.indexOf("isEngineWarpProcess()")
        val returnPos = onCreate.indexOf("return", startIndex = warpGuardPos)

        assertTrue(warpGuardPos in 0 until amGoPos, "am-go должна быть ВНУТРИ isEngineWarpProcess()-ветки")
        assertTrue(returnPos in (amGoPos + 1) until gojniPos, "после am-go обязан return — иначе guard бесполезен")
        assertTrue(gojniPos > returnPos, "gojni должна быть ПОСЛЕ return из engine_warp-ветки (т.е. в main-процессе)")
    }

    @Test
    fun `OzeroApp не грузит am-go безусловно вне engine_warp guard`() {
        val source = locateOzeroApp().readText()
        val withoutComments = source.lines()
            .filterNot { it.trim().startsWith("//") }
            .joinToString("\n")
        val onCreate = extractOnCreateBlock(withoutComments)

        val unguardedPattern = Regex(
            """super\.onCreate\(\)\s*\n\s*runCatching\s*\{\s*System\.loadLibrary\("am-go"\)""",
        )
        assertTrue(
            !unguardedPattern.containsMatchIn(onCreate),
            "am-go НЕ должен грузиться сразу после super.onCreate() без guard — это вернёт SIGABRT (v0.0.12)",
        )
    }

    private fun extractOnCreateBlock(source: String): String {
        val start = source.indexOf("override fun onCreate()")
        check(start >= 0) { "override fun onCreate() не найден в OzeroApp.kt" }
        var depth = 0
        var i = source.indexOf('{', start)
        val open = i
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open, i + 1)
                }
            }
            i++
        }
        error("закрывающая } для onCreate не найдена")
    }

    private fun countOccurrences(text: String, needle: String): Int {
        var count = 0
        var i = 0
        while (true) {
            val pos = text.indexOf(needle, i)
            if (pos < 0) break
            count++
            i = pos + needle.length
        }
        return count
    }

    private fun locateOzeroApp(): File {
        val repoRoot = locateRepoRoot()
        val file = File(repoRoot, "app/src/main/java/ru/ozero/app/OzeroApp.kt")
        check(file.isFile) { "OzeroApp.kt не найден по пути ${file.absolutePath}" }
        return file
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
