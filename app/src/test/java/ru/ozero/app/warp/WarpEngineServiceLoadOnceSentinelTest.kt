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
    fun `WarpEngineService держится foreground только в рамках WARP session`() {
        assertTrue(
            source.contains("ACTION_START_SESSION") &&
                source.contains("startForeground(") &&
                source.contains("FOREGROUND_SERVICE_TYPE_MANIFEST") &&
                !source.contains("START_STICKY"),
            "WARP engine process обязан быть foreground/started во время active session, иначе " +
                ":engine_warp может быть выгружен в фоне. Service не должен быть sticky, " +
                "иначе Android restart с null intent даст orphan notification без реального Go handle.",
        )
        assertTrue(
            source.contains("WarpEngineServiceActions.START_SESSION") &&
                source.contains("WarpEngineServiceActions.STOP_SESSION"),
            "WarpEngineService обязан брать action strings из shared constants, иначе " +
                "RemoteAwgRuntime и service могут незаметно разъехаться.",
        )
        assertTrue(
            source.contains("ACTION_STOP_SESSION") &&
                source.contains("stopForeground(STOP_FOREGROUND_REMOVE)") &&
                source.contains("START_NOT_STICKY"),
            "Manual OFF обязан гасить foreground WARP session, иначе :engine_warp останется жить " +
                "после выключения VPN.",
        )

        assertTrue(
            source.contains("null -> {") &&
                source.contains("stopSelf(startId)") &&
                source.substringAfter("null -> {").substringBefore("else ->").contains("START_NOT_STICKY"),
            "Restart с null intent обязан завершать service без foreground notification: " +
                "native handles умерли вместе со старым :engine_warp process, reattach делает main process.",
        )
    }
}
