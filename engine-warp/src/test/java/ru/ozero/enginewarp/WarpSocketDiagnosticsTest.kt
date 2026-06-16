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
        File(tmp, "root.sock").writeText("")

        val result = WarpSocketDiagnostics.listSocketCandidates(tmp.absolutePath)

        assertContains(result, "sockets")
        assertContains(result, "wireguard")
        assertContains(result, "root.sock")
        assertContains(result, "[sockets/]={a.sock}")
        assertContains(result, "[wireguard/]={b.sock}")
    }

    @Test
    fun `listSocketCandidates reports absent nested directories`(@TempDir tmp: File) {
        val result = WarpSocketDiagnostics.listSocketCandidates(tmp.absolutePath)

        assertContains(result, "[sockets/]={absent}")
        assertContains(result, "[wireguard/]={absent}")
    }

    @Test
    fun `listSocketCandidates reports empty when nested listFiles is null`(@TempDir tmp: File) {
        File(tmp, "sockets").writeText("not a directory")
        File(tmp, "wireguard").writeText("not a directory")

        val result = WarpSocketDiagnostics.listSocketCandidates(tmp.absolutePath)

        assertContains(result, "[sockets/]={empty}")
        assertContains(result, "[wireguard/]={empty}")
    }

    @Test
    fun `listSocketCandidates reports failure for unreadable path`() {
        val result = WarpSocketDiagnostics.listSocketCandidates("\u0000")

        assertTrue(result.startsWith("dirListing failed:"))
    }
}
