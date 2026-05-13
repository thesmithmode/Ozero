package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class MainScreenVisualConnectedSentinelTest {

    @Test
    fun `MainScreen использует visualConnected вместо isConnected для контентных блоков`() {
        val source = locateMainScreen().readText()

        assertTrue(
            source.contains("val visualConnected = isConnected || switching != null"),
            "MainScreen.kt обязан содержать `val visualConnected = isConnected || switching != null` — " +
                "блоки контента (IpInfoCard, UrnetworkPeerBadge, stagnant, degraded) обязаны держаться видимыми " +
                "во время switching, иначе layout прыгает при смене движка",
        )

        val blockBranchPattern = Regex("""if\s*\(\s*isConnected\s*\)\s*\{""")

        val expertBlock = extractFunBody(source, "ExpertMainContent")
        val expertRawCount = blockBranchPattern.findAll(expertBlock).count()
        assertTrue(
            expertRawCount == 0,
            "ExpertMainContent не должен использовать `if (isConnected) {` для условного контента — " +
                "должен быть `if (visualConnected) {`. Найдено $expertRawCount сырых isConnected block-branch",
        )

        val simpleBlock = extractFunBody(source, "SimpleMainContent")
        val simpleRawCount = blockBranchPattern.findAll(simpleBlock).count()
        assertTrue(
            simpleRawCount == 0,
            "SimpleMainContent не должен использовать `if (isConnected) {` для условного контента — " +
                "должен быть `if (visualConnected) {`. Найдено $simpleRawCount сырых isConnected block-branch",
        )
    }

    @Test
    fun `backgroundState для URnetwork с 0 peers — Connecting (без желтозелёного наложения)`() {
        val source = locateMainScreen().readText()
        assertTrue(
            source.contains("val backgroundState = powerState.toBackgroundState()"),
            "backgroundState обязан выводиться из powerState.toBackgroundState() — единый источник истины, " +
                "без дублирования логики между powerState и backgroundState",
        )
        assertTrue(
            source.contains("state.engineId == EngineId.URNETWORK && urnetworkPeerCount == 0"),
            "computePowerDiscState обязан обрабатывать случай URnetwork + peers==0 → Connecting, " +
                "иначе жёлтая кнопка отображается поверх зелёного фона при поиске пиров",
        )
    }

    private fun extractFunBody(source: String, funName: String): String {
        val pattern = Regex("""fun\s+$funName\s*\(""")
        val match = pattern.find(source) ?: error("$funName не найдена в MainScreen.kt")
        val start = match.range.first
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
        error("закрывающая } для $funName не найдена")
    }

    private fun locateMainScreen(): File {
        val repoRoot = locateRepoRoot()
        val file = File(repoRoot, "app/src/main/java/ru/ozero/app/ui/MainScreen.kt")
        check(file.isFile) { "MainScreen.kt не найден по пути ${file.absolutePath}" }
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
