package ru.ozero.enginescore

import java.net.Socket

fun interface VpnSocketProtector {
    fun protect(socketFd: Int): Boolean
}

object VpnSocketProtectorHolder : VpnSocketProtector {
    private data class Binding(val fd: VpnSocketProtector, val socket: ((Socket) -> Boolean)?)

    @Volatile
    private var current: Binding? = null

    fun bind(protector: VpnSocketProtector) = bind(protector, null)

    @Synchronized
    fun bind(protector: VpnSocketProtector, socketProtector: ((Socket) -> Boolean)?) {
        current = Binding(protector, socketProtector)
    }

    @Synchronized
    fun unbind(protector: VpnSocketProtector) {
        if (current?.fd === protector) current = null
    }

    fun isBound(): Boolean = current != null

    fun protectIfBound(socketFd: Int): Boolean? = current?.fd?.protect(socketFd)

    override fun protect(socketFd: Int): Boolean = protectIfBound(socketFd) ?: false

    fun protectSocket(socket: Socket): Boolean = current?.socket?.invoke(socket) ?: false
}
