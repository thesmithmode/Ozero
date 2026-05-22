package ru.ozero.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroQuickTileSentinelTest {

    @Test
    fun `OzeroQuickTile использует AndroidEntryPoint для Hilt-инжекции`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("@AndroidEntryPoint"),
            "OzeroQuickTile обязан иметь @AndroidEntryPoint — иначе Hilt не сможет инжектировать TunnelController",
        )
    }

    @Test
    fun `OzeroQuickTile инжектирует TunnelController`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("@Inject") && source.contains("TunnelController"),
            "OzeroQuickTile обязан инжектировать TunnelController для отслеживания реального статуса VPN",
        )
    }

    @Test
    fun `syncTileState маппит Connected Connecting Probing в STATE_ACTIVE`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("TunnelState.Connected")
                && source.contains("TunnelState.Connecting")
                && source.contains("TunnelState.Probing")
                && source.contains("Tile.STATE_ACTIVE"),
            "syncTileState обязан маппить Connected/Connecting/Probing в STATE_ACTIVE — " +
                "тайл должен подсвечиваться пока VPN включён или подключается",
        )
    }

    @Test
    fun `syncTileState маппит Idle Failed Disconnecting в STATE_INACTIVE`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("TunnelState.Idle")
                && source.contains("TunnelState.Failed")
                && source.contains("TunnelState.Disconnecting")
                && source.contains("Tile.STATE_INACTIVE"),
            "syncTileState обязан маппить Idle/Failed/Disconnecting в STATE_INACTIVE — " +
                "тайл должен быть серым когда VPN не работает",
        )
    }

    @Test
    fun `OzeroQuickTile отменяет job в onStopListening`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("onStopListening") && source.contains("job?.cancel()"),
            "OzeroQuickTile обязан отменять coroutine job в onStopListening — " +
                "иначе flow продолжает собираться когда тайл не виден",
        )
    }

    @Test
    fun `OzeroQuickTile отменяет scope в onDestroy`() {
        val source = locateTile().readText()
        assertTrue(
            source.contains("onDestroy") && source.contains("scope.cancel()"),
            "OzeroQuickTile обязан отменять scope в onDestroy — утечка coroutine",
        )
    }

    @Test
    fun `manifest использует ozero_logo_white как иконку тайла`() {
        val manifest = File(locateRepoRoot(), "app/src/main/AndroidManifest.xml")
        check(manifest.isFile) { "AndroidManifest.xml не найден по ${manifest.absolutePath}" }
        val content = manifest.readText()
        assertTrue(
            content.contains("ozero_logo_white"),
            "OzeroQuickTile в манифесте обязан использовать @drawable/ozero_logo_white — " +
                "иконка лого приложения, а не системная ic_lock_lock",
        )
    }

    private fun locateTile(): File {
        val file = File(locateRepoRoot(), "app/src/main/java/ru/ozero/app/OzeroQuickTile.kt")
        check(file.isFile) { "OzeroQuickTile.kt не найден по ${file.absolutePath}" }
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
