package ru.ozero.enginewarp

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtector
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class RealWarpSdkBridge(
    private val awgRuntime: AwgRuntime,
) : WarpSdkBridge {

    private val tunnelHandle = AtomicInteger(INVALID_HANDLE)
    private val proxyHandle = AtomicInteger(INVALID_HANDLE)

    @Volatile private var savedProtector: VpnSocketProtector? = null

    override suspend fun attachTun(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.AttachResult = withContext(Dispatchers.IO) {
        validateAttachArgs(tunFd, iniConfig, uapiPath)?.let {
            return@withContext WarpSdkBridge.AttachResult.Failed(it)
        }
        closeStaleHandle()
        cleanupStaleSockets(uapiPath, tunnelName)
        logIniDigest(tunnelName, iniConfig)
        val result = invokeAwgTurnOnAndProtect(tunnelName, tunFd, iniConfig, uapiPath, protector)
        if (result is WarpSdkBridge.AttachResult.Success) savedProtector = protector
        result
    }

    override fun reprotectSockets() {
        val handle = tunnelHandle.get()
        if (handle == INVALID_HANDLE) return
        val protector = savedProtector ?: return
        val v4 = runCatching { awgRuntime.getSocketV4(handle) }.getOrDefault(-1)
        val v6 = runCatching { awgRuntime.getSocketV6(handle) }.getOrDefault(-1)
        PersistentLoggers.debug(TAG, "reprotectSockets network change: v4=$v4 v6=$v6 handle=$handle")
        protectSockets(v4, v6, protector)
    }

    override suspend fun startProxy(
        tunnelName: String,
        iniConfig: String,
        uapiPath: String,
        socksPort: Int,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.ProxyResult = withContext(Dispatchers.IO) {
        validateProxyArgs(iniConfig, uapiPath, socksPort)?.let {
            return@withContext WarpSdkBridge.ProxyResult.Failed(it)
        }
        closeStaleProxy()
        cleanupStaleSockets(uapiPath, tunnelName)
        logIniDigest(tunnelName, iniConfig)
        val started = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        PersistentLoggers.debug(
            TAG,
            "awgStartProxy JNI entry name=$tunnelName port=$socksPort iniLen=${iniConfig.length} thread=$threadName",
        )
        val handle = try {
            awgRuntime.startProxy(tunnelName, iniConfig, uapiPath, socksPort, protector)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            val dt = System.currentTimeMillis() - started
            PersistentLoggers.error(TAG, "awgStartProxy threw dt=${dt}ms: ${t.message} (${t.javaClass.name})")
            return@withContext WarpSdkBridge.ProxyResult.Failed(
                "awgStartProxy failed: ${t.message ?: t.javaClass.simpleName}",
            )
        }
        val dt = System.currentTimeMillis() - started
        PersistentLoggers.debug(TAG, "awgStartProxy JNI exit handle=$handle dt=${dt}ms thread=$threadName")
        if (handle < 0) {
            return@withContext WarpSdkBridge.ProxyResult.Failed("awgStartProxy handle=$handle")
        }
        proxyHandle.set(handle)
        savedProtector = protector
        WarpSdkBridge.ProxyResult.Success
    }

    private fun validateProxyArgs(iniConfig: String, uapiPath: String, socksPort: Int): String? {
        if (socksPort !in 1..65535) return "invalid socksPort=$socksPort"
        if (iniConfig.isBlank()) return "empty iniConfig"
        if (uapiPath.isBlank()) return "empty uapiPath"
        val structuralError = validateIniStructure(iniConfig)
        return if (structuralError != null) {
            PersistentLoggers.error(TAG, "INI rejected: $structuralError")
            "INI invalid: $structuralError"
        } else {
            null
        }
    }

    private fun closeStaleProxy() {
        val staleHandle = proxyHandle.getAndSet(INVALID_HANDLE)
        if (staleHandle == INVALID_HANDLE) return
        PersistentLoggers.warn(TAG, "startProxy: stale handle=$staleHandle found, stopping previous proxy")
        runCatching {
            awgRuntime.resetProxyGlobals()
            awgRuntime.stopProxy()
        }.onFailure { PersistentLoggers.error(TAG, "stale awgStopProxy failed: ${it.message}") }
    }

    private fun validateAttachArgs(tunFd: Int, iniConfig: String, uapiPath: String): String? {
        if (tunFd < 0) return "invalid tunFd=$tunFd"
        if (iniConfig.isBlank()) return "empty iniConfig"
        if (uapiPath.isBlank()) return "empty uapiPath"
        val structuralError = validateIniStructure(iniConfig)
        if (structuralError != null) {
            PersistentLoggers.error(TAG, "INI rejected: $structuralError")
            return "INI invalid: $structuralError"
        }
        return null
    }

    private fun closeStaleHandle() {
        val staleHandle = tunnelHandle.getAndSet(INVALID_HANDLE)
        if (staleHandle == INVALID_HANDLE) return
        PersistentLoggers.warn(
            TAG,
            "attachTun: stale handle=$staleHandle обнаружен — закрываю до нового awgTurnOn",
        )
        runCatching { awgRuntime.turnOff(staleHandle) }
            .onFailure { PersistentLoggers.error(TAG, "stale awgTurnOff failed: ${it.message}") }
    }

    private fun cleanupStaleSockets(uapiPath: String, tunnelName: String) {
        val ownSocketName = "$tunnelName.sock"
        val legacySocket = File(uapiPath, ownSocketName)
        if (legacySocket.exists()) {
            val deleted = legacySocket.delete()
            Log.i(TAG, "stale legacy socket $legacySocket deleted=$deleted")
        }
        val socketsDir = File(uapiPath, "sockets")
        if (!socketsDir.isDirectory) return
        // awg can leave tunN.sock; handshake fallback must not attach to a stale UAPI socket.
        val stale = socketsDir.listFiles { f -> f.extension == "sock" }.orEmpty()
        if (stale.isEmpty()) return
        val staleDeleted = stale.count { it.delete() }
        Log.i(TAG, "stale sockets/ cleanup: name=$ownSocketName allSock=true deleted=$staleDeleted/${stale.size}")
    }

    private fun invokeAwgTurnOnAndProtect(
        tunnelName: String,
        tunFd: Int,
        iniConfig: String,
        uapiPath: String,
        protector: VpnSocketProtector,
    ): WarpSdkBridge.AttachResult {
        val started = System.currentTimeMillis()
        val threadName = Thread.currentThread().name
        val runtimeVersion = runCatching { awgRuntime.version() }
            .getOrElse { "version-threw:${it.javaClass.simpleName}" }
        PersistentLoggers.debug(
            TAG,
            "awgTurnOn JNI entry name=$tunnelName fd=$tunFd iniLen=${iniConfig.length} thread=$threadName version=$runtimeVersion",
        )
        val combined = try {
            awgRuntime.turnOnAndGetSockets(tunnelName, tunFd, iniConfig, uapiPath)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            return logAwgFailure(started, threadName, t)
        }
        val handle = combined.handle
        val dt = System.currentTimeMillis() - started
        PersistentLoggers.debug(
            TAG,
            "awgTurnOn JNI exit handle=$handle v4=${combined.socketV4Fd} v6=${combined.socketV6Fd} dt=${dt}ms thread=$threadName",
        )
        // amnezia api.go: errors → -1, success → first free slot (0,1,2,...). PORTAL_WG g.java:106 тоже `< 0`. Не менять на `<= 0`.
        if (handle < 0) {
            return WarpSdkBridge.AttachResult.Failed(
                "awgTurnOn handle=$handle (<0 = AWG SDK ошибка; 0 = валидный первый tunnel slot)",
            )
        }
        tunnelHandle.set(handle)
        val protectOk = try {
            protectSockets(combined.socketV4Fd, combined.socketV6Fd, protector)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            PersistentLoggers.error(
                TAG,
                "protect threw: ${t.message} (${t.javaClass.name}) — rollback awgTurnOff",
            )
            if (tunnelHandle.compareAndSet(handle, INVALID_HANDLE)) {
                runCatching { awgRuntime.turnOff(handle) }
            }
            return WarpSdkBridge.AttachResult.Failed("protect threw: ${t.message ?: t.javaClass.simpleName}")
        }
        if (!protectOk) {
            PersistentLoggers.error(TAG, "protect failed — rolling back to avoid routing loop")
            if (tunnelHandle.compareAndSet(handle, INVALID_HANDLE)) {
                runCatching { awgRuntime.turnOff(handle) }
            }
            return WarpSdkBridge.AttachResult.Failed("protect underlying sockets failed")
        }
        return WarpSdkBridge.AttachResult.Success
    }

    private fun logAwgFailure(startedMs: Long, threadName: String, t: Throwable): WarpSdkBridge.AttachResult {
        val dt = System.currentTimeMillis() - startedMs
        val msg = t.message ?: t.javaClass.simpleName
        PersistentLoggers.error(
            TAG,
            "awgTurnOn threw dt=${dt}ms thread=$threadName: $msg (${t.javaClass.name})",
        )
        return WarpSdkBridge.AttachResult.Failed("awgTurnOn failed: $msg")
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
            if (h == INVALID_HANDLE) {
                closeRuntimeIfIdle()
                return@withContext
            }
            val started = System.currentTimeMillis()
            val thread = Thread.currentThread().name
            PersistentLoggers.debug(TAG, "awgTurnOff JNI entry handle=$h thread=$thread")
            try {
                awgRuntime.turnOff(h)
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.debug(TAG, "awgTurnOff JNI exit handle=$h dt=${dt}ms thread=$thread")
                // Go goroutines from the killed tunnel don't stop synchronously.
                // Without a pause, a rapid awgTurnOn reuses handle=0 before the old
                // goroutines finish, causing handshake interference on the next session.
                delay(AWG_TEARDOWN_COOLDOWN_MS)
            } catch (t: Throwable) {
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.error(TAG, "awgTurnOff failed dt=${dt}ms: ${t.message} (${t.javaClass.name})")
            }
            closeRuntimeIfIdle()
        }
    }

    override suspend fun stopProxy() {
        withContext(Dispatchers.IO) {
            val h = proxyHandle.getAndSet(INVALID_HANDLE)
            if (h == INVALID_HANDLE) {
                closeRuntimeIfIdle()
                return@withContext
            }
            val started = System.currentTimeMillis()
            PersistentLoggers.debug(TAG, "awgStopProxy JNI entry handle=$h")
            try {
                awgRuntime.resetProxyGlobals()
                awgRuntime.stopProxy()
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.debug(TAG, "awgStopProxy JNI exit handle=$h dt=${dt}ms")
                delay(AWG_TEARDOWN_COOLDOWN_MS)
            } catch (t: Throwable) {
                val dt = System.currentTimeMillis() - started
                PersistentLoggers.error(TAG, "awgStopProxy failed dt=${dt}ms: ${t.message} (${t.javaClass.name})")
            }
            closeRuntimeIfIdle()
        }
    }

    private fun closeRuntimeIfIdle() {
        if (tunnelHandle.get() != INVALID_HANDLE || proxyHandle.get() != INVALID_HANDLE) return
        awgRuntime.close()
    }

    override fun isRunning(): Boolean = tunnelHandle.get() != INVALID_HANDLE || proxyHandle.get() != INVALID_HANDLE

    private companion object {
        const val TAG = "RealWarpSdkBridge"
        const val INVALID_HANDLE = -1
        const val AWG_TEARDOWN_COOLDOWN_MS = 300L

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

    fun startProxy(
        name: String,
        ini: String,
        uapiPath: String,
        port: Int,
        protector: VpnSocketProtector,
    ): Int = error("proxy mode is not supported")

    fun stopProxy() = Unit

    fun resetProxyGlobals() = Unit

    fun close() = Unit

    fun version(): String = "unknown"

    @Deprecated("awgGetConfig causes SIGSEGV on partial-handshake handle, use only post-disconnect")
    fun getConfig(handle: Int): String? = null

    fun turnOnAndGetSockets(name: String, tunFd: Int, ini: String, uapiPath: String): AwgTurnOnResult {
        val handle = turnOn(name, tunFd, ini, uapiPath)
        // amnezia AWG: errors → -1, 0 = валидный первый slot. Не менять на `<= 0`.
        if (handle < 0) return AwgTurnOnResult(handle, -1, -1)
        val v4 = runCatching { getSocketV4(handle) }.getOrDefault(-1)
        val v6 = runCatching { getSocketV6(handle) }.getOrDefault(-1)
        return AwgTurnOnResult(handle, v4, v6)
    }
}

data class AwgTurnOnResult(val handle: Int, val socketV4Fd: Int, val socketV6Fd: Int)
