package ru.ozero.app.ui.logs

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogsViewModelMultiProcessSentinelTest {

    @Test
    fun `LogsViewModel использует fileEntriesFlow а не buffer-entries для отображения`() {
        val source = locateLogsViewModel().readText()
        assertTrue(
            source.contains("fileEntriesFlow()"),
            "LogsViewModel обязан использовать fileEntriesFlow() как источник данных для uiState — " +
                "LogcatReader фильтрует --pid=mainPid, поэтому буфер содержит только главный процесс. " +
                "VPN-движки (:engine_warp, :engine_singbox) пишут через PersistentLoggers в файл. " +
                "Без file-based polling in-app viewer показывает только BatteryGuard/MainActivity (регрессия 2026-05-25).",
        )
        assertFalse(
            source.contains("buffer.entries"),
            "LogsViewModel НЕ должен использовать buffer.entries в combine — " +
                "это источник только главного процесса. Использовать fileEntriesFlow().",
        )
    }

    private fun locateLogsViewModel(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            if (File(dir, "settings.gradle.kts").isFile) {
                return File(dir, "app/src/main/java/ru/ozero/app/ui/logs/LogsViewModel.kt")
            }
            dir = dir.parentFile ?: return@repeat
        }
        error("repo root не найден")
    }
}
