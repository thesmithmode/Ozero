package ru.ozero.app.ui.logs

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.ozero.app.logging.LogFileStore
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogEntry
import ru.ozero.app.logging.LogLevel
import ru.ozero.app.logging.LogcatReader
import ru.ozero.app.logging.UnifiedLogger
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var buffer: LogBuffer
    private lateinit var reader: LogcatReader
    private lateinit var vm: LogsViewModel

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        LogFileStore.resetForTest()
        buffer = LogBuffer()
        reader = LogcatReader(buffer)
        vm = LogsViewModel(buffer, reader).also { it.ioContext = dispatcher }
    }

    @AfterEach
    fun tearDown() {
        vm.closeForTest()
        LogFileStore.resetForTest()
        Dispatchers.resetMain()
        reader.shutdown()
    }

    @Test
    fun `initial refresh = 0`() {
        assertEquals(0L, vm.refresh.value)
    }

    @Test
    fun `clear() обновляет refresh timestamp до большего значения`() = runTest(dispatcher) {
        val before = vm.refresh.value
        vm.clear()
        advanceUntilIdle()
        assertTrue(
            vm.refresh.value > before,
            "clear() обязан увеличивать refresh — UI наблюдает StateFlow для триггера re-poll, " +
                "без обновления tab Logs не покажет очищенное состояние",
        )
    }

    @Test
    fun `clear() очищает LogBuffer`() = runTest(dispatcher) {
        buffer.append(
            ru.ozero.app.logging.LogEntry(
                timestampMs = 0L,
                level = ru.ozero.app.logging.LogLevel.INFO,
                tag = "T",
                pid = 0,
                message = "test",
            ),
        )
        assertEquals(1, buffer.size())
        vm.clear()
        advanceUntilIdle()
        assertEquals(
            0,
            buffer.size(),
            "clear() обязан очистить in-memory ring buffer — иначе старые logs остаются после Clear",
        )
    }

    @Test
    fun `copyAll() возвращает строку (UnifiedLogger без init = empty)`() {
        val result = vm.copyAll()
        assertEquals(
            "",
            result,
            "Без UnifiedLogger.init() targetRef=null → read() возвращает '' — " +
                "тест в isolation проверяет что copyAll не падает на null target",
        )
    }

    @Test
    fun `LogsUiState builds sorted available tags with all first`() {
        val state = LogsUiState(
            entries = listOf(
                entry(tag = "Z"),
                entry(tag = "A"),
                entry(tag = "Z"),
            )
        )

        assertEquals(listOf(FILTER_ALL, "A", "Z"), state.availableTags)
    }

    @Test
    fun `LogsUiState filters by tag`() {
        val state = LogsUiState(
            entries = listOf(entry(tag = "A"), entry(tag = "B")),
            tagFilter = "B",
        )

        assertEquals(listOf("B"), state.filteredEntries.map { it.tag })
    }

    @Test
    fun `LogsUiState filters by minimum level severity`() {
        val state = LogsUiState(
            entries = listOf(
                entry(level = LogLevel.INFO),
                entry(level = LogLevel.WARN),
                entry(level = LogLevel.ERROR),
            ),
            levelFilter = LogLevel.WARN.name,
        )

        assertEquals(listOf(LogLevel.WARN, LogLevel.ERROR), state.filteredEntries.map { it.level })
    }

    @Test
    fun `LogsUiState unknown level does not hide entries`() {
        val state = LogsUiState(
            entries = listOf(entry(level = LogLevel.DEBUG), entry(level = LogLevel.ERROR)),
            levelFilter = "UNKNOWN",
        )

        assertEquals(2, state.filteredEntries.size)
    }

    @Test
    fun `LogsUiState filter all keeps all entries`() {
        val state = LogsUiState(
            entries = listOf(entry(tag = "A"), entry(tag = "B")),
        )

        assertEquals(2, state.filteredEntries.size)
    }

    @Test
    fun `onTagFilter updates ui state tag filter`() = runTest(dispatcher) {
        val job = backgroundScope.launch(dispatcher) { vm.uiState.collect { } }

        vm.onTagFilter("App")
        advanceUntilIdle()

        assertEquals("App", vm.uiState.value.tagFilter)
        assertEquals(FILTER_ALL, vm.uiState.value.levelFilter)
        job.cancel()
    }

    @Test
    fun `onLevelFilter updates ui state level filter`() = runTest(dispatcher) {
        val job = backgroundScope.launch(dispatcher) { vm.uiState.collect { } }

        vm.onLevelFilter(LogLevel.ERROR.name)
        advanceUntilIdle()

        assertEquals(LogLevel.ERROR.name, vm.uiState.value.levelFilter)
        assertEquals(FILTER_ALL, vm.uiState.value.tagFilter)
        job.cancel()
    }

    @Test
    fun `copyFiltered returns entries at or above selected level`() {
        initUnifiedLogger()
        UnifiedLogger.writeRawSync(logLine("INFO", "App", "visible info"))
        UnifiedLogger.writeRawSync(logLine("WARN", "App", "visible warn"))
        UnifiedLogger.writeRawSync(logLine("ERROR", "App", "visible error"))

        val result = vm.copyFiltered(LogLevel.WARN)

        assertTrue(!result.contains("visible info"))
        assertTrue(result.contains("visible warn"))
        assertTrue(result.contains("visible error"))
    }

    @Test
    fun `copyFiltered returns empty string when nothing matches`() {
        initUnifiedLogger()
        UnifiedLogger.writeRawSync(logLine("INFO", "App", "info only"))

        val result = vm.copyFiltered(LogLevel.ERROR)

        assertEquals("", result)
    }

    @Test
    fun `createFilteredFile writes filtered log file when matching entries exist`() = runTest(dispatcher) {
        initUnifiedLogger()
        UnifiedLogger.writeRawSync(logLine("DEBUG", "App", "debug line"))
        UnifiedLogger.writeRawSync(logLine("ERROR", "App", "error line"))
        var ready: File? = null

        vm.createFilteredFile(LogLevel.ERROR) { ready = it }
        advanceUntilIdle()

        val out = assertNotNull(ready)
        assertEquals("ozero_error.log", out.name)
        assertTrue(out.readText().contains("error line"))
        assertTrue(!out.readText().contains("debug line"))
    }

    @Test
    fun `createFilteredFile returns null when no entries match`() = runTest(dispatcher) {
        initUnifiedLogger()
        UnifiedLogger.writeRawSync(logLine("INFO", "App", "info only"))
        var ready: File? = File(tempDir, "sentinel")

        vm.createFilteredFile(LogLevel.ERROR) { ready = it }
        advanceUntilIdle()

        assertNull(ready)
    }

    @Test
    fun `createFilteredFile returns null when logger file is absent`() = runTest(dispatcher) {
        var ready: File? = File(tempDir, "sentinel")

        vm.createFilteredFile(LogLevel.ERROR) { ready = it }
        advanceUntilIdle()

        assertNull(ready)
    }

    @Test
    fun `uiState reflects logger file path and size`() = runTest(dispatcher) {
        initUnifiedLogger()
        UnifiedLogger.writeRawSync(logLine("ERROR", "App", "line"))
        val job = backgroundScope.launch(dispatcher) { vm.uiState.collect { } }

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.fileSize > 0)
        assertTrue(state.filePath.isNotBlank())
        job.cancel()
    }

    private fun entry(
        tag: String = "T",
        level: LogLevel = LogLevel.INFO,
    ) = LogEntry(
        timestampMs = 0L,
        level = level,
        tag = tag,
        pid = 1,
        message = "m",
    )

    private fun initUnifiedLogger() {
        val context = mockk<Context>()
        every { context.filesDir } returns tempDir
        UnifiedLogger.init(context)
        UnifiedLogger.clear()
    }

    private fun logLine(
        level: String,
        tag: String,
        message: String,
    ): String = "2026-06-07 12:00:00.000 $level [test] $tag: $message"
}
