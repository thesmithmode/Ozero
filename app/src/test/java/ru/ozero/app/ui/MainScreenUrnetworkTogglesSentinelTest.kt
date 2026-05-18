package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainScreenUrnetworkTogglesSentinelTest {

    private val source: String by lazy { locateMainScreen().readText() }

    @Test
    fun `isUrnetworkVisibleInMain true для manualEngine=URNETWORK независимо от tunnelState`() {
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Idle, EngineId.URNETWORK))
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Idle, EngineId.URNETWORK))
    }

    @Test
    fun `isUrnetworkVisibleInMain false для Idle без manualEngine`() {
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, null))
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, EngineId.BYEDPI))
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, EngineId.WARP))
    }

    @Test
    fun `isUrnetworkVisibleInMain true для Connected URnetwork без manualEngine`() {
        assertTrue(
            isUrnetworkVisibleInMain(
                TunnelState.Connected(EngineId.URNETWORK, socksPort = 0),
                manualEngine = null,
            ),
        )
    }

    @Test
    fun `isUrnetworkVisibleInMain false для Connected другого движка без manualEngine`() {
        assertFalse(
            isUrnetworkVisibleInMain(
                TunnelState.Connected(EngineId.BYEDPI, socksPort = 1080),
                manualEngine = null,
            ),
        )
        assertFalse(
            isUrnetworkVisibleInMain(
                TunnelState.Connected(EngineId.WARP, socksPort = 0),
                manualEngine = null,
            ),
        )
    }

    @Test
    fun `isUrnetworkVisibleInMain true для Connecting URnetwork`() {
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Connecting(EngineId.URNETWORK), null))
    }

    @Test
    fun `isUrnetworkVisibleInMain true для Probing URnetwork`() {
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Probing(EngineId.URNETWORK), null))
    }

    @Test
    fun `isUrnetworkVisibleInMain true для Probing null engineId с manualEngine URnetwork`() {
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Probing(engineId = null), EngineId.URNETWORK))
    }

    @Test
    fun `isUrnetworkVisibleInMain false для Disconnecting без manualEngine`() {
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Disconnecting, null))
    }

    @Test
    fun `MainScreen вызывает UrnetworkMainToggleSection хотя бы дважды — Simple+Expert`() {
        val occurrences = Regex("""UrnetworkMainToggleSection\s*\(""").findAll(source).count()
        assertTrue(
            occurrences >= 2,
            "MainScreen должен рендерить UrnetworkMainToggleSection в обоих режимах (Simple+Expert) — " +
                "найдено $occurrences вызовов",
        )
    }

    @Test
    fun `MainScreen render UrnetworkMainToggleSection защищён isUrnetworkVisibleInMain`() {
        val regex = Regex(
            """if\s*\(isUrnetworkVisibleInMain\([^)]+\)\)\s*\{\s*UrnetworkMainToggleSection""",
        )
        assertTrue(
            regex.containsMatchIn(source),
            "UrnetworkMainToggleSection обязан рендериться внутри if (isUrnetworkVisibleInMain(...)) — " +
                "иначе toggle виден для других движков",
        )
    }

    @Test
    fun `UrnetworkMainToggleSection использует main_toggle_fixed_ip и main_toggle_enhanced_anonymization строки`() {
        val body = extractFunBody(source, "UrnetworkMainToggleSection")
        assertTrue(
            body.contains("R.string.main_toggle_fixed_ip"),
            "UrnetworkMainToggleSection должен использовать main_toggle_fixed_ip string resource",
        )
        assertTrue(
            body.contains("R.string.main_toggle_enhanced_anonymization"),
            "UrnetworkMainToggleSection должен использовать main_toggle_enhanced_anonymization string resource",
        )
    }

    @Test
    fun `UrnetworkMainToggleSection помечен testTag URNETWORK_TOGGLES`() {
        val body = extractFunBody(source, "UrnetworkMainToggleSection")
        assertTrue(
            body.contains("URNETWORK_TOGGLES"),
            "UrnetworkMainToggleSection обязан помечать корневой Column testTag URNETWORK_TOGGLES",
        )
    }

    @Test
    fun `MainScreenTestTags содержит URNETWORK_TOGGLE константы`() {
        assertEquals("main_urnetwork_toggles", MainScreenTestTags.URNETWORK_TOGGLES)
        assertEquals("main_urnetwork_toggle_fixed_ip", MainScreenTestTags.URNETWORK_TOGGLE_FIXED_IP)
        assertEquals("main_urnetwork_toggle_anonymization", MainScreenTestTags.URNETWORK_TOGGLE_ANONYMIZATION)
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
