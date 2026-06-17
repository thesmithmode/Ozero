package ru.ozero.app.ui.servers

import org.junit.jupiter.api.Test
import ru.ozero.corestorage.entity.ServerEntity
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServersUiStateTest {

    @Test
    fun `content resolves entry and exit only when ids exist`() {
        val state = ServersUiState.Content(
            servers = listOf(server("entry"), server("exit")),
            entryId = "entry",
            exitId = "missing",
        )

        assertEquals("entry", state.entry?.id)
        assertNull(state.exit)
        assertFalse(state.canSave)
    }

    @Test
    fun `content cannot save when either side is absent or identical`() {
        assertFalse(content(entryId = null, exitId = "exit").canSave)
        assertFalse(content(entryId = "entry", exitId = null).canSave)
        assertFalse(content(entryId = "entry", exitId = "entry").canSave)
        assertTrue(content(entryId = "entry", exitId = "exit").canSave)
    }

    private fun content(entryId: String?, exitId: String?): ServersUiState.Content =
        ServersUiState.Content(
            servers = listOf(server("entry"), server("exit")),
            entryId = entryId,
            exitId = exitId,
        )

    private fun server(id: String): ServerEntity =
        ServerEntity(
            id = id,
            country = "DE",
            role = "test",
            protocol = "vless",
            uri = "vless://$id",
            port = 443,
        )
}
