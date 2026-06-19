package ru.ozero.enginemasterdns

import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipFile

class MasterDnsBinaryResolver(
    private val applicationInfo: ApplicationInfo,
) {
    fun resolve(): File {
        val native = nativeCandidate()
            ?: throw FileNotFoundException("masterdns_native_library_dir_missing")
        if (native.isUsableExecutable()) return native
        if (hasPackagedBinary()) {
            throw FileNotFoundException("masterdns_binary_not_extracted")
        }
        throw FileNotFoundException("masterdns_binary_missing")
    }

    private fun nativeCandidate(): File? = applicationInfo.nativeLibraryDir
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it, MasterDnsClientWrapper.BINARY_NAME) }

    private fun hasPackagedBinary(): Boolean = apkFiles().any { apk ->
        try {
            ZipFile(apk).use { zip ->
                candidateEntries().any { zip.getEntry(it) != null }
            }
        } catch (e: IOException) {
            false
        }
    }

    private fun apkFiles(): List<File> = buildList {
        applicationInfo.sourceDir?.let { add(File(it)) }
        applicationInfo.splitSourceDirs?.forEach { add(File(it)) }
    }.filter { it.isFile }

    private fun candidateEntries(): List<String> = Build.SUPPORTED_ABIS
        .filter { it.isNotBlank() }
        .map { "lib/$it/${MasterDnsClientWrapper.BINARY_NAME}" }
        .ifEmpty { listOf("lib/arm64-v8a/${MasterDnsClientWrapper.BINARY_NAME}") }

    private fun File.isUsableExecutable(): Boolean = isFile && canExecute()
}
