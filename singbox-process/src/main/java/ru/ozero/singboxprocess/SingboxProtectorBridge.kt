package ru.ozero.singboxprocess

import ru.ozero.enginesingbox.ISingboxProtector

internal class SingboxProtectorBridge(
    private val aidlProtector: ISingboxProtector,
) {
    fun protect(fd: Int): Boolean = runCatching { aidlProtector.protect(fd) }.getOrDefault(false)
}
