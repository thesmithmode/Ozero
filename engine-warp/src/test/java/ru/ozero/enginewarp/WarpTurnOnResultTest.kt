package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarpTurnOnResultTest {

    @Test
    fun `parcel contract keeps null sockets and zero describeContents`() {
        val result = WarpTurnOnResult(handle = 7, socketV4 = null, socketV6 = null)

        assertEquals(7, result.handle)
        assertNull(result.socketV4)
        assertNull(result.socketV6)
        assertEquals(0, result.describeContents())

        val source = readSource()
        assertTrue(source.contains("parcel.writeInt(if (socketV4 != null) 1 else 0)"))
        assertTrue(source.contains("parcel.writeInt(if (socketV6 != null) 1 else 0)"))
    }

    @Test
    fun `parcel contract keeps creator array size and explicit fd restore path`() {
        assertEquals(3, WarpTurnOnResult.CREATOR.newArray(3).size)

        val source = readSource()
        assertTrue(source.contains("ParcelFileDescriptor.CREATOR.createFromParcel(parcel)"))
    }

    private fun readSource(): String {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            val candidate = File(dir, "engine-warp/src/main/java/ru/ozero/enginewarp/WarpTurnOnResult.kt")
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile ?: return@repeat
        }
        error("WarpTurnOnResult source not found")
    }
}
