package ru.ozero.enginemasterdns

import android.content.pm.ApplicationInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MasterDnsBinaryResolverTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `uses extracted native library when available`() {
        val nativeDir = File(tempDir, "native").also { it.mkdirs() }
        val binary = File(nativeDir, MasterDnsClientWrapper.BINARY_NAME)
        binary.writeBytes(byteArrayOf(7))
        binary.setExecutable(true, true)
        val appInfo = ApplicationInfo().apply { nativeLibraryDir = nativeDir.absolutePath }

        val resolved = MasterDnsBinaryResolver(appInfo).resolve()

        assertEquals(binary.absolutePath, resolved.absolutePath)
    }

    @Test
    fun `does not return files dir fallback when packaged binary was not extracted`() {
        val apk = File(tempDir, "app.apk")
        writeApk(apk, "lib/arm64-v8a/${MasterDnsClientWrapper.BINARY_NAME}")
        val appInfo = ApplicationInfo().apply {
            nativeLibraryDir = File(tempDir, "missing-native").absolutePath
            sourceDir = apk.absolutePath
        }

        val error = assertThrows(FileNotFoundException::class.java) {
            MasterDnsBinaryResolver(appInfo).resolve()
        }

        assertEquals("masterdns_binary_not_extracted", error.message)
    }

    @Test
    fun `native library dir null fails without relative lookup`() {
        val apk = File(tempDir, "app-null-native.apk")
        writeApk(apk, "lib/arm64-v8a/${MasterDnsClientWrapper.BINARY_NAME}")
        val appInfo = ApplicationInfo().apply { sourceDir = apk.absolutePath }

        val error = assertThrows(FileNotFoundException::class.java) {
            MasterDnsBinaryResolver(appInfo).resolve()
        }

        assertEquals("masterdns_native_library_dir_missing", error.message)
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
        assertTrue(apk.isFile)
    }
}
