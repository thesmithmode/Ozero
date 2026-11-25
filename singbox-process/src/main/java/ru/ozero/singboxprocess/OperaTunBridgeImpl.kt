package ru.ozero.singboxprocess

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class OperaTunBridgeImpl(
    private val context: Context,
    private val startRpcTimeoutMs: Long = START_RPC_TIMEOUT_MS,
    private val abandonedCleanupTimeoutMs: Long = STOP_TIMEOUT_MS,
    private val readinessRpcTimeoutMs: Long = READINESS_RPC_TIMEOUT_MS,
) : OperaTunBridge {
    private data class Session(
        val connection: ServiceConnection,
        val remote: ISingboxEngineProcess,
        val owner: Long,
        val unbound: AtomicBoolean = AtomicBoolean(false),
        val readiness: AtomicReference<CompletableDeferred<Boolean>?> = AtomicReference(null),
    )

    private class PendingStart(val session: Session) {
        val ready = CompletableDeferred<Boolean>()
        val accepted = CompletableDeferred<Boolean>()
        val finished = CompletableDeferred<Unit>()
    }

    private val mutex = Mutex()
    private var session: Session? = null
    private val pendingStart = AtomicReference<PendingStart?>(null)
    private val protector = object : ISingboxProtector.Stub() {
        override fun protect(socket: ParcelFileDescriptor): Boolean =
            socket.use { VpnSocketProtectorHolder.protect(it.fd) }
    }

    override suspend fun start(tunFd: Int, config: String): Boolean = mutex.withLock {
        if (pendingStart.get() != null) return@withLock false
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
        var attempt: PendingStart? = null
        try {
            bound = context.bindService(
                Intent(context, OperaTunService::class.java),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
            if (!bound) return@withLock false
            val connected = withTimeoutOrNull(BIND_TIMEOUT_MS) { remoteResult.await() } ?: return@withLock false
            val current = Session(connection, connected, owner)
            val startup = PendingStart(current)
            val duplicate = ParcelFileDescriptor.fromFd(tunFd)
            attempt = startup
            pendingStart.set(startup)
            launchNativeStart(startup, duplicate, config)
            val ready = withContext(Dispatchers.IO) {
                withTimeoutOrNull(startRpcTimeoutMs) { startup.ready.await() } == true
            }
            if (!ready) return@withLock false
            session = current
            startup.accepted.complete(true)
            pendingStart.compareAndSet(startup, null)
            true
        } finally {
            if (session == null && bound) {
                withContext(NonCancellable + Dispatchers.IO) {
                    val startup = attempt
                    if (startup == null) {
                        unbind(connection)
                    } else {
                        startup.accepted.complete(false)
                        val finished = withTimeoutOrNull(abandonedCleanupTimeoutMs) { startup.finished.await() }
                        if (finished == null) unbindOnce(startup.session)
                    }
                }
            }
        }
    }

    private fun launchNativeStart(attempt: PendingStart, duplicate: ParcelFileDescriptor, config: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val current = attempt.session
            try {
                val started = runCatching {
                    duplicate.use { current.remote.startWithConfig(current.owner, it, config, protector) }
                }.onFailure {
                    PersistentLoggers.warn("OperaTunBridge", "TUN start failed: ${it.javaClass.simpleName}")
                }.isSuccess
                val ready = started && !attempt.accepted.isCompleted && queryRuntime(current)
                attempt.ready.complete(ready)
                if (!attempt.accepted.await()) release(current)
            } finally {
                pendingStart.compareAndSet(attempt, null)
                attempt.finished.complete(Unit)
            }
        }
    }

    override suspend fun isRunning(): Boolean = mutex.withLock {
        val current = session ?: return@withLock false
        withContext(Dispatchers.IO) { queryRuntime(current) }
    }

    private suspend fun queryRuntime(current: Session): Boolean {
        val pending = CompletableDeferred<Boolean>()
        val query = if (current.readiness.compareAndSet(null, pending)) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    pending.complete(runCatching { current.remote.runtimeRunning() }.getOrDefault(false))
                } finally {
                    current.readiness.compareAndSet(pending, null)
                }
            }
            pending
        } else {
            current.readiness.get() ?: return false
        }
        return withTimeoutOrNull(readinessRpcTimeoutMs) { query.await() } == true
    }

    override suspend fun stop() = mutex.withLock { stopLocked() }

    private suspend fun stopLocked() = withContext(NonCancellable + Dispatchers.IO) {
        val current = session
        session = null
        if (current != null) release(current)
    }

    private fun release(current: Session) {
        try {
            if (!current.remote.stopAndWait(current.owner, STOP_TIMEOUT_MS)) {
                PersistentLoggers.warn("OperaTunBridge", "TUN stop was not acknowledged")
            }
        } catch (e: Exception) {
            PersistentLoggers.warn("OperaTunBridge", "TUN process unavailable: ${e.javaClass.simpleName}")
        } finally {
            unbindOnce(current)
        }
    }

    private fun unbindOnce(current: Session) {
        if (current.unbound.compareAndSet(false, true)) unbind(current.connection)
    }

    private fun unbind(connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
            .onFailure { PersistentLoggers.warn("OperaTunBridge", "unbind failed: ${it.javaClass.simpleName}") }
    }

    private companion object {
        const val BIND_TIMEOUT_MS = 5_000L
        const val START_RPC_TIMEOUT_MS = 18_000L
        const val READINESS_RPC_TIMEOUT_MS = 2_000L
        const val STOP_TIMEOUT_MS = 3_000L
    }
}
