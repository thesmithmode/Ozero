package ru.ozero.app.ui

import org.junit.jupiter.api.Test
import ru.ozero.commonvpn.TunnelState
import ru.ozero.enginescore.EngineId
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainScreenUrnetworkTogglesSentinelTest {

    private val mainScreenSource: String by lazy { locateFile(MAIN_SCREEN).readText() }
    private val engineSettingsSource: String by lazy { locateFile(ENGINE_SETTINGS).readText() }
    private val stringsSource: String by lazy { locateFile(STRINGS_RU).readText() }

    @Test
    fun `isUrnetworkVisibleInMain true для manualEngine=URNETWORK независимо от tunnelState`() {
        assertTrue(isUrnetworkVisibleInMain(TunnelState.Idle, manualEngine = EngineId.URNETWORK))
    }

    @Test
    fun `isUrnetworkVisibleInMain false для Idle без manualEngine`() {
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, manualEngine = null))
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, manualEngine = EngineId.BYEDPI))
        assertFalse(isUrnetworkVisibleInMain(TunnelState.Idle, manualEngine = EngineId.WARP))
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
    }

    @Test
    fun `MainScreen НЕ содержит UrnetworkMainToggleSection`() {
        val occurrences = Regex("""UrnetworkMainToggleSection""").findAll(mainScreenSource).count()
        assertTrue(
            occurrences == 0,
            "MainScreen не должен рендерить отдельную секцию тумблеров — тумблеры живут в engine settings",
        )
    }

    @Test
    fun `MainScreen НЕ ссылается на main_toggle_fixed_ip или main_toggle_enhanced_anonymization`() {
        assertFalse(
            mainScreenSource.contains("main_toggle_fixed_ip"),
            "MainScreen не должен использовать main_toggle_fixed_ip — string удалён",
        )
        assertFalse(
            mainScreenSource.contains("main_toggle_enhanced_anonymization"),
            "MainScreen не должен использовать main_toggle_enhanced_anonymization — string удалён",
        )
    }

    @Test
    fun `strings xml НЕ содержит main_toggle_ строк`() {
        assertFalse(
            stringsSource.contains("\"main_toggle_fixed_ip\""),
            "main_toggle_fixed_ip удалён из strings.xml",
        )
        assertFalse(
            stringsSource.contains("\"main_toggle_enhanced_anonymization\""),
            "main_toggle_enhanced_anonymization удалён из strings.xml",
        )
    }

    @Test
    fun `IpInfoCard принимает urnetworkPeerCount параметр`() {
        assertTrue(
            mainScreenSource.contains("urnetworkPeerCount: Int? = null"),
            "IpInfoCard обязан принимать urnetworkPeerCount — peer count встроен в выходной узел",
        )
        assertTrue(
            mainScreenSource.contains("urnetworkSearchSeconds: Int? = null"),
            "IpInfoCard обязан принимать urnetworkSearchSeconds — для индикатора поиска",
        )
    }

    @Test
    fun `IpInfoCard разделён на две колонки — выходной узел слева, пиры справа`() {
        assertTrue(
            mainScreenSource.contains("IpCardExitNodeValue("),
            "IpInfoCard обязан рендерить выходной узел через IpCardExitNodeValue в левой колонке",
        )
        assertTrue(
            mainScreenSource.contains("IpCardPeerValue("),
            "IpInfoCard обязан рендерить число пиров через IpCardPeerValue в правой колонке",
        )
        assertTrue(
            mainScreenSource.contains("engine_status_peers_title"),
            "Правая колонка обязана использовать заголовок engine_status_peers_title",
        )
    }

    @Test
    fun `IpInfoCard call-site использует isUrnetworkVisibleInMain — peer column виден и в auto-mode`() {
        val regex = Regex(
            """IpInfoCard\([\s\S]*?urnetworkPeerCount\s*=\s*if\s*\(urnetworkActive\)""",
        )
        assertTrue(
            regex.containsMatchIn(mainScreenSource),
            "IpInfoCard call-site обязан гейтить peer column по isUrnetworkVisibleInMain(tunnelState, manualEngine), " +
                "не по `manualEngine == EngineId.URNETWORK` — auto-mode иначе теряет правую колонку.",
        )
        assertTrue(
            mainScreenSource.contains("val urnetworkActive = isUrnetworkVisibleInMain("),
            "MainScreen обязан вычислять urnetworkActive через isUrnetworkVisibleInMain " +
                "(см. предыдущий тест `isUrnetworkVisibleInMain true для Connected URnetwork без manualEngine`).",
        )
    }

    @Test
    fun `UrnetworkEngineSettingsScreen имеет toggle усиленной анонимизации`() {
        assertTrue(
            engineSettingsSource.contains("urnetwork_enhanced_anonymization"),
            "UrnetworkEngineSettingsScreen обязан использовать строку urnetwork_enhanced_anonymization",
        )
        assertTrue(
            engineSettingsSource.contains("onToggleAllowDirect"),
            "UrnetworkEngineSettingsScreen обязан прокидывать onToggleAllowDirect callback",
        )
    }

    @Test
    fun `MainScreenTestTags НЕ содержит URNETWORK_TOGGLE_ констант`() {
        val tagsSource = locateFile("app/src/main/java/ru/ozero/app/ui/MainScreenTestTags.kt").readText()
        assertFalse(
            tagsSource.contains("URNETWORK_TOGGLE_FIXED_IP"),
            "URNETWORK_TOGGLE_FIXED_IP удалён — тумблер не на main",
        )
        assertFalse(
            tagsSource.contains("URNETWORK_TOGGLE_ANONYMIZATION"),
            "URNETWORK_TOGGLE_ANONYMIZATION удалён — тумблер не на main",
        )
        assertFalse(
            tagsSource.contains("URNETWORK_TOGGLES"),
            "URNETWORK_TOGGLES удалён — секция не на main",
        )
    }

    private fun locateFile(relative: String): File {
        val repoRoot = locateRepoRoot()
        val file = File(repoRoot, relative)
        check(file.isFile) { "$relative не найден: ${file.absolutePath}" }
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

    private companion object {
        const val MAIN_SCREEN = "app/src/main/java/ru/ozero/app/ui/MainScreen.kt"
        const val ENGINE_SETTINGS =
            "app/src/main/java/ru/ozero/app/ui/settings/engines/UrnetworkEngineSettingsScreen.kt"
        const val STRINGS_RU = "app/src/main/res/values/strings.xml"
    }
}
