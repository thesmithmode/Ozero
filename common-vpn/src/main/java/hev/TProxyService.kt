package hev

import android.util.Log
import ru.ozero.coreapi.PersistentLoggers

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

    init {
        Log.i(TAG, "loadLibrary begin")
        runCatching { PersistentLoggers.instance?.info(TAG, "loadLibrary begin") }
        try {
            System.loadLibrary("hev-socks5-tunnel")
            libraryLoaded = true
            Log.i(TAG, "libhev-socks5-tunnel loaded OK")
            runCatching { PersistentLoggers.instance?.info(TAG, "libhev-socks5-tunnel loaded OK") }
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "libhev-socks5-tunnel load FAILED: $loadError")
            runCatching {
                PersistentLoggers.instance?.error(TAG, "libhev-socks5-tunnel load FAILED: $loadError", e)
            }
        } catch (e: SecurityException) {
            loadError = e.message ?: e.javaClass.simpleName
            Log.e(TAG, "libhev-socks5-tunnel load denied: $loadError")
            runCatching {
                PersistentLoggers.instance?.error(TAG, "libhev-socks5-tunnel load denied: $loadError", e)
            }
        }
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()
}
