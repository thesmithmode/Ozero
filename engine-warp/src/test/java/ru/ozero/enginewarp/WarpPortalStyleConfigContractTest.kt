package ru.ozero.enginewarp

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WarpPortalStyleConfigContractTest {

    @Test
    fun `PORTAL-style WARP config preserves AWG blob full-tunnel addresses and DNS`() {
        val awgBlob = listOf(
            "c70000000108ce1bf31eec7d93360000449e227e4596ed7f75c4d35ce31880b4133107c822c6355b51f0",
            "d7c1bba96d5c210a48aca01885fed0871cfc37d59137d73b506dc013bb4a13c060ca5b04b7ae215af71e37",
            "d6e8ff1db235f9fe0c25cb8b492471054a7c8d0d6077d430d07f6e87a8699287f6e69f54263c7334a8e",
            "144a29851429bf2e350e519445172d36953e96085110ce1fb641e5efad42c0feb4711ece959b72cc4d6f3",
        ).joinToString("")
        val conf = """
            [Interface]
            PrivateKey = xmPeOeSIU2UTjYFCSzw5Gc+Ks1uxZhanU6iQZKAyFpQ=
            Address = 172.16.0.2, 2606:4700:110:8254:760a:60f9:8032:5e3b
            DNS = 176.99.11.77, 80.78.247.254, 2a00:f940:2:4:2::5d1b, 2a00:f940:2:4:2::21ed
            MTU = 1280
            S1 = 0
            S2 = 0
            Jc = 5
            Jmin = 100
            Jmax = 200
            H1 = 1
            H2 = 2
            H3 = 3
            H4 = 4
            I1 = <b 0x$awgBlob>

            [Peer]
            PublicKey = bmXOC+F1FxEMF9dyiK2H5/1SUtzH0JuVo51h2wPfgyo=
            AllowedIPs = 0.0.0.0/0, ::/0
            Endpoint = engage.cloudflareclient.com:4500
            PersistentKeepalive = 25
        """.trimIndent()

        val cfg = WarpConfParser.parse(conf).getOrThrow()
        val rebuilt = WarpIniBuilder.build(cfg)

        assertEquals("172.16.0.2/32", cfg.interfaceAddressV4)
        assertEquals("2606:4700:110:8254:760a:60f9:8032:5e3b/128", cfg.interfaceAddressV6)
        assertEquals(
            listOf("176.99.11.77", "80.78.247.254", "2a00:f940:2:4:2::5d1b", "2a00:f940:2:4:2::21ed"),
            cfg.dnsServers,
        )
        assertEquals("engage.cloudflareclient.com:4500", cfg.peerEndpoint)
        assertEquals(awgBlob, cfg.awgParams.payloadHexI1)
        assertTrue(rebuilt.contains("AllowedIPs = 0.0.0.0/0, ::/0"))
        assertTrue(rebuilt.contains("I1 = <b 0x$awgBlob>"))
    }
}
