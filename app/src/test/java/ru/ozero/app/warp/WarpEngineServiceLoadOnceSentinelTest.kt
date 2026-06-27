package ru.ozero.app.warp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WarpEngineServiceLoadOnceSentinelTest {

    private val source by lazy {
        val f = File(
            System.getProperty("user.dir") ?: ".",
            "src/main/java/ru/ozero/app/warp/WarpEngineService.kt",
        )
        assertTrue(f.exists(), "WarpEngineService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `ensureLibraryLoaded не держит double-checked synchronized — System loadLibrary сам идемпотентен`() {
        val body = source.substringAfter("private fun ensureLibraryLoaded()")
            .substringBefore("private companion object")
        assertFalse(
            body.contains("synchronized("),
            "synchronized(this) в ensureLibraryLoaded дублирует JVM internal lock — System.loadLibrary " +
                "по контракту thread-safe + идемпотентен. Доп. лок = шум, риск ABA. Body:\n$body",
        )
        assertFalse(
            body.contains("libraryLoaded"),
            "libraryLoaded флаг лишний — eager-load в OzeroApp.onCreate (engine_warp guard) " +
                "гарантирует загрузку до первого вызова. System.loadLibrary повторно = no-op. Body:\n$body",
        )
    }

    @Test
    fun `ensureLibraryLoaded всё ещё дергает System loadLibrary как defensive fallback`() {
        val body = source.substringAfter("private fun ensureLibraryLoaded()")
            .substringBefore("private companion object")
        assertTrue(
            body.contains("System.loadLibrary(\"am-go\")"),
            "ensureLibraryLoaded удалить нельзя — defensive fallback на случай regression OzeroApp guard. " +
                "Native call безопасен. Body:\n$body",
        )
    }

    @Test
    fun `WarpEngineService foreground использует основной VPN notification id`() {
        assertTrue(
            source.contains("startForegroundSession()") &&
                source.contains("OzeroNotificationFactory(this, OzeroVpnService::class.java).enterForeground(this)"),
            "WARP engine process обязан оставаться foreground в фоне, но через основной VPN notification id, " +
                "чтобы не показывать второе уведомление.",
        )
        assertFalse(
            source.contains("Ozero WARP") || source.contains("ozero_warp_engine") || source.contains("7302"),
            "WarpEngineService не должен иметь отдельный title/channel/id для второго WARP notification.",
        )
        assertTrue(
            source.contains("STOP_FOREGROUND_DETACH"),
            "Stop WARP foreground не должен удалять основной VPN notification с traffic stats.",
        )
    }
}
