package ru.ozero.singboxfmt

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BeanLabelsTest {
    @Test
    fun `protocolLabel returns stable labels for known beans`() {
        assertEquals("VLESS", VLESSBean().protocolLabel())
        assertEquals("VMESS", VMessBean().protocolLabel())
        assertEquals("TROJAN", TrojanBean().protocolLabel())
        assertEquals("SHADOWSOCKS", ShadowsocksBean().protocolLabel())
    }
}
