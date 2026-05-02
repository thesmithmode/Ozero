package ru.ozero.commonvpn

import android.content.Context
import ru.ozero.enginescore.PersistentLoggers
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

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
) : HevTunnelGateway {

    constructor(context: Context) : this(cacheDir = context.cacheDir)

    private val started = AtomicBoolean(false)

    override fun start(config: HevTunnelConfig): Int {
        val fd = config.tunPfd.fd
        PersistentLoggers.instance?.info(
            TAG,
            "start entry thread=${Thread.currentThread().name} fd=$fd",
        )

        val tLoad0 = System.nanoTime()
        loader.loadOnce()
        val tLoadMs = (System.nanoTime() - tLoad0) / 1_000_000
        PersistentLoggers.instance?.info(
            TAG,
            "checkpoint loadOnce returned dt=${tLoadMs}ms libraryLoaded=${loader.libraryLoaded}",
        )
        if (!loader.libraryLoaded) {
            PersistentLoggers.instance?.error(
                TAG,
                "libhev-socks5-tunnel не загружена: ${loader.loadError}",
            )
            return -1
        }

        PersistentLoggers.instance?.info(TAG, "checkpoint pre-writeConfig")
        val configFile = writeConfig(config)
        PersistentLoggers.instance?.info(
            TAG,
            "checkpoint post-writeConfig path=${configFile.absolutePath} bytes=${configFile.length()}",
        )

        PersistentLoggers.instance?.info(TAG, "checkpoint pre-nativeStart fd=$fd")
        val tNative0 = System.nanoTime()
        val code = runCatching { nativeStart(configFile.absolutePath, fd) }
            .onFailure { PersistentLoggers.instance?.error(TAG, "TProxyStartService threw", it) }
            .getOrElse { -1 }
        val tNativeMs = (System.nanoTime() - tNative0) / 1_000_000
        PersistentLoggers.instance?.info(TAG, "checkpoint post-nativeStart code=$code dt=${tNativeMs}ms")
        if (code == 0) started.set(true)
        return code
    }

    // libhev internal mutex deadlock: TProxyStopService без предшествующего TProxyStartService
    // блокирует следующий start (waits forever на handshake mutex). Поэтому stop() — no-op
    // если start не был успешен. v1.0.5: воспроизводилось когда URnetwork падал на step 0
    // → performShutdown вызывал tunnelGateway.stop() → следующий BYEDPI start висел.
    override fun stop() {
        if (!started.compareAndSet(true, false)) {
            PersistentLoggers.instance?.info(TAG, "stop skipped — gateway not started")
            return
        }
        runCatching { nativeStop() }
            .onFailure { PersistentLoggers.instance?.warn(TAG, "TProxyStopService threw", it) }
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
