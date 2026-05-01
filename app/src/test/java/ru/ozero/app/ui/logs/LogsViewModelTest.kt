package ru.ozero.app.ui.logs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.app.logging.LogBuffer
import ru.ozero.app.logging.LogcatReader
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LogsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var buffer: LogBuffer
    private lateinit var reader: LogcatReader
    private lateinit var vm: LogsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        buffer = LogBuffer()
        reader = LogcatReader(buffer)
        vm = LogsViewModel(buffer, reader).also { it.ioContext = dispatcher }
    }

    @AfterEach
    fun tearDown() {
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
}
