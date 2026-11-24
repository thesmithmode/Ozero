package ru.ozero.singboxprocess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.engineopera.OperaTunBridge
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtectorHolder
import ru.ozero.enginesingbox.ISingboxEngineProcess
import ru.ozero.enginesingbox.ISingboxProtector
import java.util.UUID

class OperaTunBridgeImpl(private val context: Context) : OperaTunBridge {
    private data class Session(
        val connection: ServiceConnection,
        val remote: ISingboxEngineProcess,
        val owner: Long,
    )

    private val mutex = Mutex()
    private var session: Session? = null
    private val protector = object : ISingboxProtector.Stub() {
        override fun protect(socket: ParcelFileDescriptor): Boolean =
            socket.use { VpnSocketProtectorHolder.protect(it.fd) }
    }

    override suspend fun start(tunFd: Int, config: String): Boolean = mutex.withLock {
        stopLocked()
        val remoteResult = CompletableDeferred<ISingboxEngineProcess?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                remoteResult.complete(ISingboxEngineProcess.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName) {
                remoteResult.complete(null)
            }

            override fun onBindingDied(name: ComponentName) {
                remoteResult.complete(null)
            }

            override fun onNullBinding(name: ComponentName) {
                remoteResult.complete(null)
            }
        }
        val owner = UUID.randomUUID().mostSignificantBits
        var bound = false
        var remote: ISingboxEngineProcess? = null
        try {
            bound = context.bindService(
                Intent(context, OperaTunService::class.java),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
            if (!bound) return@withLock false
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) { remoteResult.await() } ?: return@withLock false
            remote = connected
            val ready = withContext(Dispatchers.IO) {
                ParcelFileDescriptor.fromFd(tunFd).use {
                    connected.startWithConfig(owner, it, config, protector)
                }
                connected.runtimeRunning()
            }
            if (!ready) return@withLock false
            session = Session(connection, connected, owner)
            true
        } finally {
            if (session == null && bound) {
                withContext(NonCancellable + Dispatchers.IO) {
                    release(connection, remote, owner)
                }
            }
        }
    }

    override suspend fun isRunning(): Boolean = mutex.withLock {
        val current = session ?: return@withLock false
        withContext(Dispatchers.IO) {
            runCatching { current.remote.runtimeRunning() }.getOrDefault(false)
        }
    }

    override suspend fun stop() = mutex.withLock { stopLocked() }

    private suspend fun stopLocked() = withContext(NonCancellable + Dispatchers.IO) {
        val current = session
        session = null
        if (current != null) release(current.connection, current.remote, current.owner)
    }

    private fun release(connection: ServiceConnection, remote: ISingboxEngineProcess?, owner: Long) {
        try {
            if (remote != null && !remote.stopAndWait(owner, STOP_TIMEOUT_MS)) {
                PersistentLoggers.warn("OperaTunBridge", "TUN stop was not acknowledged")
            }
        } finally {
            runCatching { context.unbindService(connection) }
                .onFailure { PersistentLoggers.warn("OperaTunBridge", "unbind failed: ${it.javaClass.simpleName}") }
        }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 5_000L
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
