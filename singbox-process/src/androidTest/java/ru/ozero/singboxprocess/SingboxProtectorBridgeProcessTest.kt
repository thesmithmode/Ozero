package ru.ozero.singboxprocess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import ru.ozero.enginesingbox.ISingboxProtector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SingboxProtectorBridgeProcessTest {
    @Test
    fun `duplicated descriptor crosses process and original remains usable`() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val connected = CountDownLatch(1)
        var transfer: IFdTransferTest? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                transfer = IFdTransferTest.Stub.asInterface(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(
            context.bindService(
                Intent(context, FdTransferTestService::class.java),
                connection,
                Context.BIND_AUTO_CREATE,
            ),
        )
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            val peer = requireNotNull(transfer).transfer(
                object : ISingboxProtector.Stub() {
                    override fun protect(socket: ParcelFileDescriptor): Boolean = socket.use {
                        Os.write(it.fileDescriptor, byteArrayOf(RECEIVER_MARKER), 0, 1)
                        true
                    }
                },
            )
            peer.use {
                val bytes = ByteArray(2)
                assertEquals(2, Os.read(it.fileDescriptor, bytes, 0, bytes.size))
                assertEquals(RECEIVER_MARKER, bytes[0].toInt())
                assertEquals(AFTER_RECEIVER_CLOSE, bytes[1].toInt())
            }
        } finally {
            context.unbindService(connection)
        }
    }

    private companion object {
        const val RECEIVER_MARKER = 0x70
        const val AFTER_RECEIVER_CLOSE = 0x71
    }
}
