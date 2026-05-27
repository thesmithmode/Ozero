package ru.ozero.enginemasterdns

import java.io.File

interface MasterDnsClientWrapperContract {
    val binary: File
    fun startClient(
        configPath: String,
        resolversPath: String,
        logPath: String?,
        upstreamSocksUrl: String? = null,
    ): Process
}

class MasterDnsClientWrapper(private val nativeLibDir: String) : MasterDnsClientWrapperContract {

    override val binary: File get() = File(nativeLibDir, "libmdnsvpn.so")

    override fun startClient(
        configPath: String,
        resolversPath: String,
        logPath: String?,
        upstreamSocksUrl: String?,
    ): Process {
        val args = buildArgs(binary.absolutePath, configPath, resolversPath, logPath, upstreamSocksUrl)
        return ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
    }

    companion object {
        fun buildArgs(
            binaryPath: String,
            configPath: String,
            resolversPath: String,
            logPath: String?,
            upstreamSocksUrl: String? = null,
        ): List<String> = buildList {
            add(binaryPath)
            add("-config")
            add(configPath)
            add("-resolvers")
            add(resolversPath)
            if (logPath != null) {
                add("-log")
                add(logPath)
            }
            if (upstreamSocksUrl != null) {
                add("--socks5-proxy-url")
                add(upstreamSocksUrl)
            }
        }
    }
}
