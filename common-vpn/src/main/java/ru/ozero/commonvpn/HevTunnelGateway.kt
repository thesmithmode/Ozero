package ru.ozero.commonvpn

import android.content.Context
import android.util.Log
import ru.ozero.coreapi.PersistentLoggers
import java.io.File

interface HevTunnelGateway {
    fun start(config: HevTunnelConfig): Int
    fun stop()
}

class NativeHevTunnelGateway(
    private val cacheDir: File,
    private val nativeStart: (configPath: String, fd: Int) -> Int = { path, fd ->
        hev.TProxyService.TProxyStartService(path, fd)
        0
    },
    private val nativeStop: () -> Unit = { hev.TProxyService.TProxyStopService() },
) : HevTunnelGateway {

    constructor(context: Context) : this(cacheDir = context.cacheDir)

    override fun start(config: HevTunnelConfig): Int {
        if (!hev.TProxyService.libraryLoaded) {
            Log.e(TAG, "libhev-socks5-tunnel не загружена: ${hev.TProxyService.loadError}")
            PersistentLoggers.instance?.error(
                TAG,
                "libhev not loaded: ${hev.TProxyService.loadError}",
            )
            return -1
        }
        val configFile = writeConfig(config)
        Log.i(TAG, "TProxyStartService path=${configFile.absolutePath} fd=${config.tunFd}")
        PersistentLoggers.instance?.info(TAG, "TProxyStartService fd=${config.tunFd}")
        val code = runCatching { nativeStart(configFile.absolutePath, config.tunFd) }
            .onFailure {
                Log.e(TAG, "TProxyStartService threw", it)
                PersistentLoggers.instance?.error(TAG, "TProxyStartService threw", it)
            }
            .getOrElse { -1 }
        PersistentLoggers.instance?.info(TAG, "TProxyStartService → code=$code")
        return code
    }

    override fun stop() {
        runCatching { nativeStop() }
            .onFailure { Log.w(TAG, "TProxyStopService threw", it) }
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
