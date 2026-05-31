package ru.ozero.commonvpn

import ru.ozero.enginescore.EngineId
import java.util.concurrent.atomic.AtomicReference

class RuntimeFailureRouter(
    private val tunnelController: TunnelController,
) {
    private val handlerRef = AtomicReference<((EngineId, String) -> Unit)?>(null)

    fun bind(handler: (EngineId, String) -> Unit) {
        handlerRef.set(handler)
    }

    fun unbind(handler: (EngineId, String) -> Unit) {
        handlerRef.compareAndSet(handler, null)
    }

    fun handleEngineFailure(engineId: EngineId, reason: String) {
        val handler = handlerRef.get()
        if (handler != null) {
            handler(engineId, reason)
        } else {
            tunnelController.onEngineDied(engineId, reason)
        }
    }
}
