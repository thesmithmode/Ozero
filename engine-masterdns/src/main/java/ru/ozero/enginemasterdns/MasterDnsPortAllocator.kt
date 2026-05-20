package ru.ozero.enginemasterdns

import java.net.ServerSocket

open class MasterDnsPortAllocator(
    private val range: IntRange = 18000..18999,
) {
    open fun allocate(desired: Int): Int {
        if (desired in range && isFree(desired)) return desired
        for (p in range) if (isFree(p)) return p
        error("no free port in $range")
    }

    private fun isFree(port: Int): Boolean = runCatching {
        ServerSocket(port).use { }
    }.isSuccess
}
