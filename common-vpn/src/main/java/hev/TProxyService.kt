package hev

import android.os.Build
import android.os.Looper
import ru.ozero.enginescore.PersistentLoggers

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
object TProxyService {

    private const val TAG = "TProxyService"

    @JvmStatic
    @Volatile
    var libraryLoaded: Boolean = false
        private set

    @JvmStatic
    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    private var loadAttempted: Boolean = false
    private val loadLock = Any()

    @JvmStatic
    fun loadOnce() {
        if (loadAttempted) return
        synchronized(loadLock) {
            if (loadAttempted) return
            val t0 = System.nanoTime()
            val thread = Thread.currentThread()
            val isMain = Looper.myLooper() === Looper.getMainLooper()
            val ctx = "thread=${thread.name} tid=${thread.id} main=$isMain " +
                "device=${Build.MANUFACTURER}/${Build.BRAND}/${Build.MODEL} " +
                "sdk=${Build.VERSION.SDK_INT}"
            PersistentLoggers.instance?.info(TAG, "loadLibrary begin $ctx")
            try {
                System.loadLibrary("hev-ozero-socks5-tunnel")
                libraryLoaded = true
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.info(TAG, "libhev-ozero-socks5-tunnel loaded OK dt=${dtMs}ms")
            } catch (e: UnsatisfiedLinkError) {
                loadError = e.message ?: e.javaClass.simpleName
                libraryLoaded = false
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.error(
                    TAG,
                    "libhev-ozero-socks5-tunnel load FAILED dt=${dtMs}ms: $loadError",
                    e,
                )
                runCatching { dumpVendorMaps() }
            } catch (e: SecurityException) {
                loadError = e.message ?: e.javaClass.simpleName
                libraryLoaded = false
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.error(
                    TAG,
                    "libhev-ozero-socks5-tunnel load denied dt=${dtMs}ms: $loadError",
                    e,
                )
                runCatching { dumpVendorMaps() }
            } catch (e: Throwable) {
                loadError = "${e.javaClass.simpleName}: ${e.message ?: "no message"}"
                libraryLoaded = false
                val dtMs = (System.nanoTime() - t0) / 1_000_000
                PersistentLoggers.instance?.error(
                    TAG,
                    "libhev-ozero-socks5-tunnel load THROWN dt=${dtMs}ms: $loadError",
                    e,
                )
                runCatching { dumpVendorMaps() }
            } finally {
                loadAttempted = true
            }
        }
    }

    private fun dumpVendorMaps() {
        val keywords = listOf("nubia", "glnubia", "perf", "tencent", "game", "booster", "libgl")
        val matches = mutableListOf<String>()
        java.io.File("/proc/self/maps").bufferedReader().useLines { seq ->
            for (line in seq) {
                if (matches.size >= MAX_MAPS_LINES) break
                val lower = line.lowercase()
                if (keywords.any { it in lower }) matches.add(redactProcMapsLine(line))
            }
        }
        val payload = if (matches.isEmpty()) {
            "proc/maps vendor: none"
        } else {
            "proc/maps vendor:\n${matches.joinToString("\n")}"
        }
        PersistentLoggers.instance?.info(TAG, payload)
    }

    private fun redactProcMapsLine(line: String): String {
        val fields = line.trim().split(Regex("\\s+"), limit = 6)
        return fields.getOrNull(5)?.substringAfterLast('/') ?: "[anonymous]"
    }

    private const val MAX_MAPS_LINES = 30

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()

    @JvmStatic
    external fun TProxyGetStats(): LongArray
}
