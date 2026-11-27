package ru.ozero.enginegfvpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** One in-flight notification and at most one pending refresh per SDK session. */
internal class GfVpnNetworkChanges(
    scope: CoroutineScope,
    refresh: suspend () -> Unit,
) : AutoCloseable {
    private val pending = Channel<Unit>(Channel.CONFLATED)
    private val worker = scope.launch {
        for (event in pending) {
            refresh()
        }
    }

    fun offer(): Boolean = pending.trySend(Unit).isSuccess

    override fun close() {
        pending.cancel()
        worker.cancel()
    }
}
