package ru.ozero.enginetelegram

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MtgWrapper(private val nativeLibDir: String) {

    private val binary: File get() = File(nativeLibDir, "libmtg.so")

    suspend fun generateSecret(domain: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder(binary.absolutePath, "generate-secret", "--hex", domain)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            process.waitFor()
            output.lines().lastOrNull { it.isNotBlank() }?.trim()
        }.onFailure { Log.e(TAG, "generateSecret failed: ${it.message}") }.getOrNull()
    }

    fun startProxy(
        port: Int,
        secret: String,
        dohIp: String = "1.1.1.1",
        upstream: String? = null,
    ): Process {
        val args = buildList {
            add(binary.absolutePath)
            add("simple-run")
            add("-n")
            add(dohIp)
            add("-c")
            add("8192")
            add("-t")
            add("10s")
            add("-a")
            add("1MB")
            if (upstream != null) {
                add("--socks5-proxy-url")
                add(upstream)
            }
            add("127.0.0.1:$port")
            add(secret)
        }
        Log.i(TAG, "starting mtg port=$port upstream=$upstream")
        return ProcessBuilder(args)
            .redirectErrorStream(true)
            .start()
    }

    private companion object {
        const val TAG = "MtgWrapper"
    }
}
