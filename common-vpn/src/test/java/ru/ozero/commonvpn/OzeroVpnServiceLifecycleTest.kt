package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

class OzeroVpnServiceLifecycleTest {

    private val source by lazy {
        val moduleRoot = File(System.getProperty("user.dir") ?: ".")
        val f = File(moduleRoot, "src/main/java/ru/ozero/commonvpn/OzeroVpnService.kt")
        assertTrue(f.exists(), "OzeroVpnService.kt не найден: $f")
        f.readText()
    }

    @Test
    fun `onCreate переопределён и вызывает super`() {
        assertTrue(
            source.contains("override fun onCreate()"),
            "OzeroVpnService должен переопределять onCreate() для диагностики Hilt инжекта.",
        )
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private val")
        assertTrue(
            onCreateBody.contains("super.onCreate()"),
            "onCreate обязан вызывать super.onCreate() — иначе Hilt не запустит инжект.",
        )
    }

    @Test
    fun `onCreate логирует before и after super через PersistentLoggers`() {
        val onCreateBody = source.substringAfter("override fun onCreate()").substringBefore("\n    private val")
        assertTrue(
            onCreateBody.contains("onCreate before super"),
            "onCreate должен логировать момент ДО super.onCreate() для диагностики падений Hilt.",
        )
        assertTrue(
            onCreateBody.contains("onCreate after super"),
            "onCreate должен логировать момент ПОСЛЕ super.onCreate() (Hilt инжект завершён).",
        )
        val beforeIdx = onCreateBody.indexOf("before super")
        val superIdx = onCreateBody.indexOf("super.onCreate()")
        val afterIdx = onCreateBody.indexOf("after super")
        assertTrue(
            beforeIdx in 0 until superIdx && superIdx < afterIdx,
            "Порядок логов в onCreate должен быть: before super → super.onCreate() → after super.",
        )
    }

    @Test
    fun `onStartCommand имеет entry-log первой строкой`() {
        val onStartBody = source.substringAfter("override fun onStartCommand")
            .substringBefore("private fun startVpn()")
        val entryIdx = onStartBody.indexOf("onStartCommand entry action=")
        val legacyIdx = onStartBody.indexOf("Log.i(TAG, \"onStartCommand action=")
        assertTrue(
            entryIdx >= 0,
            "onStartCommand должен начинаться с entry-лога через PersistentLoggers " +
                "для диагностики проблем доставки intent.",
        )
        assertTrue(
            legacyIdx > entryIdx,
            "Entry-лог обязан стоять ПЕРЕД остальной логикой и старым Log.i(TAG, ...).",
        )
    }

    @Test
    fun `onStartCommand guard на неинжекченный pipeline возвращает START_NOT_STICKY`() {
        val onStartBody = source.substringAfter("override fun onStartCommand")
            .substringBefore("private fun startVpn()")
        assertTrue(
            onStartBody.contains("::pipeline.isInitialized"),
            "onStartCommand должен проверять `::pipeline.isInitialized` — иначе при сбое " +
                "Hilt graph crash будет неинформативным UninitializedPropertyAccessException.",
        )
        val guardBlock = onStartBody.substringAfter("::pipeline.isInitialized").substringBefore("when (intent?.action)")
        assertTrue(
            guardBlock.contains("stopSelf(startId)"),
            "Guard на pipeline обязан вызвать stopSelf(startId) — освободить service slot.",
        )
        assertTrue(
            guardBlock.contains("START_NOT_STICKY"),
            "Guard на pipeline обязан вернуть START_NOT_STICKY — нет смысла рестартовать сломанный graph.",
        )
    }
}
