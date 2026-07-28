package ru.ozero.enginescore

fun interface VpnSocketProtector {
    fun protect(socketFd: Int): Boolean
}

object VpnSocketProtectorHolder : VpnSocketProtector {
    @Volatile
    private var current: VpnSocketProtector? = null

    @Synchronized
    fun bind(protector: VpnSocketProtector) {
        current = protector
    }

    @Synchronized
    fun unbind(protector: VpnSocketProtector) {
        if (current === protector) current = null
    }

    fun isBound(): Boolean = current != null

    fun protectIfBound(socketFd: Int): Boolean? {
        val protector = current ?: return null
        return protector.protect(socketFd)
    }

    override fun protect(socketFd: Int): Boolean = current?.protect(socketFd) ?: false
}
