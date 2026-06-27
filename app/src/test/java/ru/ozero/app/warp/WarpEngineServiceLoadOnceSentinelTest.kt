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
    fun `WarpEngineService не поднимает отдельный foreground notification`() {
        assertFalse(
            source.contains("startForeground("),
            "WARP engine process не должен показывать второе уведомление рядом с основным VPN notification.",
        )
        assertFalse(
            source.contains("Notification.Builder") || source.contains("NotificationCompat.Builder"),
            "WarpEngineService не должен строить собственное уведомление: UI/traffic notification принадлежит OzeroVpnService.",
        )
        assertTrue(
            source.contains("onStartCommand") &&
                source.contains("stopSelf(startId)") &&
                source.contains("START_NOT_STICKY"),
            "Если system всё же стартанёт service командой, он обязан завершиться без orphan notification.",
        )
    }
}
