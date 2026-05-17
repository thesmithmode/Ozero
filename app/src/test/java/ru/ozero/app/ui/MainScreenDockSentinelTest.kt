package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainScreenDockSentinelTest {

    private val source: String by lazy { locateMainScreen().readText() }

    @Test
    fun `simpleDockTabs не содержит DOCK_TAB_SERVERS — Параметры скрыты в простом режиме`() {
        val body = extractFunBody(source, "simpleDockTabs")
        assertFalse(
            body.contains("DOCK_TAB_SERVERS"),
            "simpleDockTabs не должна включать DOCK_TAB_SERVERS (вкладка Параметры) — " +
                "она должна быть скрыта в простом режиме",
        )
    }

    @Test
    fun `simpleDockTabs содержит DOCK_TAB_SPLIT_TUNNEL`() {
        val body = extractFunBody(source, "simpleDockTabs")
        assertTrue(
            body.contains("DOCK_TAB_SPLIT_TUNNEL"),
            "simpleDockTabs обязана содержать DOCK_TAB_SPLIT_TUNNEL — Split Tunneling должен быть в простом режиме",
        )
    }

    @Test
    fun `expertDockTabs содержит DOCK_TAB_SERVERS и DOCK_TAB_SPLIT_TUNNEL`() {
        val body = extractFunBody(source, "expertDockTabs")
        assertTrue(
            body.contains("DOCK_TAB_SERVERS"),
            "expertDockTabs обязана содержать DOCK_TAB_SERVERS (вкладка Параметры) — в экспертном режиме она видна",
        )
        assertTrue(
            body.contains("DOCK_TAB_SPLIT_TUNNEL"),
            "expertDockTabs обязана содержать DOCK_TAB_SPLIT_TUNNEL — Split Tunneling должен быть в экспертном режиме",
        )
    }

    @Test
    fun `SimpleMainContent использует simpleDockTabs а не expertDockTabs`() {
        val body = extractFunBody(source, "SimpleMainContent")
        assertTrue(
            body.contains("simpleDockTabs()"),
            "SimpleMainContent обязана вызывать simpleDockTabs() — без вкладки Параметры",
        )
        assertFalse(
            body.contains("expertDockTabs()"),
            "SimpleMainContent не должна вызывать expertDockTabs()",
        )
    }

    @Test
    fun `ExpertMainContent использует expertDockTabs а не simpleDockTabs`() {
        val body = extractFunBody(source, "ExpertMainContent")
        assertTrue(
            body.contains("expertDockTabs()"),
            "ExpertMainContent обязана вызывать expertDockTabs() — с вкладкой Параметры и Split Tunnel",
        )
        assertFalse(
            body.contains("simpleDockTabs()"),
            "ExpertMainContent не должна вызывать simpleDockTabs()",
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
        check(file.isFile) { "MainScreen.kt не найден: ${file.absolutePath}" }
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
