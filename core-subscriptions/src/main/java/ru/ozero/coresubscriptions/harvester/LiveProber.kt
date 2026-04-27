package ru.ozero.coresubscriptions.harvester

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import ru.ozero.corestorage.dao.ServerDao
import ru.ozero.corestorage.entity.ServerEntity
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.time.Duration.Companion.seconds

class LiveProber(
    private val serverDao: ServerDao,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun probeAll(servers: List<ServerEntity>): ProbeStats {
        if (servers.isEmpty()) return ProbeStats(0, 0, 0)
                        val sem = Semaphore(MAX_CONCURRENT)
        val results = coroutineScope {
            servers.map { srv ->
                async(Dispatchers.IO) {
                    sem.withPermit {
                        val alive = withTimeoutOrNull(PROBE_TIMEOUT) { probeOne(srv) } ?: false
                        serverDao.setAlive(srv.id, alive, now())
                        alive
                    }
                }
            }.map { it.await() }
        }
        val live = results.count { it }
        val dead = results.size - live
        Log.i(TAG, "probeAll: ${servers.size} → live=$live dead=$dead")
        return ProbeStats(total = servers.size, live = live, dead = dead)
    }

    private suspend fun probeOne(server: ServerEntity): Boolean = withContext(Dispatchers.IO) {
        val host = extractHost(server.uri) ?: return@withContext false
        runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, server.port), CONNECT_TIMEOUT_MS)
                s.isConnected
            }
        }.getOrDefault(false)
    }

        private fun extractHost(uri: String): String? {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return null
        val rest = uri.substring(schemeEnd + 3)
        val authority = rest.substringBefore('/').substringBefore('?')
        val hostPort = authority.substringAfter('@', authority)
        val host = hostPort.substringBefore(':')
        return host.takeIf { it.isNotBlank() }
    }

    data class ProbeStats(val total: Int, val live: Int, val dead: Int)

    private companion object {
        const val TAG = "LiveProber"
        const val MAX_CONCURRENT = 16
        val PROBE_TIMEOUT = 5.seconds
        const val CONNECT_TIMEOUT_MS = 3_000
    }
}
