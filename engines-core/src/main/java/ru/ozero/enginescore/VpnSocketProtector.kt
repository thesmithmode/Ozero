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

    override fun protect(socketFd: Int): Boolean = current?.protect(socketFd) ?: false
}
