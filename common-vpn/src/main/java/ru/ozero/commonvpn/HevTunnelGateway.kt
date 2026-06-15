package ru.ozero.commonvpn

import android.content.Context
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

interface HevTunnelGateway {
    fun start(config: HevTunnelConfig): Int
    fun stop()
}

class NativeHevTunnelGateway(
    private val cacheDir: File,
    private val loader: TProxyLoader = DefaultTProxyLoader,
    private val nativeStart: (configPath: String, fd: Int) -> Int = { path, fd ->
        hev.TProxyService.TProxyStartService(path, fd)
        0
    },
    private val nativeStop: () -> Unit = { hev.TProxyService.TProxyStopService() },
    private val nativeStats: () -> LongArray? = {
        runCatching { hev.TProxyService.TProxyGetStats() }.getOrNull()
    },
    private val pollIntervalMs: Long = STATS_POLL_INTERVAL_MS,
    private val statsPollerEnabled: Boolean = false,
) : HevTunnelGateway {

    constructor(context: Context) : this(cacheDir = context.cacheDir, statsPollerEnabled = true)

    private val started = AtomicBoolean(false)
    private val statsPoller = AtomicReference<Thread?>(null)

    override fun start(config: HevTunnelConfig): Int {
        val fd = config.tunPfd.fd
        PersistentLoggers.info(
            TAG,
            "start entry thread=${Thread.currentThread().name} fd=$fd",
        )

        val tLoad0 = System.nanoTime()
        loader.loadOnce()
        val tLoadMs = (System.nanoTime() - tLoad0) / 1_000_000
        PersistentLoggers.info(
            TAG,
            "checkpoint loadOnce returned dt=${tLoadMs}ms libraryLoaded=${loader.libraryLoaded}",
        )
        if (!loader.libraryLoaded) {
            PersistentLoggers.error(
                TAG,
                "libhev-ozero-socks5-tunnel не загружена: ${loader.loadError}",
            )
            return -1
        }

        PersistentLoggers.info(TAG, "checkpoint pre-writeConfig")
        val configFile = writeConfig(config)
        PersistentLoggers.info(
            TAG,
            "checkpoint post-writeConfig path=${configFile.absolutePath} bytes=${configFile.length()}",
        )

        PersistentLoggers.info(TAG, "checkpoint pre-nativeStart fd=$fd")
        val tNative0 = System.nanoTime()
        val code = runCatching { nativeStart(configFile.absolutePath, fd) }
            .onFailure { PersistentLoggers.error(TAG, "TProxyStartService threw", it) }
            .getOrElse { -1 }
        val tNativeMs = (System.nanoTime() - tNative0) / 1_000_000
        PersistentLoggers.info(TAG, "checkpoint post-nativeStart code=$code dt=${tNativeMs}ms")
        if (code == 0) {
            started.set(true)
            if (statsPollerEnabled) startStatsPoller()
        }
        return code
    }

    private fun startStatsPoller() {
        val poller = Thread({
            var prev = longArrayOf(0L, 0L, 0L, 0L)
            var idleTicks = 0
            try {
                while (started.get()) {
                    Thread.sleep(pollIntervalMs)
                    val s = nativeStats() ?: continue
                    if (s.size < 4) continue
                    val txPkts = s[STATS_IDX_TX_PKTS]
                    val txBytes = s[STATS_IDX_TX_BYTES]
                    val rxPkts = s[STATS_IDX_RX_PKTS]
                    val rxBytes = s[STATS_IDX_RX_BYTES]
                    val dTxB = txBytes - prev[STATS_IDX_TX_BYTES]
                    val dRxB = rxBytes - prev[STATS_IDX_RX_BYTES]
                    val dTxP = txPkts - prev[STATS_IDX_TX_PKTS]
                    val dRxP = rxPkts - prev[STATS_IDX_RX_PKTS]
                    val movement = dTxB != 0L || dRxB != 0L
                    if (movement) {
                        idleTicks = 0
                        PersistentLoggers.info(
                            TAG,
                            "hev stats tx=${txBytes}B/${txPkts}p rx=${rxBytes}B/${rxPkts}p " +
                                "Δtx=${dTxB}B/${dTxP}p Δrx=${dRxB}B/${dRxP}p",
                        )
                    } else {
                        idleTicks++
                        if (idleTicks % STATS_IDLE_REPORT_EVERY == 0) {
                            PersistentLoggers.warn(
                                TAG,
                                "hev stats IDLE ${idleTicks * pollIntervalMs / 1000}s " +
                                    "tx=${txBytes}B/${txPkts}p rx=${rxBytes}B/${rxPkts}p — " +
                                    "TUN→hev pipeline пуст: либо нет default route, либо app не генерит трафик",
                            )
                        }
                    }
                    prev = s
                }
            } catch (_: InterruptedException) {
                /* stop signal */
            } catch (t: Throwable) {
                PersistentLoggers.warn(TAG, "stats poller stopped: ${t.message}")
            }
        }, "hev-stats-poller").apply { isDaemon = true }
        statsPoller.getAndSet(poller)?.interrupt()
        poller.start()
    }

    // libhev internal mutex deadlock: TProxyStopService без предшествующего TProxyStartService
    // блокирует следующий start (waits forever на handshake mutex). Поэтому stop() — no-op
    // если start не был успешен: воспроизводилось когда URnetwork падал на step 0
    // → performShutdown вызывал tunnelGateway.stop() → следующий BYEDPI start висел.
    override fun stop() {
        if (!started.compareAndSet(true, false)) {
            PersistentLoggers.info(TAG, "stop skipped — gateway not started")
            return
        }
        statsPoller.getAndSet(null)?.interrupt()
        runCatching { nativeStop() }
            .onFailure { PersistentLoggers.warn(TAG, "TProxyStopService threw", it) }
    }

    private fun writeConfig(config: HevTunnelConfig): File {
        if (!cacheDir.exists()) cacheDir.mkdirs()
        val file = File(cacheDir, CONFIG_FILE)
        file.writeText(config.toYaml())
        return file
    }

    private companion object {
        const val TAG = "NativeHevTunnel"
        const val CONFIG_FILE = "hev-socks5-tunnel.yaml"

        const val STATS_IDX_TX_PKTS = 0
        const val STATS_IDX_TX_BYTES = 1
        const val STATS_IDX_RX_PKTS = 2
        const val STATS_IDX_RX_BYTES = 3
        const val STATS_POLL_INTERVAL_MS = 5_000L
        const val STATS_IDLE_REPORT_EVERY = 6
    }
}

interface TProxyLoader {
    fun loadOnce()
    val libraryLoaded: Boolean
    val loadError: String?
}

internal object DefaultTProxyLoader : TProxyLoader {
    override fun loadOnce() = hev.TProxyService.loadOnce()
    override val libraryLoaded: Boolean get() = hev.TProxyService.libraryLoaded
    override val loadError: String? get() = hev.TProxyService.loadError
}
