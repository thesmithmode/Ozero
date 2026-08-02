package ru.ozero.singboxprocess

import android.net.DnsResolver
import android.os.Build
import android.os.CancellationSignal
import android.system.ErrnoException
import io.nekohasekai.libbox.ExchangeContext
import io.nekohasekai.libbox.Func
import io.nekohasekai.libbox.LocalDNSTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
        runBlocking {
            awaitDnsCallback(
                start = { callback ->
                    DnsResolver.getInstance().rawQuery(
                        network,
                        message,
                        DnsResolver.FLAG_NO_RETRY,
                        IO_EXECUTOR,
                        cancellation,
                        callback,
                    )
                },
                onAnswer = { answer, rcode ->
                    if (rcode == RCODE_SUCCESS) context.rawSuccess(answer) else context.errorCode(rcode)
                },
                onErrno = context::errnoCode,
                cancellation = cancellation,
            )
        }
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
        runBlocking {
            awaitDnsCallback<Collection<InetAddress>>(
                start = { callback ->
                    val queryType = when {
                        network.endsWith('4') -> DnsResolver.TYPE_A
                        network.endsWith('6') -> DnsResolver.TYPE_AAAA
                        else -> null
                    }
                    if (queryType == null) {
                        DnsResolver.getInstance().query(
                            physicalNetwork,
                            domain,
                            DnsResolver.FLAG_NO_RETRY,
                            IO_EXECUTOR,
                            cancellation,
                            callback,
                        )
                    } else {
                        DnsResolver.getInstance().query(
                            physicalNetwork,
                            domain,
                            queryType,
                            DnsResolver.FLAG_NO_RETRY,
                            IO_EXECUTOR,
                            cancellation,
                            callback,
                        )
                    }
                },
                onAnswer = { answer, rcode ->
                    if (rcode == RCODE_SUCCESS) {
                        context.success(
                            answer
                                .filterNetwork(network)
                                .joinToString("\n") { it.hostAddress.orEmpty() },
                        )
                    } else {
                        context.errorCode(rcode)
                    }
                },
                onErrno = context::errnoCode,
                cancellation = cancellation,
            )
        }
    }

    private fun lookupLegacy(context: ExchangeContext, addresses: List<InetAddress>, network: String) {
        context.success(addresses.filterNetwork(network).joinToString("\n") { it.hostAddress.orEmpty() })
    }

    private fun Collection<InetAddress>.filterNetwork(
        network: String,
    ): Collection<InetAddress> = when (network.lowercase()) {
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

internal suspend fun <T : Any> awaitDnsCallback(
    start: (DnsResolver.Callback<T>) -> Unit,
    onAnswer: (T, Int) -> Unit,
    onErrno: (Int) -> Unit,
    cancellation: CancellationSignal? = null,
    errnoExtractor: (Throwable?) -> Int? = { cause ->
        (cause as? ErrnoException)?.errno
    },
) = suspendCancellableCoroutine { continuation ->
    val completed = AtomicBoolean(false)
    fun complete(action: () -> Unit) {
        if (!completed.compareAndSet(false, true)) return
        try {
            action()
            continuation.resume(Unit)
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
        }
    }
    cancellation?.setOnCancelListener {
        if (completed.compareAndSet(false, true)) {
            continuation.cancel(CancellationException("DNS query cancelled"))
        }
    }
    continuation.invokeOnCancellation { cancellation?.cancel() }
    start(
        object : DnsResolver.Callback<T> {
            override fun onAnswer(answer: T, rcode: Int) {
                complete { onAnswer(answer, rcode) }
            }

            override fun onError(error: DnsResolver.DnsException) {
                val errno = errnoExtractor(error.cause)
                if (errno != null) {
                    complete { onErrno(errno) }
                } else {
                    complete { throw error }
                }
            }
        },
    )
}
