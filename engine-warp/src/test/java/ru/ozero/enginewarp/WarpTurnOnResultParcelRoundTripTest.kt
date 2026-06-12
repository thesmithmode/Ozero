package ru.ozero.enginewarp

import android.os.Parcel
import android.os.ParcelFileDescriptor
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WarpTurnOnResultParcelRoundTripTest {

    @Test
    fun `parcel roundtrip preserves null sockets`() {
        val restored = roundTrip(WarpTurnOnResult(handle = 7, socketV4 = null, socketV6 = null))

        assertEquals(7, restored.handle)
        assertNull(restored.socketV4)
        assertNull(restored.socketV6)
        assertEquals(0, restored.describeContents())
    }

    @Test
    fun `parcel roundtrip preserves socket presence and newArray size`() {
        val v4 = ParcelFileDescriptor.createPipe()
        val v6 = ParcelFileDescriptor.createPipe()
        try {
            val original = WarpTurnOnResult(handle = 9, socketV4 = v4[0], socketV6 = v6[0])

            val restored = roundTrip(original)

            assertEquals(9, restored.handle)
            assertEquals(1, restored.describeContents())
            assertEquals(3, WarpTurnOnResult.CREATOR.newArray(3).size)
            runCatching { restored.socketV4?.close() }
            runCatching { restored.socketV6?.close() }
        } finally {
            v4.forEach { runCatching { it.close() } }
            v6.forEach { runCatching { it.close() } }
        }
    }

    private fun roundTrip(result: WarpTurnOnResult): WarpTurnOnResult {
        val parcel = Parcel.obtain()
        return try {
            result.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            WarpTurnOnResult.CREATOR.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
