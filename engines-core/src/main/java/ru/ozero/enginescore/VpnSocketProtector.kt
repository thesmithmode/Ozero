package ru.ozero.enginescore

fun interface VpnSocketProtector {
    fun protect(socketFd: Int): Boolean
}

object VpnSocketProtectorHolder : VpnSocketProtector {
    @Volatile
    private var current: VpnSocketProtector? = null

    fun bind(protector: VpnSocketProtector) {
        current = protector
    }

    fun unbind() {
        current = null
    }

    override fun protect(socketFd: Int): Boolean = current?.protect(socketFd) ?: false
}
