package ru.ozero.commonnet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom

enum class NatObservation {
    TRANSLATED,
    UNCHANGED,
}

data class NetworkDiagnosticsReport(
    val linkMtu: Int?,
    val tcpReachable: Boolean,
    val nat: NatObservation?,
)

class NetworkDiagnostics(
    private val tcpHost: String = "1.1.1.1",
    private val tcpPort: Int = 443,
    private val stunHost: String = "stun.cloudflare.com",
    private val stunPort: Int = 3478,
    private val timeoutMs: Int = 3_000,
) {
    init {
        require(timeoutMs > 0)
    }

    suspend fun run(linkMtu: Int?, isAllowed: () -> Boolean): NetworkDiagnosticsReport? =
        withContext(Dispatchers.IO) {
            val allowed = { isActive && isAllowed() }
            if (!allowed()) return@withContext null
            val tcp = checkTcp(allowed)
            if (!allowed()) return@withContext null
            val nat = checkStun(allowed)
            if (!allowed()) return@withContext null
            NetworkDiagnosticsReport(linkMtu, tcp, nat)
        }

    private fun checkTcp(allowed: () -> Boolean): Boolean = runCatching {
        Socket().use { socket ->
            val target = InetSocketAddress(tcpHost, tcpPort)
            if (!allowed()) return false
            socket.connect(target, timeoutMs)
            true
        }
    }.getOrDefault(false)

    private fun checkStun(allowed: () -> Boolean): NatObservation? = runCatching {
        val target = InetSocketAddress(stunHost, stunPort)
        if (target.isUnresolved || !allowed()) return null
        DatagramSocket().use { socket ->
            socket.soTimeout = timeoutMs
            socket.connect(target)
            val local = socket.localSocketAddress
            val transaction = ByteArray(12).also { SecureRandom().nextBytes(it) }
            val request = StunBinding.request(transaction)
            if (!allowed()) return null
            socket.send(DatagramPacket(request, request.size))
            val response = DatagramPacket(ByteArray(2048), 2048)
            socket.receive(response)
            val mapped = StunBinding.mappedAddress(response.data.copyOf(response.length), transaction)
                ?: return null
            if (mapped == local) NatObservation.UNCHANGED else NatObservation.TRANSLATED
        }
    }.getOrNull()
}
