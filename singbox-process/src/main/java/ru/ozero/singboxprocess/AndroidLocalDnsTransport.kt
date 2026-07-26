package ru.ozero.singboxprocess

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.Func
import io.nekohasekai.libbox.LocalDNSTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

internal class AndroidLocalDnsTransport(
    private val defaultInterfaceMonitor: DefaultInterfaceMonitor,
) : LocalDNSTransport {
    override fun raw(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    override fun exchange(context: ExchangeContext, message: ByteArray) {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { "Raw DNS requires Android 10" }
        val network = requireNotNull(defaultInterfaceMonitor.currentPhysicalNetwork()) {
            "Physical network unavailable"
        }
        val cancellation = CancellationSignal()
        context.onCancel(Func { cancellation.cancel() })
        DnsResolver.getInstance().rawQuery(
            network,
            message,
            DnsResolver.FLAG_NO_RETRY,
            IO_EXECUTOR,
            cancellation,
            object : DnsResolver.Callback<ByteArray> {
                override fun onAnswer(answer: ByteArray, rcode: Int) {
                    if (rcode == RCODE_SUCCESS) context.rawSuccess(answer) else context.errorCode(rcode)
                }

                override fun onError(error: DnsResolver.DnsException) = context.report(error)
            },
        )
    }

    override fun lookup(context: ExchangeContext, network: String, domain: String) {
        val physicalNetwork = requireNotNull(defaultInterfaceMonitor.currentPhysicalNetwork()) {
            "Physical network unavailable"
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            try {
                lookupLegacy(context, physicalNetwork.getAllByName(domain).toList(), network)
            } catch (_: UnknownHostException) {
                context.errorCode(RCODE_NXDOMAIN)
            }
            return
        }
        val cancellation = CancellationSignal()
        context.onCancel(Func { cancellation.cancel() })
        DnsResolver.getInstance().query(
            physicalNetwork,
            domain,
            DnsResolver.FLAG_NO_RETRY,
            IO_EXECUTOR,
            cancellation,
            object : DnsResolver.Callback<List<InetAddress>> {
                override fun onAnswer(answer: List<InetAddress>, rcode: Int) {
                    if (rcode != RCODE_SUCCESS) {
                        context.errorCode(rcode)
                        return
                    }
                    context.success(answer.filterNetwork(network).joinToString("\n") { it.hostAddress.orEmpty() })
                }

                override fun onError(error: DnsResolver.DnsException) = context.report(error)
            },
        )
    }

    private fun lookupLegacy(context: ExchangeContext, addresses: List<InetAddress>, network: String) {
        context.success(addresses.filterNetwork(network).joinToString("\n") { it.hostAddress.orEmpty() })
    }

    private fun ExchangeContext.report(error: DnsResolver.DnsException) {
        val cause = error.cause
        if (cause is ErrnoException) errnoCode(cause.errno) else errorCode(error.code)
    }

    private fun List<InetAddress>.filterNetwork(network: String): List<InetAddress> = when (network.lowercase()) {
        "ip4", "ipv4" -> filterIsInstance<Inet4Address>()
        "ip6", "ipv6" -> filterIsInstance<Inet6Address>()
        else -> this
    }

    private companion object {
        const val RCODE_SUCCESS = 0
        const val RCODE_NXDOMAIN = 3
        val IO_EXECUTOR = Dispatchers.IO.asExecutor()
    }
}
