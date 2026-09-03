package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ConfigBuilderProbeTest {

    @Test
    fun `probe config maps every socks inbound to its own outbound`() {
        val json = ConfigBuilder.buildProbeConfig(
            listOf(
                ConfigBuilder.ProbeTarget(bean("first.example"), 20_001),
                ConfigBuilder.ProbeTarget(bean("second.example"), 20_002),
            ),
        )

        assertContains(json, "\"tag\":\"probe-in-0\"")
        assertContains(json, "\"listen_port\":20001")
        assertContains(json, "\"tag\":\"probe-in-1\"")
        assertContains(json, "\"listen_port\":20002")
        assertContains(json, "\"tag\":\"probe-out-0\"")
        assertContains(json, "\"tag\":\"probe-out-1\"")
        assertContains(json, "\"inbound\":[\"probe-in-0\"],\"outbound\":\"probe-out-0\"")
        assertContains(json, "\"inbound\":[\"probe-in-1\"],\"outbound\":\"probe-out-1\"")
        assertContains(json, "\"final\":\"block\"")
        assertContains(json, "\"log\":{\"level\":\"debug\"")
        assertFalse(json.contains("\"type\":\"tun\""))
    }

    @Test
    fun `probe config supports large batch and rejects empty and duplicate ports`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProbeConfig(emptyList())
        }

        ConfigBuilder.buildProbeConfig(
            (0 until 200).map { index ->
                ConfigBuilder.ProbeTarget(bean("server-$index.example"), 21_000 + index)
            },
        )

        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProbeConfig(
                listOf(
                    ConfigBuilder.ProbeTarget(bean("first.example"), 22_000),
                    ConfigBuilder.ProbeTarget(bean("second.example"), 22_000),
                ),
            )
        }
    }

    @Test
    fun `probe config rejects invalid port and unsupported target`() {
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProbeConfig(
                listOf(ConfigBuilder.ProbeTarget(bean("invalid-port.example"), 0)),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConfigBuilder.buildProbeConfig(
                listOf(ConfigBuilder.ProbeTarget(bean("unsupported.example", type = "splithttp"), 23_000)),
            )
        }
    }

    private fun bean(host: String, type: String = "tcp"): VLESSBean = VLESSBean().apply {
        name = host
        serverAddress = host
        serverPort = 443
        uuid = "12345678-1234-1234-1234-123456789abc"
        this.type = type
    }
}
