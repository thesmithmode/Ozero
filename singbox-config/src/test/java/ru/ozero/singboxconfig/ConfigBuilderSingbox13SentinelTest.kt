package ru.ozero.singboxconfig

import org.junit.jupiter.api.Test
import ru.ozero.singboxfmt.VLESSBean
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConfigBuilderSingbox13SentinelTest {

    private fun makeBean() = VLESSBean().apply {
        uuid = "00000000-0000-0000-0000-000000000000"
        serverAddress = "test.example.com"
        serverPort = 443
        type = "tcp"
        security = "none"
    }

    @Test
    fun `tun inbound must not contain deprecated sniff field`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())
        assertFalse(
            json.contains(""""sniff":true"""),
            "sniff:true removed in sing-box 1.13 — causes SIGABRT",
        )
    }

    @Test
    fun `tun inbound must not contain deprecated sniff_override_destination`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())
        assertFalse(
            json.contains("sniff_override_destination"),
            "sniff_override_destination removed in sing-box 1.13",
        )
    }

    @Test
    fun `route rules must use action sniff instead of inbound sniff`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())
        assertContains(json, """"action":"sniff"""")
    }

    @Test
    fun `dns route rule must use hijack-dns action`() {
        val json = ConfigBuilder.buildSingboxConfig(makeBean())
        assertContains(json, """"protocol":"dns","action":"hijack-dns"""")
        assertFalse(json.contains(""""type":"dns""""), "dns outbound removed in sing-box 1.13.0")
    }

    @Test
    fun `auto-select config must not contain deprecated sniff fields`() {
        val beans = listOf(makeBean(), makeBean().apply { serverAddress = "test2.example.com" })
        val json = ConfigBuilder.buildSingboxAutoConfig(beans)
        assertFalse(json.contains(""""sniff":true"""))
        assertFalse(json.contains("sniff_override_destination"))
        assertContains(json, """"action":"sniff"""")
    }
}
