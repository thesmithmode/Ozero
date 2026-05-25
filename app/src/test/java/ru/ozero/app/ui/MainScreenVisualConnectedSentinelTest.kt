package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
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
    fun `backgroundState — единый источник истины из powerState`() {
        val source = locateMainScreen().readText()
        assertTrue(
            source.contains("val backgroundState = powerState.toBackgroundState()"),
            "backgroundState обязан выводиться из powerState.toBackgroundState() — единый источник истины, " +
                "без дублирования логики между powerState и backgroundState",
        )
    }

    @Test
    fun `powerDiscState в MainViewModel — Connected плюс degraded даёт Switching (жёлтый)`() {
        val source = locateMainViewModel().readText()
        assertTrue(
            source.contains("s is TunnelState.Connected && degraded -> PowerDiscState.Switching"),
            "MainViewModel.powerDiscState обязан рисовать жёлтую кнопку (Switching) при Connected + degraded — " +
                "это общий паттерн для WARP (UAPI null/handshake stale) и URnetwork (peers==0). " +
                "Зелёная кнопка ⇔ реально трафик идёт. Регрессия 2026-05-20: WARP висел Connected зелёным без трафика.",
        )
        assertTrue(
            source.contains("val powerDiscState"),
            "MainViewModel обязан содержать powerDiscState StateFlow — " +
                "атомарное состояние кнопки, без гонки трёх отдельных StateFlow в UI",
        )
        val screenSource = locateMainScreen().readText()
        assertTrue(
            screenSource.contains("viewModel.powerDiscState"),
            "MainScreen обязан читать viewModel.powerDiscState — " +
                "единый источник истины, вычисленный через combine() в VM",
        )
    }

    @Test
    fun `powerDiscState в MainViewModel — Disconnecting маппится в Off а не в Connecting`() {
        val source = locateMainViewModel().readText()
        val fnStart = source.indexOf("val powerDiscState")
        assertTrue(fnStart >= 0, "powerDiscState не найден в MainViewModel.kt")
        val blockStart = source.indexOf("combine(", fnStart)
        assertTrue(blockStart >= 0, "powerDiscState обязан использовать combine() — Регрессия 2026-05-25")
        val blockEnd = source.indexOf(".stateIn(", blockStart)
        val combineBody = if (blockEnd > blockStart) source.substring(blockStart, blockEnd) else ""
        assertFalse(
            combineBody.contains("TunnelState.Disconnecting"),
            "powerDiscState combine-блок НЕ должен упоминать TunnelState.Disconnecting — " +
                "Disconnecting маппится в Off через else-ветку. " +
                "Disconnecting → Connecting вызывает yellow flash после green при фейле WARP: " +
                "видно оба цвета одновременно (yellow+green overlap). Баг 2026-05-25.",
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

    private fun locateMainViewModel(): File {
        val repoRoot = locateRepoRoot()
        val file = File(repoRoot, "app/src/main/java/ru/ozero/app/ui/MainViewModel.kt")
        check(file.isFile) { "MainViewModel.kt не найден по пути ${file.absolutePath}" }
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
