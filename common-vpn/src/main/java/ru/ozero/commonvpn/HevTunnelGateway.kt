package ru.ozero.commonvpn

import android.content.Context
import android.os.ParcelFileDescriptor
import ru.ozero.enginescore.PersistentLoggers
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
        val tName = Thread.currentThread().name
        PersistentLoggers.instance?.info(TAG, "start entry thread=$tName originalFd=${config.tunPfd.fd}")

        val tLoad0 = System.nanoTime()
        hev.TProxyService.loadOnce()
        val tLoadMs = (System.nanoTime() - tLoad0) / 1_000_000
        val loaded = hev.TProxyService.libraryLoaded
        val loadErr = hev.TProxyService.loadError
        PersistentLoggers.instance?.info(
            TAG,
            "checkpoint loadOnce returned dt=${tLoadMs}ms libraryLoaded=$loaded loadError=$loadErr",
        )
        if (!hev.TProxyService.libraryLoaded) {
            PersistentLoggers.instance?.error(
                TAG,
                "libhev-socks5-tunnel не загружена: ${hev.TProxyService.loadError}",
            )
            return -1
        }

        PersistentLoggers.instance?.info(TAG, "checkpoint pre-dup")
        val duped = try {
            config.tunPfd.dup()
        } catch (t: Throwable) {
            PersistentLoggers.instance?.error(TAG, "tunPfd.dup() threw", t)
            return -1
        }
        PersistentLoggers.instance?.info(TAG, "checkpoint post-dup newFd=${duped.fd}")
        closeDuped()
        dupedRef.set(duped)

        PersistentLoggers.instance?.info(TAG, "checkpoint pre-writeConfig")
        val configFile = writeConfig(config)
        PersistentLoggers.instance?.info(
            TAG,
            "checkpoint post-writeConfig path=${configFile.absolutePath} bytes=${configFile.length()}",
        )

        PersistentLoggers.instance?.info(TAG, "checkpoint pre-nativeStart fd=${duped.fd}")
        val tNative0 = System.nanoTime()
        val code = runCatching { nativeStart(configFile.absolutePath, duped.fd) }
            .onFailure {
                PersistentLoggers.instance?.error(TAG, "TProxyStartService threw", it)
                closeDuped()
            }
            .getOrElse { -1 }
        val tNativeMs = (System.nanoTime() - tNative0) / 1_000_000
        PersistentLoggers.instance?.info(TAG, "checkpoint post-nativeStart code=$code dt=${tNativeMs}ms")
        if (code != 0) {
            closeDuped()
        }
        return code
    }

    override fun stop() {
        closeDuped()
        runCatching { nativeStop() }
            .onFailure { PersistentLoggers.instance?.warn(TAG, "TProxyStopService threw", it) }
    }

    private fun closeDuped() {
        dupedRef.getAndSet(null)?.let { pfd ->
            runCatching { pfd.close() }
                .onFailure { PersistentLoggers.instance?.warn(TAG, "duped pfd.close threw", it) }
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
