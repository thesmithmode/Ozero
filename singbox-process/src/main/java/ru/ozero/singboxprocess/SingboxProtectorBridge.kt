package ru.ozero.singboxprocess

import ru.ozero.enginesingbox.ISingboxProtector
import ru.ozero.singboxcore.Protector

internal class SingboxProtectorBridge(
    private val aidlProtector: ISingboxProtector,
) : Protector {
    override fun protect(fd: Int): Boolean = runCatching { aidlProtector.protect(fd) }.getOrDefault(false)
}
