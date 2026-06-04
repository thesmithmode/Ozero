package ru.ozero.commonvpn

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertTrue

class NativeHevTunnelGatewayExtraTest {

    private val loader = object : TProxyLoader {
        override fun loadOnce() = Unit
        override val libraryLoaded: Boolean = true
        override val loadError: String? = null
    }

    @Test
    fun `stop before start is a no-op and does not call nativeStop`(@TempDir tmp: File) {
        var stopCalled = false
        val gateway = NativeHevTunnelGateway(
            cacheDir = tmp,
            loader = loader,
            nativeStart = { _, _ -> 0 },
            nativeStop = { stopCalled = true },
        )

        gateway.stop()

        assertTrue(!stopCalled)
    }
}
