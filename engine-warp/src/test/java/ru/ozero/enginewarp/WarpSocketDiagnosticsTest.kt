package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertTrue

class WarpSocketDiagnosticsTest {

    @Test
    fun `listSocketCandidates reports socket directories and absent branches`(@TempDir tmp: File) {
        File(tmp, "sockets").mkdirs()
        File(tmp, "wireguard").mkdirs()
        File(tmp, "sockets/a.sock").writeText("")
        File(tmp, "wireguard/b.sock").writeText("")

        val result = WarpSocketDiagnostics.listSocketCandidates(tmp.absolutePath)

        assertContains(result, "[sockets/]={a.sock}")
        assertContains(result, "[wireguard/]={b.sock}")
    }

    @Test
    fun `listSocketCandidates reports failure for unreadable path`() {
        val result = WarpSocketDiagnostics.listSocketCandidates("\u0000")

        assertTrue(result.startsWith("dirListing failed:"))
    }
}
