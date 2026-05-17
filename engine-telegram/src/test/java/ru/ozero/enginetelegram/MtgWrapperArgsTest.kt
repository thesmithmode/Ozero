package ru.ozero.enginetelegram

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class MtgWrapperArgsTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var fakeBinary: File
    private lateinit var wrapper: MtgWrapper

    @BeforeEach
    fun setUp() {
        fakeBinary = File(tempDir.toFile(), "libmtg.so")
        fakeBinary.writeText("#!/bin/sh\nprintf '%s\\n' \"\$0\" \"\$@\"")
        fakeBinary.setExecutable(true)
        wrapper = MtgWrapper(tempDir.toFile().absolutePath)
    }

    @AfterEach
    fun tearDown() {
        fakeBinary.delete()
    }

    @Nested
    inner class GenerateSecret {

        @Test
        fun `should return null when binary does not exist`() = runTest {
            fakeBinary.delete()
            val result = wrapper.generateSecret("example.com")
            assertNull(result, "generateSecret должен вернуть null если binary не существует на диске")
        }

        @Test
        fun `should pass generate-secret --hex domain args to binary`() = runTest {
            val recordFile = File(tempDir.toFile(), "args.txt")
            val recordPath = recordFile.absolutePath
            fakeBinary.writeText(
                "#!/bin/sh\nprintf '%s\\n' \"\$@\" > '$recordPath'\nprintf 'fakesecret'",
            )
            fakeBinary.setExecutable(true)
            val result = wrapper.generateSecret("example.com")
            assertNotNull(result)
            val recordedArgs = recordFile.readLines().filter { it.isNotBlank() }
            assertTrue("generate-secret" in recordedArgs, "generate-secret не найден в: $recordedArgs")
            assertTrue("--hex" in recordedArgs, "--hex не найден в: $recordedArgs")
            assertTrue("example.com" in recordedArgs, "domain не найден в: $recordedArgs")
        }

        @Test
        fun `should return null on non-zero exit code`() = runTest {
            fakeBinary.writeText("#!/bin/sh\nexit 1")
            fakeBinary.setExecutable(true)
            val result = wrapper.generateSecret("example.com")
            assertNull(result, "generateSecret должен вернуть null при ненулевом exit code")
        }

        @Test
        fun `should return last non-blank line of output as secret`() = runTest {
            fakeBinary.writeText("#!/bin/sh\nprintf 'ee5a401c3a9990adbfd\n'")
            fakeBinary.setExecutable(true)
            val result = wrapper.generateSecret("example.com")
            assertTrue(result == "ee5a401c3a9990adbfd", "должна вернуться последняя непустая строка вывода")
        }

        @Test
        fun `should return null and not hang when binary hangs longer than timeout`() = runTest {
            fakeBinary.writeText("#!/bin/sh\nsleep 120")
            fakeBinary.setExecutable(true)
            val startMs = System.currentTimeMillis()
            val result = wrapper.generateSecret("example.com")
            val elapsedMs = System.currentTimeMillis() - startMs
            assertNull(result, "hanging binary должен дать null после timeout")
            assertTrue(
                elapsedMs < 30_000,
                "generateSecret обязан принудительно завершить процесс за ≤10s timeout, прошло ${elapsedMs}ms",
            )
        }
    }

    private fun collectArgs(process: Process): List<String> {
        val output = process.inputStream.bufferedReader().readText().trim()
        process.waitFor()
        return output.lines().filter { it.isNotBlank() }
    }

    @Test
    fun `should use binary from nativeLibraryDir as first arg`() {
        val args = collectArgs(wrapper.startProxy(3128, "secret123"))
        assertTrue(args.first().endsWith("libmtg.so"), "first arg must be libmtg.so path, got: ${args.first()}")
    }

    @Test
    fun `should include simple-run subcommand`() {
        val args = collectArgs(wrapper.startProxy(3128, "secret123"))
        assertTrue("simple-run" in args)
    }

    @Test
    fun `should include port in bind address`() {
        val args = collectArgs(wrapper.startProxy(4567, "secret123"))
        assertTrue(args.any { it == "127.0.0.1:4567" })
    }

    @Test
    fun `should include secret as last arg`() {
        val args = collectArgs(wrapper.startProxy(3128, "mysecret"))
        assertTrue(args.last() == "mysecret")
    }

    @Test
    fun `should not include socks5-proxy-url when upstream is null`() {
        val args = collectArgs(wrapper.startProxy(3128, "s", upstream = null))
        assertFalse(args.any { it == "--socks5-proxy-url" })
    }

    @Test
    fun `should include socks5-proxy-url when upstream given`() {
        val upstream = "socks5://127.0.0.1:1080"
        val args = collectArgs(wrapper.startProxy(3128, "s", upstream = upstream))
        val idx = args.indexOf("--socks5-proxy-url")
        assertTrue(idx >= 0, "--socks5-proxy-url not found in args")
        assertTrue(args.getOrNull(idx + 1) == upstream, "upstream value mismatch")
    }

    @Test
    fun `should use custom dohIp`() {
        val args = collectArgs(wrapper.startProxy(3128, "s", dohIp = "8.8.8.8"))
        val idx = args.indexOf("-n")
        assertTrue(idx >= 0, "-n flag not found")
        assertTrue(args.getOrNull(idx + 1) == "8.8.8.8")
    }

    @Test
    fun `should include connection limit flag`() {
        val args = collectArgs(wrapper.startProxy(3128, "s"))
        val idx = args.indexOf("-c")
        assertTrue(idx >= 0, "-c flag not found")
        assertTrue(args.getOrNull(idx + 1) == "8192")
    }

    @Test
    fun `should include anti-replay flag`() {
        val args = collectArgs(wrapper.startProxy(3128, "s"))
        val idx = args.indexOf("-a")
        assertTrue(idx >= 0, "-a flag not found")
        assertTrue(args.getOrNull(idx + 1) == "1MB")
    }
}
