package ru.ozero.commonvpn

import java.io.File

object TunInterfaceStats {

    private const val PROC_NET_DEV = "/proc/net/dev"

    data class Snapshot(val rxBytes: Long, val txBytes: Long)

    fun parseProcNetDev(content: String, iface: String): Pair<Long, Long>? {
        val prefix = "$iface:"
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trimStart()
            if (!line.startsWith(prefix)) return@forEach
            val tail = line.substring(prefix.length).trim()
            val parts = tail.split(Regex("\\s+"))
            if (parts.size < 16) return null
            val rx = parts[0].toLongOrNull() ?: return null
            val tx = parts[8].toLongOrNull() ?: return null
            return rx to tx
        }
        return null
    }

    fun parseTunInterfaces(content: String): Set<String> {
        val result = mutableSetOf<String>()
        content.lineSequence().forEach { rawLine ->
            val line = rawLine.trimStart()
            val colonIdx = line.indexOf(':')
            if (colonIdx <= 0) return@forEach
            val name = line.substring(0, colonIdx).trim()
            if (name.startsWith("tun")) result += name
        }
        return result
    }

    fun pickNewTunInterface(before: Set<String>, after: Set<String>): String? {
        val diff = after - before
        if (diff.isNotEmpty()) return diff.maxByOrNull { tunSuffixOrZero(it) }
        if (after.isEmpty()) return null
        return after.maxByOrNull { tunSuffixOrZero(it) }
    }

    fun snapshotTunInterfaces(): Set<String> {
        return runCatching { parseTunInterfaces(File(PROC_NET_DEV).readText()) }
            .getOrElse { emptySet() }
    }

    fun readTunStats(iface: String): Snapshot? {
        return readTunStats(iface) { File(PROC_NET_DEV).readText() }
    }

    internal fun readTunStats(iface: String, readProcNetDev: () -> String): Snapshot? {
        val content = runCatching { readProcNetDev() }.getOrNull() ?: return null
        val (rx, tx) = parseProcNetDev(content, iface) ?: return null
        return Snapshot(rxBytes = rx, txBytes = tx)
    }

    private fun tunSuffixOrZero(name: String): Int {
        val s = name.removePrefix("tun")
        return s.toIntOrNull() ?: 0
    }
}
