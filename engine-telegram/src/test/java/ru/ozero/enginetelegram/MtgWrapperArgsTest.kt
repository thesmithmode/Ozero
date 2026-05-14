package ru.ozero.enginetelegram

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
