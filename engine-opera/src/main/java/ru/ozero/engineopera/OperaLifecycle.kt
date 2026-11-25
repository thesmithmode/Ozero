package ru.ozero.engineopera

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class OperaLifecycle {
    private val lifecycle = Mutex()
    private val stopGate = Mutex()
    private val lock = Any()
    private var operation: Job? = null
    private var stopping = false

    suspend fun <T> run(
        rejected: T,
        onCancelled: suspend () -> Unit = {},
        block: suspend () -> T,
    ): T = lifecycle.withLock {
        var accepted = false
        try {
            coroutineScope {
                val job = coroutineContext.job
                accepted = synchronized(lock) {
                    if (stopping) {
                        false
                    } else {
                        operation = job
                        true
                    }
                }
                if (accepted) block() else rejected
            }
        } catch (e: CancellationException) {
            if (accepted) withContext(NonCancellable) { onCancelled() }
            throw e
        } finally {
            synchronized(lock) { operation = null }
        }
    }

    suspend fun <T> attach(rejected: T, block: suspend () -> T): T = lifecycle.withLock {
        if (synchronized(lock) { stopping }) rejected else block()
    }

    suspend fun stop(block: suspend () -> Unit) = stopGate.withLock {
        val current = synchronized(lock) {
            stopping = true
            operation
        }
        try {
            current?.cancel()
            lifecycle.withLock { block() }
        } finally {
            synchronized(lock) { stopping = false }
        }
    }
}
