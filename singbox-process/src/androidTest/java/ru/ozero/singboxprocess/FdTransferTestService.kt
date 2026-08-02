package ru.ozero.singboxprocess

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.system.Os
import ru.ozero.enginesingbox.ISingboxProtector

class FdTransferTestService : Service() {
    private val binder = object : IFdTransferTest.Stub() {
        override fun transfer(protector: ISingboxProtector): ParcelFileDescriptor {
            val (source, peer) = ParcelFileDescriptor.createSocketPair()
            source.use {
                check(SingboxProtectorBridge(protector).protect(it.fd))
                Os.write(it.fileDescriptor, byteArrayOf(AFTER_RECEIVER_CLOSE), 0, 1)
            }
            return peer
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private companion object {
        const val AFTER_RECEIVER_CLOSE = 0x71
    }
}
