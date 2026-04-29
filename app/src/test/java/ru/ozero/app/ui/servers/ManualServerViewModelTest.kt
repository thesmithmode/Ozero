package ru.ozero.app.ui.servers

import io.mockk.coEvery
import io.mockk.mockk
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
import ru.ozero.app.subscription.ServerImportService
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalCoroutinesApi::class)
class ManualServerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var importer: ServerImportService
    private lateinit var vm: ManualServerViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        importer = mockk(relaxed = false)
        vm = ManualServerViewModel(importer)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state Idle с пустым URI`() {
        val state = vm.uiState.value
        assertIs<ManualServerUiState.Idle>(state)
        assertEquals("", state.uri)
    }

    @Test
    fun `onUriChange обновляет URI в Idle`() {
        vm.onUriChange("vless://abc@host:443")
        val state = vm.uiState.value
        assertIs<ManualServerUiState.Idle>(state)
        assertEquals("vless://abc@host:443", state.uri)
    }

    @Test
    fun `onAdd с пустым URI → Error «пустой URI»`() = runTest {
        vm.onAdd()
        val state = vm.uiState.value
        assertIs<ManualServerUiState.Error>(state)
        assertEquals("пустой URI", state.reason)
    }

    @Test
    fun `onAdd с валидным URI → Success(protocol)`() = runTest {
        val entity = ServerEntity(
            id = "id-1",
            country = "RU",
            role = "single",
            protocol = "vless",
            uri = "vless://abc@host:443",
            port = 443,
        )
        coEvery { importer.import(any()) } returns ServerImportService.ImportResult.Ok(entity)

        vm.onUriChange("vless://abc@host:443")
        vm.onAdd()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertIs<ManualServerUiState.Success>(state)
        assertEquals("vless", state.protocol)
    }

    @Test
    fun `onAdd при ошибке импорта → Error(reason)`() = runTest {
        coEvery { importer.import(any()) } returns ServerImportService.ImportResult.Error("invalid URI")

        vm.onUriChange("garbage://x")
        vm.onAdd()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertIs<ManualServerUiState.Error>(state)
        assertEquals("invalid URI", state.reason)
    }

    @Test
    fun `onDismissResult сбрасывает в Idle`() = runTest {
        coEvery { importer.import(any()) } returns ServerImportService.ImportResult.Error("x")
        vm.onUriChange("v")
        vm.onAdd()
        advanceUntilIdle()
        assertIs<ManualServerUiState.Error>(vm.uiState.value)

        vm.onDismissResult()
        val state = vm.uiState.value
        assertIs<ManualServerUiState.Idle>(state)
        assertEquals("", state.uri)
    }
}
