package ru.ozero.enginewarp

import android.content.Context
import com.getkeepsafe.relinker.ReLinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.amnezia.awg.GoBackend
import ru.ozero.enginescore.PersistentLoggers
import ru.ozero.enginescore.VpnSocketProtector
import java.io.File

class RealWarpSdkBridge internal constructor(
    private val awgRuntime: AwgRuntime,
) : WarpSdkBridge {

    constructor(context: Context) : this(ReLinkerAwgRuntime(context))

    @Volatile
    private var tunnelHandle: Int = INVALID_HANDLE

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
        val socketFile = File(uapiPath, "$tunnelName.sock")
        if (socketFile.exists()) {
            val deleted = socketFile.delete()
            PersistentLoggers.info(TAG, "stale socket $socketFile deleted=$deleted")
        }
        logIniDigest(tunnelName, iniConfig)
        try {
            val handle = awgRuntime.turnOn(tunnelName, tunFd, iniConfig, uapiPath)
            if (handle < 0) {
                return@withContext WarpSdkBridge.AttachResult.Failed("awgTurnOn handle=$handle")
            }
            tunnelHandle = handle
            val protectOk = protectUnderlyingSockets(handle, protector)
            if (!protectOk) {
                PersistentLoggers.error(TAG, "protect failed — rolling back to avoid routing loop")
                runCatching { awgRuntime.turnOff(handle) }
                tunnelHandle = INVALID_HANDLE
                return@withContext WarpSdkBridge.AttachResult.Failed("protect underlying sockets failed")
            }
            PersistentLoggers.info(TAG, "awgTurnOn OK handle=$handle name=$tunnelName")
            WarpSdkBridge.AttachResult.Success
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            PersistentLoggers.error(TAG, "awgTurnOn threw: $msg (${t.javaClass.name})")
            WarpSdkBridge.AttachResult.Failed("awgTurnOn failed: $msg")
        }
    }

    private fun protectUnderlyingSockets(handle: Int, protector: VpnSocketProtector): Boolean {
        val v4 = runCatching { awgRuntime.getSocketV4(handle) }.getOrElse {
            PersistentLoggers.warn(TAG, "awgGetSocketV4 threw: ${it.message}")
            -1
        }
        val v6 = runCatching { awgRuntime.getSocketV6(handle) }.getOrElse {
            PersistentLoggers.warn(TAG, "awgGetSocketV6 threw: ${it.message}")
            -1
        }
        if (v4 <= 0 && v6 <= 0) {
            PersistentLoggers.error(TAG, "no underlying sockets available v4=$v4 v6=$v6")
            return false
        }
        var anyProtected = false
        if (v4 > 0) {
            val ok = protector.protect(v4)
            PersistentLoggers.info(TAG, "protect v4 sock=$v4 ok=$ok")
            if (!ok) {
                PersistentLoggers.error(TAG, "protect v4 returned false — VpnService binding lost")
                return false
            }
            anyProtected = true
        }
        if (v6 > 0) {
            val ok = protector.protect(v6)
            PersistentLoggers.info(TAG, "protect v6 sock=$v6 ok=$ok")
            if (!ok) {
                PersistentLoggers.warn(TAG, "protect v6 returned false — continuing v4-only")
            } else {
                anyProtected = true
            }
        }
        return anyProtected
    }

    override suspend fun detachTun() {
        withContext(Dispatchers.IO) {
            val h = tunnelHandle
            if (h == INVALID_HANDLE) return@withContext
            try {
                awgRuntime.turnOff(h)
                PersistentLoggers.info(TAG, "awgTurnOff OK handle=$h")
            } catch (t: Throwable) {
                PersistentLoggers.error(TAG, "awgTurnOff failed: ${t.message}")
            } finally {
                tunnelHandle = INVALID_HANDLE
            }
        }
    }

    override fun isRunning(): Boolean = tunnelHandle != INVALID_HANDLE

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
            PersistentLoggers.info(
                TAG,
                "INI digest ($tunnelName): bytes=$totalBytes lines=${lines.size} nonEmpty=$nonEmpty keys=$keys",
            )
            PersistentLoggers.info(TAG, "INI passthrough ($tunnelName):\n${sanitizeIni(ini)}")
        }

        fun sanitizeIni(ini: String): String = ini.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trimStart()
            when {
                trimmed.startsWith("PrivateKey", ignoreCase = true) -> "PrivateKey = ***"
                trimmed.startsWith("PresharedKey", ignoreCase = true) -> "PresharedKey = ***"
                else -> line
            }
        }
    }
}

interface AwgRuntime {
    fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int
    fun turnOff(handle: Int)
    fun getSocketV4(handle: Int): Int
    fun getSocketV6(handle: Int): Int
    fun getConfig(handle: Int): String? = null
}

class ReLinkerAwgRuntime(context: Context) : AwgRuntime {
    private val appContext = context.applicationContext

    @Volatile private var loaded = false
    private val lock = Any()

    private fun loadOnce() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            try {
                ReLinker.loadLibrary(appContext, LIB_NAME)
                loaded = true
                PersistentLoggers.info(TAG, "ReLinker loaded $LIB_NAME")
            } catch (e: Throwable) {
                PersistentLoggers.error(TAG, "ReLinker.loadLibrary $LIB_NAME failed: ${e.message}")
                throw e
            }
        }
    }

    override fun turnOn(name: String, tunFd: Int, ini: String, uapiPath: String): Int {
        loadOnce()
        val result = GoBackend.awgTurnOn(name, tunFd, ini, uapiPath)
        PersistentLoggers.info(TAG, "awgTurnOn name=$name fd=$tunFd version=${GoBackend.awgVersion()} → handle=$result")
        return result
    }

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

object AwgRuntimePreloader {
    fun preload(context: Context) {
        runCatching { ReLinkerAwgRuntime(context).preload() }
    }
}
