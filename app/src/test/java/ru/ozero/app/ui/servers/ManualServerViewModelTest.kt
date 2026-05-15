package ru.ozero.app.ui.servers

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ManualServerViewModelTest {

    private fun vm() = ManualServerViewModel()

    @Test
    fun `initial state is Idle empty uri`() {
        val s = vm().uiState.value
        val idle = assertIs<ManualServerUiState.Idle>(s)
        assertEquals("", idle.uri)
    }

    @Test
    fun `onUriChange updates Idle uri`() {
        val v = vm()
        v.onUriChange("vless://abc")
        val s = assertIs<ManualServerUiState.Idle>(v.uiState.value)
        assertEquals("vless://abc", s.uri)
    }

    @Test
    fun `onUriChange same text no-op`() {
        val v = vm()
        v.onUriChange("x")
        val before = v.uiState.value
        v.onUriChange("x")
        assertEquals(before, v.uiState.value)
    }

    @Test
    fun `onAdd empty uri produces Error EMPTY_URI`() {
        val v = vm()
        v.onAdd()
        val e = assertIs<ManualServerUiState.Error>(v.uiState.value)
        assertEquals(ManualServerUiState.Error.Reason.EMPTY_URI, e.reason)
        assertEquals("", e.uri)
    }

    @Test
    fun `onAdd whitespace uri trimmed and rejected as empty`() {
        val v = vm()
        v.onUriChange("   ")
        v.onAdd()
        val e = assertIs<ManualServerUiState.Error>(v.uiState.value)
        assertEquals(ManualServerUiState.Error.Reason.EMPTY_URI, e.reason)
    }

    @Test
    fun `onAdd valid uri returns IMPORT_UNAVAILABLE Error`() {
        val v = vm()
        v.onUriChange("vless://server.example:443")
        v.onAdd()
        val e = assertIs<ManualServerUiState.Error>(v.uiState.value)
        assertEquals(ManualServerUiState.Error.Reason.IMPORT_UNAVAILABLE, e.reason)
        assertEquals("vless://server.example:443", e.uri)
    }

    @Test
    fun `onUriChange after Error resets to Idle preserving new uri`() {
        val v = vm()
        v.onUriChange("bad")
        v.onAdd()
        assertIs<ManualServerUiState.Error>(v.uiState.value)
        v.onUriChange("good")
        val idle = assertIs<ManualServerUiState.Idle>(v.uiState.value)
        assertEquals("good", idle.uri)
    }

    @Test
    fun `onAdd from Error state retries with same uri`() {
        val v = vm()
        v.onUriChange("retry-uri")
        v.onAdd()
        val first = assertIs<ManualServerUiState.Error>(v.uiState.value)
        v.onAdd()
        val second = assertIs<ManualServerUiState.Error>(v.uiState.value)
        assertEquals(first.uri, second.uri)
    }

    @Test
    fun `onDismissResult resets to Idle empty`() {
        val v = vm()
        v.onUriChange("x")
        v.onAdd()
        v.onDismissResult()
        val idle = assertIs<ManualServerUiState.Idle>(v.uiState.value)
        assertEquals("", idle.uri)
    }

    @Test
    fun `onAdd from Importing state no-op`() {
        val v = vm()
        v.onAdd()
        v.onUriChange("x")
        v.onAdd()
        val before = v.uiState.value
        v.onAdd()
        assertEquals(before, v.uiState.value)
    }
}
