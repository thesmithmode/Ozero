package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeanAccessorCoverageTest {

    @Test
    fun `standard v2ray bean compatibility accessors round trip`() {
        val bean = VLESSBean()

        bean.grpcServiceNameCompat = true
        bean.grpcMultiMode = true
        bean.wsUseBrowserForwarder = true
        bean.shUseBrowserForwarder = true
        bean.splithttpExtra = "x-padding=1"
        bean.meekUrl = "https://front.example/path"
        bean.mekyaKcpSeed = "seed"
        bean.mekyaKcpHeaderType = "srtp"
        bean.mekyaUrl = "https://mekya.example"

        assertTrue(bean.grpcServiceNameCompat)
        assertTrue(bean.grpcMultiMode)
        assertTrue(bean.wsUseBrowserForwarder)
        assertTrue(bean.shUseBrowserForwarder)
        assertEquals("x-padding=1", bean.splithttpExtra)
        assertEquals("https://front.example/path", bean.meekUrl)
        assertEquals("seed", bean.mekyaKcpSeed)
        assertEquals("srtp", bean.mekyaKcpHeaderType)
        assertEquals("https://mekya.example", bean.mekyaUrl)
    }

    @Test
    fun `shadowsocks plugin opts accessor round trips and defaults initialize`() {
        val bean = ShadowsocksBean()
        bean.method = ""
        bean.pluginOpts = "mode=websocket;host=edge.example"

        bean.initializeDefaultValues()

        assertEquals("aes-128-gcm", bean.method)
        assertEquals("mode=websocket;host=edge.example", bean.pluginOpts)
        assertFalse(bean.pluginOpts.isBlank())
    }
}
