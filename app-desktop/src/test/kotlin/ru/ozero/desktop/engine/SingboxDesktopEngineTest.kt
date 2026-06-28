package ru.ozero.desktop.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class SingboxDesktopEngineTest {

    private val engine = SingboxDesktopEngine()

    @Test
    fun `should have SINGBOX id`() {
        assertEquals(ru.ozero.desktop.model.EngineId.SINGBOX, engine.id)
    }

    @Test
    fun `should report available on platform`() {
        assertTrue(engine.isAvailableOnPlatform)
    }

    @Test
    fun `should not be running initially`() {
        assertFalse(engine.isRunning())
    }

    @Test
    fun `should return zero port initially`() {
        assertEquals(0, engine.listeningPort())
    }

    @Nested
    inner class ProxyConfig {

        @Test
        fun `should contain mixed inbound`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.contains(""""type":"mixed""""))
            assertTrue(config.contains(""""tag":"mixed-in""""))
        }

        @Test
        fun `should use default port 7890`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.contains(""""listen_port":7890"""))
        }

        @ParameterizedTest
        @ValueSource(ints = [1080, 8080, 9999])
        fun `should use specified port`(port: Int) {
            val config = SingboxDesktopEngine.buildProxyConfig(port = port)
            assertTrue(config.contains(""""listen_port":$port"""))
        }

        @Test
        fun `should listen on localhost`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.contains(""""listen":"127.0.0.1""""))
        }

        @Test
        fun `should not include direct dns resolver`() {
            val config = SingboxDesktopEngine.buildProxyConfig(dnsServer = "8.8.8.8")
            assertFalse(config.contains(""""server":"8.8.8.8""""))
        }

        @Test
        fun `should not contain direct outbound`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertFalse(config.contains(""""type":"direct""""))
        }

        @Test
        fun `should contain block outbound`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.contains(""""type":"block""""))
        }

        @Test
        fun `should not contain dns outbound`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertFalse(config.contains(""""type":"dns""""))
        }

        @Test
        fun `should have auto_detect_interface`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.contains(""""auto_detect_interface":true"""))
        }

        @Test
        fun `should produce valid json structure`() {
            val config = SingboxDesktopEngine.buildProxyConfig()
            assertTrue(config.startsWith("{"))
            assertTrue(config.endsWith("}"))
            assertEquals(config.count { it == '{' }, config.count { it == '}' })
        }
    }

    @Nested
    inner class FullProxyConfig {

        @Test
        fun `should include proxy outbound`() {
            val proxyOutbound = """{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":1080}"""
            val config = SingboxDesktopEngine.buildFullProxyConfig(proxyOutbound)
            assertTrue(config.contains(proxyOutbound))
        }

        @Test
        fun `should route final to proxy`() {
            val proxyOutbound = """{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":1080}"""
            val config = SingboxDesktopEngine.buildFullProxyConfig(proxyOutbound)
            assertTrue(config.contains(""""final":"proxy""""))
        }

        @Test
        fun `should use custom port`() {
            val proxyOutbound = """{"type":"direct","tag":"proxy"}"""
            val config = SingboxDesktopEngine.buildFullProxyConfig(proxyOutbound, port = 5555)
            assertTrue(config.contains(""""listen_port":5555"""))
        }
    }

    @Nested
    inner class TunConfig {

        @Test
        fun `should contain tun inbound`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""type":"tun""""))
        }

        @Test
        fun `should use ozero-tun interface`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""interface_name":"ozero-tun""""))
        }

        @Test
        fun `should configure ipv4 address`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""inet4_address":"172.19.0.1/30""""))
        }

        @Test
        fun `should enable auto_route`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""auto_route":true"""))
        }

        @Test
        fun `should enable strict_route`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""strict_route":false"""))
        }

        @Test
        fun `should use gvisor stack`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""stack":"gvisor""""))
        }

        @Test
        fun `should enable sniff`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""sniff":true"""))
            assertTrue(config.contains(""""sniff_override_destination":true"""))
        }

        @Test
        fun `should route final to block`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertTrue(config.contains(""""final":"block""""))
        }

        @Test
        fun `should not include direct dns resolver`() {
            val config = SingboxDesktopEngine.buildTunConfig(dnsServer = "9.9.9.9")
            assertFalse(config.contains(""""server":"9.9.9.9""""))
        }

        @Test
        fun `should produce balanced json`() {
            val config = SingboxDesktopEngine.buildTunConfig()
            assertEquals(config.count { it == '{' }, config.count { it == '}' })
            assertEquals(config.count { it == '[' }, config.count { it == ']' })
        }
    }

    @Nested
    inner class TunForwardConfig {

        @Test
        fun `should contain tun inbound`() {
            val config = SingboxDesktopEngine.buildTunForwardConfig(1080)
            assertTrue(config.contains(""""type":"tun""""))
        }

        @Test
        fun `should contain socks outbound to specified port`() {
            val config = SingboxDesktopEngine.buildTunForwardConfig(1080)
            assertTrue(config.contains(""""type":"socks""""))
            assertTrue(config.contains(""""server_port":1080"""))
        }

        @ParameterizedTest
        @ValueSource(ints = [1080, 8080, 3128])
        fun `should forward to different ports`(port: Int) {
            val config = SingboxDesktopEngine.buildTunForwardConfig(port)
            assertTrue(config.contains(""""server_port":$port"""))
        }

        @Test
        fun `should connect to localhost`() {
            val config = SingboxDesktopEngine.buildTunForwardConfig(1080)
            assertTrue(config.contains(""""server":"127.0.0.1""""))
        }

        @Test
        fun `should route final to proxy`() {
            val config = SingboxDesktopEngine.buildTunForwardConfig(1080)
            assertTrue(config.contains(""""final":"proxy""""))
        }

        @Test
        fun `should produce balanced json`() {
            val config = SingboxDesktopEngine.buildTunForwardConfig(1080)
            assertEquals(config.count { it == '{' }, config.count { it == '}' })
            assertEquals(config.count { it == '[' }, config.count { it == ']' })
        }
    }

    @Nested
    inner class FullTunConfig {

        @Test
        fun `should contain tun inbound`() {
            val proxyOutbound = """{"type":"vless","tag":"proxy","server":"example.com","server_port":443}"""
            val config = SingboxDesktopEngine.buildFullTunConfig(proxyOutbound)
            assertTrue(config.contains(""""type":"tun""""))
        }

        @Test
        fun `should include custom outbound`() {
            val proxyOutbound = """{"type":"vless","tag":"proxy","server":"example.com","server_port":443}"""
            val config = SingboxDesktopEngine.buildFullTunConfig(proxyOutbound)
            assertTrue(config.contains(proxyOutbound))
        }

        @Test
        fun `should route final to proxy`() {
            val proxyOutbound = """{"type":"vless","tag":"proxy","server":"example.com","server_port":443}"""
            val config = SingboxDesktopEngine.buildFullTunConfig(proxyOutbound)
            assertTrue(config.contains(""""final":"proxy""""))
        }
    }

    @Nested
    inner class ProxyOutboundValidation {

        @Test
        fun `should reject blocked proxy config`() {
            assertFalse(SingboxDesktopEngine.hasProxyOutbound(SingboxDesktopEngine.buildProxyConfig()))
        }

        @Test
        fun `should reject blocked tun config`() {
            assertFalse(SingboxDesktopEngine.hasProxyOutbound(SingboxDesktopEngine.buildTunConfig()))
        }

        @Test
        fun `should accept full proxy config`() {
            val proxyOutbound = """{"type":"vless","tag":"proxy","server":"example.com","server_port":443}"""
            val config = SingboxDesktopEngine.buildFullProxyConfig(proxyOutbound)
            assertTrue(SingboxDesktopEngine.hasProxyOutbound(config))
        }
    }

    @Nested
    inner class ExtractPort {

        @Test
        fun `should return config port when set`() {
            val config = EngineConfig(socksPort = 5555)
            assertEquals(5555, engine.extractPort(config))
        }

        @Test
        fun `should return default port when not set`() {
            val config = EngineConfig()
            assertEquals(SingboxDesktopEngine.DEFAULT_MIXED_PORT, engine.extractPort(config))
        }
    }

    @Nested
    inner class DetectReady {

        @Test
        fun `should detect started keyword`() {
            assertTrue(engine.detectReady("sing-box started"))
        }

        @Test
        fun `should detect inbound mixed keyword`() {
            assertTrue(engine.detectReady("inbound/mixed started"))
        }

        @Test
        fun `should not detect random output`() {
            assertFalse(engine.detectReady("loading config..."))
        }
    }
}
