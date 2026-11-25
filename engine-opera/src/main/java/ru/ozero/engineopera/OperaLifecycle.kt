package ru.ozero.engineopera

import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class OperaLifecycle {
    private val lifecycle = Mutex()
    private val stopGate = Mutex()
    private val lock = Any()
    private var operation: Job? = null
    private var stopping = false

    suspend fun <T> run(rejected: T, block: suspend () -> T): T = coroutineScope {
        val job = coroutineContext.job
        lifecycle.withLock {
            val accepted = synchronized(lock) {
                if (stopping) {
                    false
                } else {
                    operation = job
                    true
                }
            }
            if (!accepted) return@withLock rejected
            try {
                block()
            } finally {
                synchronized(lock) { operation = null }
            }
        }
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
