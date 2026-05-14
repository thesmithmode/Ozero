package ru.ozero.enginewarp

import android.content.Context
import android.os.ParcelFileDescriptor
import android.util.Log
import com.getkeepsafe.relinker.ReLinker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.GoBackend
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtector
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class RealWarpSdkBridge(
    private val awgRuntime: AwgRuntime,
) : WarpSdkBridge {

    constructor(context: Context) : this(ReLinkerAwgRuntime(context))

    private val tunnelHandle = AtomicInteger(INVALID_HANDLE)

    override suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.AttachResult = withContext(Dispatchers.IO) {
        if (tunFd < 0) return@withContext WarpSdkBridge.AttachResult.Failed("invalid tunFd=$tunFd")
        if (iniConfig.isBlank()) return@withContext WarpSdkBridge.AttachResult.Failed("empty iniConfig")
        if (uapiPath.isBlank()) return@withContext WarpSdkBridge.AttachResult.Failed("empty uapiPath")
        val structuralError = validateIniStructure(iniConfig)
        if (structuralError != null) {
            PersistentLoggers.error(TAG, "INI rejected: $structuralError")
            return@withContext WarpSdkBridge.AttachResult.Failed("INI invalid: $structuralError")
        }
        val staleHandle = tunnelHandle.getAndSet(INVALID_HANDLE)
        if (staleHandle != INVALID_HANDLE) {
            PersistentLoggers.warn(
                TAG,
                "attachTun: stale handle=$staleHandle обнаружен — закрываю до нового awgTurnOn",
            )
            runCatching { awgRuntime.turnOff(staleHandle) }
                .onFailure { PersistentLoggers.error(TAG, "stale awgTurnOff failed: ${it.message}") }
        }
        val socketFile = File(uapiPath, "$tunnelName.sock")
        if (socketFile.exists()) {
            val deleted = socketFile.delete()
            Log.i(TAG, "stale socket $socketFile deleted=$deleted")
        }
        logIniDigest(tunnelName, iniConfig)
        val started = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val runtimeVersion = runCatching { awgRuntime.version() }
            .getOrElse { "version-threw:${it.javaClass.simpleName}" }
        PersistentLoggers.warn(
            TAG,
            "awgTurnOn JNI entry name=$tunnelName fd=$tunFd iniLen=${iniConfig.length} thread=$threadName version=$runtimeVersion",
        )
        try {
            val combined = awgRuntime.turnOnAndGetSockets(tunnelName, tunFd, iniConfig, uapiPath)
            val handle = combined.handle
            val dt = System.currentTimeMillis() - started
            PersistentLoggers.warn(
                TAG,
                "awgTurnOn JNI exit handle=$handle v4=${combined.socketV4Fd} v6=${combined.socketV6Fd} dt=${dt}ms thread=$threadName",
            )
            if (handle < 0) {
                return@withContext WarpSdkBridge.AttachResult.Failed("awgTurnOn handle=$handle")
            }
            tunnelHandle.set(handle)
            val protectOk = protectSockets(combined.socketV4Fd, combined.socketV6Fd, protector)
            if (!protectOk) {
                PersistentLoggers.error(TAG, "protect failed — rolling back to avoid routing loop")
                if (tunnelHandle.compareAndSet(handle, INVALID_HANDLE)) {
                    runCatching { awgRuntime.turnOff(handle) }
                }
                return@withContext WarpSdkBridge.AttachResult.Failed("protect underlying sockets failed")
            }
            WarpSdkBridge.AttachResult.Success
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val dt = System.currentTimeMillis() - started
            val msg = t.message ?: t.javaClass.simpleName
            PersistentLoggers.error(
                TAG,
                "awgTurnOn threw dt=${dt}ms thread=$threadName: $msg (${t.javaClass.name})",
            )
            WarpSdkBridge.AttachResult.Failed("awgTurnOn failed: $msg")
        }
    }

    private fun protectSockets(v4: Int, v6: Int, protector: VpnSocketProtector): Boolean {
        if (v4 <= 0 && v6 <= 0) {
            PersistentLoggers.error(TAG, "no underlying sockets available v4=$v4 v6=$v6")
            return false
        }
        var anyProtected = false
        if (v4 > 0) {
            val ok = protector.protect(v4)
            Log.i(TAG, "protect v4 sock=$v4 ok=$ok")
            closeRawFd(v4, "v4")
            if (!ok) {
                PersistentLoggers.error(TAG, "protect v4 returned false — VpnService binding lost")
                closeRawFd(v6, "v6")
                return false
            }
            anyProtected = true
        }
        if (v6 > 0) {
            val ok = protector.protect(v6)
            Log.i(TAG, "protect v6 sock=$v6 ok=$ok")
            closeRawFd(v6, "v6")
            if (!ok) {
                PersistentLoggers.warn(TAG, "protect v6 returned false — continuing v4-only")
            } else {
                anyProtected = true
            }
        }
        return anyProtected
    }

    private fun closeRawFd(rawFd: Int, label: String) {
        if (rawFd <= 0) return
        runCatching { ParcelFileDescriptor.adoptFd(rawFd).close() }
            .onFailure { PersistentLoggers.warn(TAG, "close raw fd=$rawFd ($label) failed: ${it.message}") }
    }

    override suspend fun detachTun() {
        withContext(Dispatchers.IO) {
            val h = tunnelHandle.getAndSet(INVALID_HANDLE)
            if (h == INVALID_HANDLE) return@withContext
            val started = System.currentTimeMillis()
            val thread = Thread.currentThread().name
            PersistentLoggers.warn(TAG, "awgTurnOff JNI entry handle=$h thread=$thread")
            try {
                awgRuntime.turnOff(h)
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.warn(TAG, "awgTurnOff JNI exit handle=$h dt=${dt}ms thread=$thread")
            } catch (t: Throwable) {
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.error(TAG, "awgTurnOff failed dt=${dt}ms: ${t.message} (${t.javaClass.name})")
            }
        }
    }

    override fun isRunning(): Boolean = tunnelHandle.get() != INVALID_HANDLE

    private companion object {
        const val TAG = "RealWarpSdkBridge"
        const val INVALID_HANDLE = -1

        fun validateIniStructure(ini: String): String? {
            val hasInterface = ini.lineSequence().any { it.trim().equals("[Interface]", ignoreCase = true) }
            val hasPeer = ini.lineSequence().any { it.trim().equals("[Peer]", ignoreCase = true) }
            return when {
                !hasInterface -> "[Interface] section отсутствует"
                !hasPeer -> "[Peer] section отсутствует"
                else -> null
            }
        }

        fun logIniDigest(tunnelName: String, ini: String) {
            val totalBytes = ini.toByteArray(Charsets.UTF_8).size
            val lines = ini.lines()
            val nonEmpty = lines.count { it.isNotBlank() }
            val keys = lines.mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("[")) {
                    null
                } else {
                    trimmed.substringBefore('=').trim().lowercase()
                }
            }.distinct()
            Log.i(
                TAG,
                "INI digest ($tunnelName): bytes=$totalBytes lines=${lines.size} nonEmpty=$nonEmpty keys=$keys",
            )
            Log.i(TAG, "INI passthrough ($tunnelName):\n${IniSanitizer.sanitize(ini)}")
        }
    }
}

internal object IniSanitizer {
    fun sanitize(ini: String): String = ini.lineSequence().joinToString("\n") { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("PrivateKey", ignoreCase = true) -> "PrivateKey = ***"
            trimmed.startsWith("PresharedKey", ignoreCase = true) -> "PresharedKey = ***"
            trimmed.startsWith("PublicKey", ignoreCase = true) -> redactKey(line, "PublicKey")
            trimmed.startsWith("Endpoint", ignoreCase = true) -> redactEndpoint(line)
            else -> line
        }
    }

    private fun redactKey(line: String, name: String): String {
        val value = line.substringAfter('=').trim()
        val tail = value.takeLast(8).ifEmpty { "***" }
        return "$name = ***$tail"
    }

    private fun redactEndpoint(line: String): String {
        val value = line.substringAfter('=').trim()
        val port = value.substringAfterLast(':', "")
        return if (port.isNotEmpty() && port.all { it.isDigit() }) {
            "Endpoint = ***:$port"
        } else {
            "Endpoint = ***"
        }
    }
}

interface AwgRuntime {
    fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int
    fun turnOff(handle: Int)
    fun getSocketV4(handle: Int): Int
    fun getSocketV6(handle: Int): Int

    fun version(): String = "unknown"

    @Deprecated("awgGetConfig causes SIGSEGV on partial-handshake handle, use only post-disconnect")
    fun getConfig(handle: Int): String? = null

    fun turnOnAndGetSockets(name: String, tunFd: Int, ini: String, uapiPath: String): AwgTurnOnResult {
        val handle = turnOn(name, tunFd, ini, uapiPath)
        if (handle < 0) return AwgTurnOnResult(handle, -1, -1)
        val v4 = runCatching { getSocketV4(handle) }.getOrDefault(-1)
        val v6 = runCatching { getSocketV6(handle) }.getOrDefault(-1)
        return AwgTurnOnResult(handle, v4, v6)
    }
}

data class AwgTurnOnResult(val handle: Int, val socketV4Fd: Int, val socketV6Fd: Int)

class ReLinkerAwgRuntime(context: Context) : AwgRuntime {
    private val appContext = context.applicationContext

    @Volatile private var loaded = false
    private val lock = Any()

    private fun loadOnce() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val started = System.currentTimeMillis()
            val thread = Thread.currentThread().name
            try {
                ReLinker.loadLibrary(appContext, LIB_NAME)
                loaded = true
                val dt = System.currentTimeMillis() - started
                Log.d(TAG, "ReLinker.loadLibrary $LIB_NAME ok dt=${dt}ms thread=$thread")
            } catch (e: Throwable) {
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.error(
                    TAG,
                    "ReLinker.loadLibrary $LIB_NAME failed dt=${dt}ms thread=$thread err=${e.message} (${e.javaClass.name})",
                )
                throw e
            }
        }
    }

    override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
        loadOnce()
        return GoBackend.awgTurnOn(name, tunFd, ini, uapiPath)
    }

    override fun version(): String = runCatching {
        loadOnce()
        GoBackend.awgVersion() ?: "null"
    }.getOrElse { it.javaClass.simpleName }

    override fun getConfig(handle: Int): String? {
        loadOnce()
        return runCatching { GoBackend.awgGetConfig(handle) }.getOrNull()
    }

    override fun turnOff(handle: Int) {
        loadOnce()
        GoBackend.awgTurnOff(handle)
    }

    override fun getSocketV4(handle: Int): Int {
        loadOnce()
        return GoBackend.awgGetSocketV4(handle)
    }

    override fun getSocketV6(handle: Int): Int {
        loadOnce()
        return GoBackend.awgGetSocketV6(handle)
    }

    fun preload() = loadOnce()

    private companion object {
        const val TAG = "ReLinkerAwgRuntime"
        const val LIB_NAME = "am-go"
    }
}
