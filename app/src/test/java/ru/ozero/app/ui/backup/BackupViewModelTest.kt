package ru.ozero.app.ui.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import ru.ozero.corebackup.AppBackupData
import ru.ozero.corebackup.AppBackupManager
import ru.ozero.corebackup.AppBackupSerializer
import ru.ozero.corebackup.BackupCategory
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BackupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var manager: AppBackupManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        manager = mockk(relaxed = true)
        mockkObject(AppBackupSerializer)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(AppBackupSerializer)
    }

    @Test
    fun `export success writes encrypted payload and updates state`() = runTest(dispatcher) {
        val output = java.io.ByteArrayOutputStream()
        val context = contextWithOutput(output)
        val uri = backupUri()
        val data = sampleBackupData()

        coEvery { manager.export(setOf(BackupCategory.GENERAL_SETTINGS)) } returns data
        every { AppBackupSerializer.serializeEncrypted(data) } returns byteArrayOf(1, 2, 3, 4)

        val vm = BackupViewModel(manager)
        vm.export(context, uri, setOf(BackupCategory.GENERAL_SETTINGS))
        advanceUntilIdle()

        assertIs<BackupUiState.ExportSuccess>(vm.uiState.value)
        assertTrue(output.size() > 0)
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), output.toByteArray().toList())
    }

    @Test
    fun `export failure surfaces error and leaves state readable`() = runTest(dispatcher) {
        val context = contextWithOutput(java.io.ByteArrayOutputStream())
        val uri = backupUri()

        coEvery { manager.export(any()) } throws IllegalStateException("boom")

        val vm = BackupViewModel(manager)
        vm.export(context, uri, BackupCategory.ALL)
        advanceUntilIdle()

        val state = assertIs<BackupUiState.Error>(vm.uiState.value)
        assertTrue(state.message.contains("boom"))
    }

    @Test
    fun `beginImport success sets pending import and confirmImport completes`() = runTest(dispatcher) {
        val context = contextWithInput(byteArrayOf(9, 8, 7))
        val data = sampleBackupData()

        every { AppBackupSerializer.deserializeAuto(byteArrayOf(9, 8, 7)) } returns data

        val vm = BackupViewModel(manager)
        vm.beginImport(context, backupUri())
        advanceUntilIdle()

        val pending = assertIs<BackupUiState.PendingImport>(vm.uiState.value)
        assertTrue(pending.availableCategories.isNotEmpty())

        coEvery { manager.import(data, any()) } returns Unit

        vm.confirmImport(setOf(BackupCategory.GENERAL_SETTINGS))
        advanceUntilIdle()

        assertIs<BackupUiState.ImportSuccess>(vm.uiState.value)
    }

    @Test
    fun `beginImport failure sets error and cancelImport resets to idle`() = runTest(dispatcher) {
        val context = contextWithInput(byteArrayOf(1))
        every { AppBackupSerializer.deserializeAuto(byteArrayOf(1)) } throws IllegalArgumentException("bad")

        val vm = BackupViewModel(manager)
        vm.beginImport(context, backupUri())
        advanceUntilIdle()

        val error = assertIs<BackupUiState.Error>(vm.uiState.value)
        assertTrue(error.message.contains("bad"))

        vm.cancelImport()
        assertIs<BackupUiState.Idle>(vm.uiState.value)

        vm.dismissResult()
        assertIs<BackupUiState.Idle>(vm.uiState.value)
    }

    @Test
    fun `confirmImport without pending data does nothing`() = runTest(dispatcher) {
        val vm = BackupViewModel(manager)
        vm.confirmImport(setOf(BackupCategory.GENERAL_SETTINGS))
        advanceUntilIdle()

        assertIs<BackupUiState.Idle>(vm.uiState.value)
    }

    @Test
    fun `export failure when output stream missing surfaces unknown error`() = runTest(dispatcher) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(any()) } returns null
        val context = mockk<Context> {
            every { contentResolver } returns resolver
        }

        val vm = BackupViewModel(manager)
        vm.export(context, backupUri(), BackupCategory.ALL)
        advanceUntilIdle()

        val state = assertIs<BackupUiState.Error>(vm.uiState.value)
        assertTrue(state.message.contains("Cannot open output stream"))
    }

    @Test
    fun `beginImport failure when input stream missing surfaces unknown error`() = runTest(dispatcher) {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } returns null
        val context = mockk<Context> {
            every { contentResolver } returns resolver
        }

        val vm = BackupViewModel(manager)
        vm.beginImport(context, backupUri())
        advanceUntilIdle()

        val state = assertIs<BackupUiState.Error>(vm.uiState.value)
        assertTrue(state.message.contains("Cannot open input stream"))
    }

    @Test
    fun `confirmImport failure clears pending and exposes error`() = runTest(dispatcher) {
        val context = contextWithInput(byteArrayOf(9, 8, 7))
        val data = sampleBackupData()

        every { AppBackupSerializer.deserializeAuto(byteArrayOf(9, 8, 7)) } returns data
        coEvery { manager.import(data, any()) } throws IllegalStateException("import boom")

        val vm = BackupViewModel(manager)
        vm.beginImport(context, backupUri())
        advanceUntilIdle()

        vm.confirmImport(setOf(BackupCategory.GENERAL_SETTINGS))
        advanceUntilIdle()

        val state = assertIs<BackupUiState.Error>(vm.uiState.value)
        assertTrue(state.message.contains("import boom"))
        vm.cancelImport()
        assertIs<BackupUiState.Idle>(vm.uiState.value)
    }

    @Test
    fun `dismissResult resets error and success states to idle`() = runTest(dispatcher) {
        val context = contextWithOutput(java.io.ByteArrayOutputStream())
        val uri = backupUri()

        coEvery { manager.export(any()) } throws IllegalStateException("boom")

        val vm = BackupViewModel(manager)
        vm.export(context, uri, BackupCategory.ALL)
        advanceUntilIdle()
        assertIs<BackupUiState.Error>(vm.uiState.value)

        vm.dismissResult()
        assertIs<BackupUiState.Idle>(vm.uiState.value)
    }

    private fun contextWithOutput(output: java.io.OutputStream): Context {
        val resolver = mockk<ContentResolver>()
        every { resolver.openOutputStream(any()) } returns output
        return mockk {
            every { contentResolver } returns resolver
        }
    }

    private fun backupUri(): Uri = mockk(relaxed = true)

    private fun contextWithInput(bytes: ByteArray): Context {
        val resolver = mockk<ContentResolver>()
        every { resolver.openInputStream(any()) } returns bytes.inputStream()
        return mockk {
            every { contentResolver } returns resolver
        }
    }

    private fun sampleBackupData(): AppBackupData = AppBackupData(
        exportedAt = "2026-06-07T00:00:00Z",
        settings = ru.ozero.corebackup.BackupSettings(
            splitMode = "auto",
            ipv6Enabled = true,
            autoStart = false,
            manualEngine = "warp",
            bydpiWinningArgs = "--ok",
            urnetworkEnabled = true,
            urnetworkJwt = "jwt",
            customDnsServers = "1.1.1.1",
            hostsMode = "block",
            hostsList = "a.example",
            uiLocaleTag = "ru",
            appMode = "vpn",
            engineAutoPriority = "true",
            trafficMode = "tunnel",
            bydpiUseUiMode = true,
            bydpiUiSettingsJson = "{}",
            bydpiDefaultAccepted = true,
            urnetworkCountryCode = "NL",
            fptnToken = "token",
        ),
        urnetwork = ru.ozero.corebackup.BackupUrnetwork(byJwt = "jwta"),
        warpSlots = emptyList(),
        splitRules = emptyList(),
    )
}
