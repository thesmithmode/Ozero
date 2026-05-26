package ru.ozero.desktop.proxy

import java.util.logging.Logger

interface SystemProxy {
    fun enable(addr: String, bypass: List<String> = listOf("<local>"))
    fun disable()
    fun enableKillSwitch()
}

class WindowsSystemProxy : SystemProxy {

    private val log = Logger.getLogger("WindowsSystemProxy")

    override fun enable(addr: String, bypass: List<String>) {
        log.info("setting system proxy to $addr")
        regSetDword(PROXY_ENABLE, 1)
        regSetString(PROXY_SERVER, addr)
        regSetString(PROXY_OVERRIDE, bypass.joinToString(";"))
        regDelete(AUTO_CONFIG_URL)
        flushDns()
    }

    override fun disable() {
        log.info("disabling system proxy")
        regSetDword(PROXY_ENABLE, 0)
        regDelete(PROXY_SERVER)
        regDelete(PROXY_OVERRIDE)
        regDelete(AUTO_CONFIG_URL)
        flushDns()
    }

    override fun enableKillSwitch() {
        log.info("enabling kill switch proxy")
        regSetDword(PROXY_ENABLE, 1)
        regSetString(PROXY_SERVER, KILL_SWITCH_ADDR)
        regDelete(PROXY_OVERRIDE)
        flushDns()
    }

    private fun regSetDword(name: String, value: Int) {
        exec("reg", "add", INTERNET_SETTINGS_KEY, "/v", name, "/t", "REG_DWORD", "/d", value.toString(), "/f")
    }

    private fun regSetString(name: String, value: String) {
        exec("reg", "add", INTERNET_SETTINGS_KEY, "/v", name, "/t", "REG_SZ", "/d", value, "/f")
    }

    private fun regDelete(name: String) {
        exec("reg", "delete", INTERNET_SETTINGS_KEY, "/v", name, "/f")
    }

    private fun flushDns() {
        exec("ipconfig", "/flushdns")
    }

    private fun exec(vararg args: String) {
        runCatching {
            val pb = ProcessBuilder(*args)
            if (isWindows()) {
                pb.redirectErrorStream(true)
            }
            val p = pb.start()
            p.waitFor()
        }.onFailure { log.warning("exec failed: ${args.toList()} — ${it.message}") }
    }

    companion object {
        private const val INTERNET_SETTINGS_KEY =
            "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings"
        private const val PROXY_ENABLE = "ProxyEnable"
        private const val PROXY_SERVER = "ProxyServer"
        private const val PROXY_OVERRIDE = "ProxyOverride"
        private const val AUTO_CONFIG_URL = "AutoConfigURL"
        private const val KILL_SWITCH_ADDR = "127.0.0.1:65535"

        private fun isWindows(): Boolean =
            System.getProperty("os.name").lowercase().contains("win")

        fun create(): SystemProxy {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("win") -> WindowsSystemProxy()
                os.contains("mac") -> MacSystemProxy()
                else -> LinuxSystemProxy()
            }
        }
    }
}

class MacSystemProxy : SystemProxy {
    private val log = Logger.getLogger("MacSystemProxy")

    override fun enable(addr: String, bypass: List<String>) {
        val parts = addr.split(":")
        val host = parts.getOrElse(0) { "127.0.0.1" }
        val port = parts.getOrElse(1) { "7890" }
        val services = listOf("Wi-Fi", "Ethernet")
        services.forEach { svc ->
            exec("networksetup", "-setsocksfirewallproxy", svc, host, port)
            exec("networksetup", "-setsocksfirewallproxystate", svc, "on")
            exec("networksetup", "-setwebproxy", svc, host, port)
            exec("networksetup", "-setwebproxystate", svc, "on")
            exec("networksetup", "-setsecurewebproxy", svc, host, port)
            exec("networksetup", "-setsecurewebproxystate", svc, "on")
        }
    }

    override fun disable() {
        val services = listOf("Wi-Fi", "Ethernet")
        services.forEach { svc ->
            exec("networksetup", "-setsocksfirewallproxystate", svc, "off")
            exec("networksetup", "-setwebproxystate", svc, "off")
            exec("networksetup", "-setsecurewebproxystate", svc, "off")
        }
    }

    override fun enableKillSwitch() {
        enable("127.0.0.1:65535")
    }

    private fun exec(vararg args: String) {
        runCatching {
            ProcessBuilder(*args).redirectErrorStream(true).start().waitFor()
        }.onFailure { log.warning("exec failed: ${args.toList()} — ${it.message}") }
    }
}

class LinuxSystemProxy : SystemProxy {
    private val log = Logger.getLogger("LinuxSystemProxy")

    override fun enable(addr: String, bypass: List<String>) {
        val parts = addr.split(":")
        val host = parts.getOrElse(0) { "127.0.0.1" }
        val port = parts.getOrElse(1) { "7890" }
        exec("gsettings", "set", "org.gnome.system.proxy", "mode", "manual")
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "host", host)
        exec("gsettings", "set", "org.gnome.system.proxy.socks", "port", port)
        exec("gsettings", "set", "org.gnome.system.proxy.http", "host", host)
        exec("gsettings", "set", "org.gnome.system.proxy.http", "port", port)
        exec("gsettings", "set", "org.gnome.system.proxy.https", "host", host)
        exec("gsettings", "set", "org.gnome.system.proxy.https", "port", port)
    }

    override fun disable() {
        exec("gsettings", "set", "org.gnome.system.proxy", "mode", "none")
    }

    override fun enableKillSwitch() {
        enable("127.0.0.1:65535")
    }

    private fun exec(vararg args: String) {
        runCatching {
            ProcessBuilder(*args).redirectErrorStream(true).start().waitFor()
        }.onFailure { log.warning("exec failed: ${args.toList()} — ${it.message}") }
    }
}
