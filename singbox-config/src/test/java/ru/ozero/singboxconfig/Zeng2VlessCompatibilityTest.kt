package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.V2RayFmt
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Zeng2VlessCompatibilityTest {
    private val validRealityPublicKey = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA"
    private val uuid = "12345678-1234-1234-1234-123456789abc"

    @Test
    fun `zeng2 raw transport is accepted as tcp`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://$uuid@203.0.113.10:4100?encryption=none&security=none&type=raw#Raw",
        )

        assertEquals(BeanSupportDecision.Supported, ConfigBuilder.supportDecision(bean))
        val json = ConfigBuilder.buildSingboxConfig(bean)
        assertContains(json, "\"type\":\"vless\"")
    }

    @Test
    fun `zeng2 websocket alias is accepted as ws`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://$uuid@203.0.113.10:443?encryption=none&host=front.example.com" +
                "&path=%2Fv1&security=tls&sni=front.example.com&type=websocket#WebSocket",
        )

        assertEquals(BeanSupportDecision.Supported, ConfigBuilder.supportDecision(bean))
        val json = ConfigBuilder.buildSingboxConfig(bean)
        assertContains(json, "\"transport\":{\"type\":\"ws\"")
    }

    @Test
    fun `zeng2 reality without fingerprint defaults to chrome utls`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://$uuid@203.0.113.10:443?encryption=none&pbk=$validRealityPublicKey" +
                "&security=reality&sid=23103e1e&sni=front.example.com&type=raw#Reality",
        )

        assertEquals(BeanSupportDecision.Supported, ConfigBuilder.supportDecision(bean))
        val json = ConfigBuilder.buildSingboxConfig(bean)
        assertContains(json, "\"reality\":{\"enabled\":true")
        assertContains(json, "\"utls\":{\"enabled\":true,\"fingerprint\":\"chrome\"}")
    }

    @Test
    fun `zeng2 grpc reality preserves supplied fingerprint`() {
        val bean = V2RayFmt.parseVLESS(
            "vless://$uuid@203.0.113.10:9001?encryption=none&fp=qq&mode=gun" +
                "&pbk=$validRealityPublicKey&security=reality&serviceName=grpc-reality" +
                "&sid=6ba85179e30d4fc2&sni=front.example.com&type=grpc#Grpc",
        )

        assertEquals(BeanSupportDecision.Supported, ConfigBuilder.supportDecision(bean))
        val json = ConfigBuilder.buildSingboxConfig(bean)
        assertContains(json, "\"transport\":{\"type\":\"grpc\"")
        assertContains(json, "\"fingerprint\":\"qq\"")
    }
}
