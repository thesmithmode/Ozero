package ru.ozero.commonvpn

import ru.ozero.enginescore.PersistentLoggers

class TunnelSocksRoute internal constructor(val port: Int) {
    private val lock = Any()
    private var valid = true
    private val cancellations = mutableSetOf<() -> Unit>()

    fun register(cancel: () -> Unit): Boolean = synchronized(lock) {
        if (!valid) return@synchronized false
        cancellations.add(cancel)
        true
    }

    fun unregister(cancel: () -> Unit) {
        synchronized(lock) { cancellations.remove(cancel) }
    }

    internal fun invalidate() {
        val callbacks = synchronized(lock) {
            valid = false
            cancellations.toList().also { cancellations.clear() }
        }
        callbacks.forEach { cancel ->
            runCatching { cancel() }
                .onFailure { PersistentLoggers.warn("TunnelSocksRoute", "cancel failed: ${it.javaClass.simpleName}") }
        }
    }
}
