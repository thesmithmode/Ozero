package ru.ozero.commonvpn

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.ozero.coreapi.PersistentLoggers
import java.io.File
import java.util.concurrent.atomic.AtomicReference

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

    private val dupedRef = AtomicReference<ParcelFileDescriptor?>(null)

    override fun start(config: HevTunnelConfig): Int {
        hev.TProxyService.loadOnce()
        PersistentLoggers.instance?.info(
            TAG,
            "start entry libraryLoaded=${hev.TProxyService.libraryLoaded} loadError=${hev.TProxyService.loadError}",
        )
        if (!hev.TProxyService.libraryLoaded) {
            Log.e(TAG, "libhev-socks5-tunnel не загружена: ${hev.TProxyService.loadError}")
            PersistentLoggers.instance?.error(
                TAG,
                "libhev not loaded: ${hev.TProxyService.loadError}",
            )
            return -1
        }
        val duped = try {
            config.tunPfd.dup()
        } catch (t: Throwable) {
            Log.e(TAG, "tunPfd.dup() threw", t)
            PersistentLoggers.instance?.error(TAG, "tunPfd.dup() threw", t)
            return -1
        }
        closeDuped()
        dupedRef.set(duped)
        val configFile = writeConfig(config)
        Log.i(TAG, "TProxyStartService path=${configFile.absolutePath} fd=${duped.fd}")
        PersistentLoggers.instance?.info(TAG, "TProxyStartService fd=${duped.fd}")
        val code = runCatching { nativeStart(configFile.absolutePath, duped.fd) }
            .onFailure {
                Log.e(TAG, "TProxyStartService threw", it)
                PersistentLoggers.instance?.error(TAG, "TProxyStartService threw", it)
            }
            .getOrElse { -1 }
        PersistentLoggers.instance?.info(TAG, "TProxyStartService → code=$code")
        if (code != 0) {
            closeDuped()
        }
        return code
    }

    override fun stop() {
        runCatching { nativeStop() }
            .onFailure { Log.w(TAG, "TProxyStopService threw", it) }
        closeDuped()
    }

    private fun closeDuped() {
        dupedRef.getAndSet(null)?.let { pfd ->
            runCatching { pfd.close() }
                .onFailure { Log.w(TAG, "duped pfd.close threw", it) }
        }
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
