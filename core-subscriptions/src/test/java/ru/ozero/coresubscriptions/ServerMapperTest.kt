package ru.ozero.coresubscriptions

import org.junit.jupiter.api.Test
import ru.ozero.coresubscriptions.uri.Hysteria2Server
import ru.ozero.coresubscriptions.uri.ParsedServer
import ru.ozero.coresubscriptions.uri.ShadowsocksServer
import ru.ozero.coresubscriptions.uri.TrojanServer
import ru.ozero.coresubscriptions.uri.VlessServer
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerMapperTest {
    private val mapper = ServerMapper()

    @Test
    fun mapsVlessToEntity() {
        val uri = "vless://UUID@host.io:443?security=reality"
        val parsed =
            ParsedServer.Vless(
                VlessServer(uuid = "UUID", host = "host.io", port = 443, security = "reality"),
            )
        val entity = mapper.toEntity(parsed, originalUri = uri)!!
        assertEquals("vless", entity.protocol)
        assertEquals(443, entity.port)
        assertEquals(uri, entity.uri)
    }

    @Test
    fun mapsHysteria2ToEntity() {
        val uri = "hysteria2://pass@host:443"
        val parsed = ParsedServer.Hysteria2(Hysteria2Server("pass", "host", 443))
        val entity = mapper.toEntity(parsed, originalUri = uri)!!
        assertEquals("hysteria2", entity.protocol)
        assertEquals(443, entity.port)
    }

    @Test
    fun mapsTrojanToEntity() {
        val uri = "trojan://pass@host:443"
        val parsed = ParsedServer.Trojan(TrojanServer("pass", "host", 443))
        val entity = mapper.toEntity(parsed, originalUri = uri)!!
        assertEquals("trojan", entity.protocol)
    }

    @Test
    fun mapsShadowsocksToEntity() {
        val uri = "ss://aes:p@host:8388"
        val parsed = ParsedServer.Shadowsocks(ShadowsocksServer("aes", "p", "host", 8388))
        val entity = mapper.toEntity(parsed, originalUri = uri)!!
        assertEquals("shadowsocks", entity.protocol)
        assertEquals(8388, entity.port)
    }

    @Test
    fun idIsStableForSameUri() {
        val uri = "vless://UUID@host.io:443?security=reality"
        val parsed = ParsedServer.Vless(VlessServer("UUID", "host.io", 443, security = "reality"))
        val a = mapper.toEntity(parsed, uri)!!
        val b = mapper.toEntity(parsed, uri)!!
        assertEquals(a.id, b.id)
    }

    @Test
    fun errorReturnsNull() {
        assertNull(mapper.toEntity(ParsedServer.Error("bad"), "uri"))
    }
}
