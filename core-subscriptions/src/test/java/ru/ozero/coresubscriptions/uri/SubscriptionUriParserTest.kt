package ru.ozero.coresubscriptions.uri

import org.junit.jupiter.api.Test
import kotlin.test.assertIs

class SubscriptionUriParserTest {
    private val parser = SubscriptionUriParser()

    @Test
    fun dispatchesVless() {
        val result = parser.parse("vless://UUID@host:443?encryption=none&security=none")
        assertIs<ParsedServer.Vless>(result)
    }

    @Test
    fun dispatchesHysteria2() {
        val result = parser.parse("hysteria2://pass@host:443")
        assertIs<ParsedServer.Hysteria2>(result)
    }

    @Test
    fun dispatchesHy2Alias() {
        val result = parser.parse("hy2://pass@host:443")
        assertIs<ParsedServer.Hysteria2>(result)
    }

    @Test
    fun dispatchesTrojan() {
        val result = parser.parse("trojan://pass@host:443")
        assertIs<ParsedServer.Trojan>(result)
    }

    @Test
    fun dispatchesShadowsocks() {
        val result = parser.parse("ss://aes-256-gcm:pass@host:8388")
        assertIs<ParsedServer.Shadowsocks>(result)
    }

    @Test
    fun unknownSchemeReturnsError() {
        val result = parser.parse("unknown://whatever")
        assertIs<ParsedServer.Error>(result)
    }

    @Test
    fun malformedVlessReturnsError() {
        val result = parser.parse("vless://broken")
        assertIs<ParsedServer.Error>(result)
    }
}
