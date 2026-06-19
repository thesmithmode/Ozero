package ru.ozero.enginemasterdns

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MasterDnsBinaryInstallerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `uses extracted native library when available`() {
        val nativeDir = File(tempDir, "native").also { it.mkdirs() }
        val binary = File(nativeDir, MasterDnsClientWrapper.BINARY_NAME)
        binary.writeBytes(byteArrayOf(7))
        binary.setExecutable(true, true)
        val appInfo = ApplicationInfo().apply { nativeLibraryDir = nativeDir.absolutePath }

        val resolved = MasterDnsBinaryInstaller(appInfo, File(tempDir, "install")).resolve()

        assertEquals(binary.absolutePath, resolved.absolutePath)
    }

    @Test
    fun `extracts subprocess binary from apk when native library dir is empty`() {
        val apk = File(tempDir, "app.apk")
        writeApk(apk, "lib/arm64-v8a/${MasterDnsClientWrapper.BINARY_NAME}")
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = File(tempDir, "missing-native").absolutePath
            sourceDir = apk.absolutePath
        }

        val resolved = MasterDnsBinaryInstaller(appInfo, File(tempDir, "install")).resolve()

        assertEquals(MasterDnsClientWrapper.BINARY_NAME, resolved.name)
        assertTrue(resolved.isFile)
        assertTrue(resolved.canExecute())
    }

    @Test
    fun `extracts subprocess binary from apk when native library dir is null`() {
        val apk = File(tempDir, "app-null-native.apk")
        writeApk(apk, "lib/arm64-v8a/${MasterDnsClientWrapper.BINARY_NAME}")
        val appInfo = ApplicationInfo().apply { sourceDir = apk.absolutePath }

        val resolved = MasterDnsBinaryInstaller(appInfo, File(tempDir, "install-null-native")).resolve()

        assertEquals(MasterDnsClientWrapper.BINARY_NAME, resolved.name)
        assertTrue(resolved.isFile)
        assertTrue(resolved.canExecute())
    }

    @Test
    fun `wrapper accepts self healing binary provider`() {
        val binary = File(tempDir, MasterDnsClientWrapper.BINARY_NAME)
        val wrapper = MasterDnsClientWrapper("/missing") { binary }

        assertEquals(binary.absolutePath, wrapper.binary.absolutePath)
    }

    private fun writeApk(apk: File, entryName: String) {
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(entryName))
            zip.write(byteArrayOf(3))
            zip.closeEntry()
        }
    }
}
