package ru.ozero.desktop.engine

import ru.ozero.desktop.model.EngineId
import java.io.File

class SingboxDesktopEngine : SubprocessEngine() {

    override val id = EngineId.SINGBOX
    override val binaryName = if (isWindows()) "sing-box.exe" else "sing-box"
    override val startupTimeoutMs = 10_000L

    private var configFile: File? = null

    override fun extractPort(config: EngineConfig): Int =
        if (config.socksPort > 0) config.socksPort else DEFAULT_MIXED_PORT

    override suspend fun start(config: EngineConfig): EngineStartResult {
        if (config.singboxJson.isNullOrBlank()) {
            return EngineStartResult.Failed(PROTECTED_CONFIG_REQUIRED)
        }
        return super.start(config)
    }

    override fun buildCommand(config: EngineConfig, binaryPath: String): List<String> {
        val json = config.singboxJson.orEmpty()
        val tempFile = File.createTempFile("singbox-", ".json")
        tempFile.writeText(json)
        tempFile.deleteOnExit()
        configFile = tempFile
        return listOf(binaryPath, "run", "-c", tempFile.absolutePath)
    }

    override fun detectReady(line: String): Boolean =
        line.contains("started") || line.contains("inbound/mixed")

    override suspend fun stop() {
        super.stop()
        configFile?.delete()
        configFile = null
    }

    companion object {
        const val DEFAULT_MIXED_PORT = 7890
        const val PROTECTED_CONFIG_REQUIRED = "Sing-box requires a configured protected outbound"

        fun buildProxyConfig(
            port: Int = DEFAULT_MIXED_PORT,
            dnsServer: String = "1.1.1.1",
        ): String {
            require(dnsServer.isNotBlank())
            return buildString {
                append('{')
                append(""""log":{"level":"warn","timestamp":true},""")
                append(""""inbounds":[{""")
                append(""""type":"mixed",""")
                append(""""tag":"mixed-in",""")
                append(""""listen":"127.0.0.1",""")
                append(""""listen_port":$port""")
                append("""}],""")
                append(""""outbounds":[""")
                append("""{"type":"block","tag":"block"}""")
                append("""],""")
                append(""""dns":{"servers":[{"type":"rcode","tag":"dns-block","rcode":"refused"}]},""")
                append(""""route":{""")
                append(""""final":"block",""")
                append(""""auto_detect_interface":true,""")
                append(""""rules":[{"protocol":"dns","outbound":"block"}]""")
                append('}')
                append('}')
            }
        }

        fun buildFullProxyConfig(
            proxyOutbound: String,
            port: Int = DEFAULT_MIXED_PORT,
            dnsServer: String = "1.1.1.1",
        ): String = buildString {
            append('{')
            append(""""log":{"level":"warn","timestamp":true},""")
            append(""""inbounds":[{""")
            append(""""type":"mixed",""")
            append(""""tag":"mixed-in",""")
            append(""""listen":"127.0.0.1",""")
            append(""""listen_port":$port""")
            append("""}],""")
            append(""""outbounds":[""")
            append(proxyOutbound)
            append(""",{"type":"direct","tag":"direct"}""")
            append(""",{"type":"block","tag":"block"}""")
            append(""",{"type":"dns","tag":"dns-out"}""")
            append("""],""")
            append(""""dns":{"servers":[{"type":"udp","tag":"dns-proxy","server":"$dnsServer","detour":"proxy"}]},""")
            append(""""route":{""")
            append(""""final":"proxy",""")
            append(""""auto_detect_interface":true,""")
            append(""""rules":[{"protocol":"dns","outbound":"dns-out"}]""")
            append('}')
            append('}')
        }

        fun buildTunConfig(
            proxyOutbound: String,
            dnsServer: String = "1.1.1.1",
        ): String = buildString {
            append('{')
            append(""""log":{"level":"warn","timestamp":true},""")
            append(""""inbounds":[{""")
            append(""""type":"tun",""")
            append(""""tag":"tun-in",""")
            append(""""interface_name":"ozero-tun",""")
            append(""""inet4_address":"172.19.0.1/30",""")
            append(""""auto_route":true,""")
            append(""""strict_route":false,""")
            append(""""stack":"gvisor",""")
            append(""""sniff":true,""")
            append(""""sniff_override_destination":true""")
            append("""}],""")
            append(""""outbounds":[""")
            append(proxyOutbound)
            append(""",{"type":"direct","tag":"direct"},""")
            append("""{"type":"block","tag":"block"},""")
            append("""{"type":"dns","tag":"dns-out"}""")
            append("""],""")
            append(""""dns":{"servers":[{"type":"udp","tag":"dns-proxy","server":"$dnsServer","detour":"proxy"}]},""")
            append(""""route":{""")
            append(""""final":"proxy",""")
            append(""""auto_detect_interface":true,""")
            append(""""rules":[{"protocol":"dns","outbound":"dns-out"}]""")
            append('}')
            append('}')
        }

        fun buildTunForwardConfig(
            socksPort: Int,
            dnsServer: String = "1.1.1.1",
        ): String = buildString {
            append('{')
            append(""""log":{"level":"warn","timestamp":true},""")
            append(""""inbounds":[{""")
            append(""""type":"tun",""")
            append(""""tag":"tun-in",""")
            append(""""interface_name":"ozero-tun",""")
            append(""""inet4_address":"172.19.0.1/30",""")
            append(""""auto_route":true,""")
            append(""""strict_route":false,""")
            append(""""stack":"gvisor",""")
            append(""""sniff":true,""")
            append(""""sniff_override_destination":true""")
            append("""}],""")
            append(""""outbounds":[""")
            append("""{"type":"socks","tag":"proxy","server":"127.0.0.1","server_port":$socksPort},""")
            append("""{"type":"direct","tag":"direct"},""")
            append("""{"type":"block","tag":"block"},""")
            append("""{"type":"dns","tag":"dns-out"}""")
            append("""],""")
            append(""""dns":{"servers":[{"type":"udp","tag":"dns-proxy","server":"$dnsServer","detour":"proxy"}]},""")
            append(""""route":{""")
            append(""""final":"proxy",""")
            append(""""auto_detect_interface":true,""")
            append(""""rules":[{"protocol":"dns","outbound":"dns-out"}]""")
            append('}')
            append('}')
        }

        fun buildFullTunConfig(
            proxyOutbound: String,
            dnsServer: String = "1.1.1.1",
        ): String = buildString {
            append('{')
            append(""""log":{"level":"warn","timestamp":true},""")
            append(""""inbounds":[{""")
            append(""""type":"tun",""")
            append(""""tag":"tun-in",""")
            append(""""interface_name":"ozero-tun",""")
            append(""""inet4_address":"172.19.0.1/30",""")
            append(""""auto_route":true,""")
            append(""""strict_route":false,""")
            append(""""stack":"gvisor",""")
            append(""""sniff":true,""")
            append(""""sniff_override_destination":true""")
            append("""}],""")
            append(""""outbounds":[""")
            append(proxyOutbound)
            append(""",{"type":"direct","tag":"direct"}""")
            append(""",{"type":"block","tag":"block"}""")
            append(""",{"type":"dns","tag":"dns-out"}""")
            append("""],""")
            append(""""dns":{"servers":[{"type":"udp","tag":"dns-proxy","server":"$dnsServer","detour":"proxy"}]},""")
            append(""""route":{""")
            append(""""final":"proxy",""")
            append(""""auto_detect_interface":true,""")
            append(""""rules":[{"protocol":"dns","outbound":"dns-out"}]""")
            append('}')
            append('}')
        }

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")
    }
}
