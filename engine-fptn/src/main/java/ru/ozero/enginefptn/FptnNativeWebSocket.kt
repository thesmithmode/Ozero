package ru.ozero.enginefptn

import androidx.annotation.Keep

interface FptnWebSocketClient {
    var onOpen: () -> Unit
    var onMessage: (ByteArray) -> Unit
    var onFailure: () -> Unit
    var onSocketOpened: (Int) -> Unit

    fun loadOnce()
    val libraryLoaded: Boolean
    val loadError: String?

    fun nativeCreate(
        serverIp: String,
        serverPort: Int,
        tunIpv4: String,
        tunIpv6: String,
        sni: String,
        accessToken: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long

    fun nativeDestroy(handle: Long)
    fun nativeRun(handle: Long): Boolean
    fun nativeStop(handle: Long): Boolean
    fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean
    fun nativeIsStarted(handle: Long): Boolean
}

class FptnNativeWebSocket : FptnWebSocketClient {

    override var onOpen: () -> Unit = {}
    override var onMessage: (ByteArray) -> Unit = {}
    override var onFailure: () -> Unit = {}
    override var onSocketOpened: (Int) -> Unit = {}
    override val libraryLoaded: Boolean
        get() = Companion.libraryLoaded
    override val loadError: String?
        get() = Companion.loadError
    override fun loadOnce() = Companion.loadOnce()

    @Keep
    fun onOpenImpl() {
        onOpen()
    }

    @Keep
    fun onMessageImpl(packets: Array<ByteArray>) {
        packets.forEach(onMessage)
    }

    @Keep
    fun onFailureImpl() {
        onFailure()
    }

    @Keep
    fun onSocketOpenedImpl(socketFd: Int) {
        onSocketOpened(socketFd)
    }

    override fun nativeCreate(
        serverIp: String,
        serverPort: Int,
        tunIpv4: String,
        tunIpv6: String,
        sni: String,
        accessToken: String,
        md5Fingerprint: String,
        censorshipStrategy: String,
    ): Long = nativeCreateV2(
        serverIp = serverIp,
        serverPort = serverPort,
        tunIpv4 = tunIpv4,
        tunIpv6 = tunIpv6,
        sni = sni,
        accessToken = accessToken,
        md5Fingerprint = md5Fingerprint,
        clientVersion = "Ozero",
        censorshipStrategy = censorshipStrategy,
        connectionStrategy = "rolling-tunnel",
    )

    private external fun nativeCreateV2(
        serverIp: String,
        serverPort: Int,
        tunIpv4: String,
        tunIpv6: String,
        sni: String,
        accessToken: String,
        md5Fingerprint: String,
        clientVersion: String,
        censorshipStrategy: String,
        connectionStrategy: String,
    ): Long

    external override fun nativeDestroy(handle: Long)

    external override fun nativeRun(handle: Long): Boolean

    external override fun nativeStop(handle: Long): Boolean

    external override fun nativeSend(handle: Long, data: ByteArray, length: Long): Boolean

    external override fun nativeIsStarted(handle: Long): Boolean

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
