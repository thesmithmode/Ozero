package ru.ozero.app.ui.settings.engines.singbox

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.ShadowsocksBean
import ru.ozero.singboxfmt.TrojanBean
import ru.ozero.singboxfmt.VLESSBean
import ru.ozero.singboxfmt.VMessBean
import ru.ozero.singboxfmt.protocolLabel
import kotlin.test.assertEquals

class SingboxProbeProtocolLabelTest {
    @Test
    fun `uses shared protocol labels`() {
        assertEquals("VLESS", VLESSBean().protocolLabel())
        assertEquals("VMESS", VMessBean().protocolLabel())
        assertEquals("TROJAN", TrojanBean().protocolLabel())
        assertEquals("SHADOWSOCKS", ShadowsocksBean().protocolLabel())
    }
}
