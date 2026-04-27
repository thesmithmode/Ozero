package hev

import android.util.Log

@Suppress("ktlint:standard:function-naming", "FunctionNaming")
object TProxyService {

    @JvmStatic
    @Volatile
    var libraryLoaded: Boolean = false
        private set

    @JvmStatic
    @Volatile
    var loadError: String? = null
        private set

    init {
        try {
            System.loadLibrary("hev-socks5-tunnel")
            libraryLoaded = true
            Log.i("TProxyService", "libhev-socks5-tunnel loaded OK")
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message ?: e.javaClass.simpleName
            Log.e("TProxyService", "libhev-socks5-tunnel load FAILED: $loadError")
        } catch (e: SecurityException) {
            loadError = e.message ?: e.javaClass.simpleName
            Log.e("TProxyService", "libhev-socks5-tunnel load denied: $loadError")
        }
    }

    @JvmStatic
    external fun TProxyStartService(configPath: String, fd: Int)

    @JvmStatic
    external fun TProxyStopService()
}
