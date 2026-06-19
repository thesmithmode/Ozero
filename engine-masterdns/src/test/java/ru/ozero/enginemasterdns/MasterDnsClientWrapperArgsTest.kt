package ru.ozero.enginemasterdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.FileNotFoundException

class MasterDnsClientWrapperArgsTest {

    @Test
    fun `args start with absolute binary path`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/data/app/lib/libmdnsvpn.so",
            configPath = "/data/data/ru.ozero.app/files/masterdns/client_config.toml",
            resolversPath = "/data/data/ru.ozero.app/files/masterdns/client_resolvers.txt",
            logPath = null,
        )
        assertEquals("/data/app/lib/libmdnsvpn.so", args.first())
    }

    @Test
    fun `args contain config and resolvers flags`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/lib/libmdnsvpn.so",
            configPath = "/tmp/c.toml",
            resolversPath = "/tmp/r.txt",
            logPath = null,
        )
        assertTrue(args.containsInOrder("-config", "/tmp/c.toml"))
        assertTrue(args.containsInOrder("-resolvers", "/tmp/r.txt"))
    }

    @Test
    fun `log flag appears when logPath provided`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/lib/libmdnsvpn.so",
            configPath = "/c.toml",
            resolversPath = "/r.txt",
            logPath = "/data/log.txt",
        )
        assertTrue(args.containsInOrder("-log", "/data/log.txt"))
    }

    @Test
    fun `log flag absent when logPath null`() {
        val args = MasterDnsClientWrapper.buildArgs(
            binaryPath = "/lib/libmdnsvpn.so",
            configPath = "/c.toml",
            resolversPath = "/r.txt",
            logPath = null,
        )
        assertFalse("-log" in args)
    }

    @Test
    fun `args length without log is 5`() {
        val args = MasterDnsClientWrapper.buildArgs("/b", "/c", "/r", null)
        assertEquals(5, args.size)
    }

    @Test
    fun `args length with log is 7`() {
        val args = MasterDnsClientWrapper.buildArgs("/b", "/c", "/r", "/l")
        assertEquals(7, args.size)
    }

    @Test
    fun `wrapper binary path resolved relative to nativeLibDir`() {
        val wrapper = MasterDnsClientWrapper("/data/app/native/arm64-v8a")
        assertEquals("/data/app/native/arm64-v8a/libmdnsvpn.so", wrapper.binary.path.replace('\\', '/'))
    }

    @Test
    fun `wrapper binary throws clear error when nativeLibDir null and no provider supplied`() {
        val wrapper = MasterDnsClientWrapper(null)

        val error = assertThrows(FileNotFoundException::class.java) {
            wrapper.binary
        }

        assertEquals("masterdns_native_library_dir_missing", error.message)
    }

    private fun List<String>.containsInOrder(flag: String, value: String): Boolean {
        val idx = indexOf(flag)
        return idx >= 0 && idx + 1 < size && this[idx + 1] == value
    }
}
