package ru.ozero.engineopera

import java.io.File
import java.util.concurrent.TimeUnit

interface OperaProxyProcess {
    val alive: Boolean
    fun stop()
}

fun interface OperaProxyLauncher {
    fun launch(config: OperaProxyConfig, port: Int): OperaProxyProcess
}

class SubprocessOperaProxyLauncher(
    private val binary: File,
    private val workDir: File,
) : OperaProxyLauncher {
    override fun launch(config: OperaProxyConfig, port: Int): OperaProxyProcess {
        require(binary.isFile && binary.canExecute()) { "opera binary unavailable" }
        check(workDir.isDirectory || workDir.mkdirs()) { "opera working directory unavailable" }
        check(workDir.list()?.isEmpty() == true) { "opera working directory must be empty" }
        val process = ProcessBuilder(OperaProxyCommand.build(binary, config, port))
            .directory(workDir)
            .redirectErrorStream(true)
            .start()
        return try {
            process.outputStream.close()
            Thread {
                runCatching {
                    process.inputStream.use { input ->
                        val buffer = ByteArray(4096)
                        while (input.read(buffer) != -1) Unit
                    }
                }
            }.apply {
                isDaemon = true
                name = "opera-proxy-output"
                start()
            }
            object : OperaProxyProcess {
                override val alive: Boolean get() = process.isAlive

                override fun stop() {
                    process.destroy()
                    if (!process.waitFor(500, TimeUnit.MILLISECONDS)) {
                        process.destroyForcibly()
                        check(process.waitFor(500, TimeUnit.MILLISECONDS)) { "opera process did not stop" }
                    }
                }
            }
        } catch (e: Exception) {
            process.destroyForcibly()
            throw e
        }
    }
}
