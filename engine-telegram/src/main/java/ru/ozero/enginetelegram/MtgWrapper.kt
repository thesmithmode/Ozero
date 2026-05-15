package ru.ozero.enginetelegram

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

class MtgWrapper(private val nativeLibDir: String) {

    private val binary: File get() = File(nativeLibDir, "libmtg.so")

    suspend fun generateSecret(domain: String): String? = withContext(Dispatchers.IO) {
        if (!binary.exists()) {
            Log.e(TAG, "generateSecret: binary not found at ${binary.absolutePath}")
            return@withContext null
        }
        var process: Process? = null
        try {
            process = ProcessBuilder(binary.absolutePath, "generate-secret", "--hex", domain)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val finished = process.waitFor(GENERATE_SECRET_TIMEOUT_S, TimeUnit.SECONDS)
            if (!finished) {
                Log.e(TAG, "generateSecret timeout > ${GENERATE_SECRET_TIMEOUT_S}s")
                return@withContext null
            }
            val exitCode = process.exitValue()
            if (exitCode != 0) {
                Log.e(TAG, "generateSecret exitCode=$exitCode output=$output")
                return@withContext null
            }
            val secret = output.lines().lastOrNull { it.isNotBlank() }?.trim()
            if (secret == null) Log.e(TAG, "generateSecret: empty output")
            secret
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "generateSecret failed: ${t.message}")
            null
        } finally {
            process?.let { p -> if (p.isAlive) runCatching { p.destroyForcibly() } }
        }
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
        const val GENERATE_SECRET_TIMEOUT_S = 10L
    }
}
