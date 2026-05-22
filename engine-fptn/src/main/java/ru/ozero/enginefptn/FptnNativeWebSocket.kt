package ru.ozero.enginefptn

import androidx.annotation.Keep

class FptnNativeWebSocket {

    var onOpen: () -> Unit = {}
    var onMessage: (ByteArray) -> Unit = {}
    var onFailure: () -> Unit = {}

    @Keep
    fun onOpenImpl() {
        onOpen()
    }

    @Keep
    fun onMessageImpl(data: ByteArray) {
        onMessage(data)
    }

    @Keep
    fun onFailureImpl() {
        onFailure()
    }

    external fun nativeCreate(
        serverIp: String,
        serverPort: Int,
        tunIpv4: String,
        tunIpv6: String,
        sni: String,
        accessToken: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    external fun nativeDestroy(handle: Long)

    external fun nativeRun(handle: Long): Boolean

    external fun nativeStop(handle: Long): Boolean

    external fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean

    external fun nativeIsStarted(handle: Long): Boolean

    companion object {
        private var loadAttempted = false
        private val loadLock = Any()
        var libraryLoaded = false
            private set
        var loadError: String? = null
            private set

        fun loadOnce() {
            if (loadAttempted) return
            synchronized(loadLock) {
                if (loadAttempted) return
                loadAttempted = true
                try {
                    System.loadLibrary("fptn_native_lib")
                    libraryLoaded = true
                } catch (e: UnsatisfiedLinkError) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                } catch (e: Throwable) {
                    loadError = e.message ?: e.javaClass.simpleName
                    libraryLoaded = false
                }
            }
        }
    }
}
