package ru.ozero.app.relay

import android.os.ParcelFileDescriptor
import ru.ozero.enginescore.PersistentLoggers

object AndroidDummyPipeFactory : DummyPipeFactory {
    override fun create(): DummyPipeFactory.PipeHandle {
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        runCatching {
            val jfd = readEnd.fileDescriptor
            val flags = android.system.Os.fcntlInt(jfd, android.system.OsConstants.F_GETFL, 0)
            android.system.Os.fcntlInt(
                jfd,
                android.system.OsConstants.F_SETFL,
                flags or android.system.OsConstants.O_NONBLOCK,
            )
        }.onFailure { PersistentLoggers.warn("MeshSession", "pipe non-blocking threw: ${it.message}") }
        return DummyPipeFactory.PipeHandle(readEnd.detachFd(), writeEnd)
    }
}
