package ru.ozero.enginemasterdns

import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile

class MasterDnsBinaryInstaller(
    private val applicationInfo: ApplicationInfo,
    private val installDir: File,
) {
    fun resolve(): File {
        val native = File(applicationInfo.nativeLibraryDir.orEmpty(), MasterDnsClientWrapper.BINARY_NAME)
        if (native.isUsableExecutable()) return native
        val installed = File(installDir, MasterDnsClientWrapper.BINARY_NAME)
        if (installed.isUsableExecutable()) return installed
        installDir.mkdirs()
        extractFromApk(installed)
        installed.setReadable(true, true)
        installed.setExecutable(true, true)
        if (installed.isUsableExecutable()) return installed
        throw FileNotFoundException("masterdns_binary_not_executable")
    }

    private fun extractFromApk(target: File) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.delete()
        for (apk in apkFiles()) {
            if (extractFromApkFile(apk, tmp)) {
                replaceTarget(tmp, target)
                return
            }
        }
        throw FileNotFoundException("masterdns_binary_missing")
    }

    private fun extractFromApkFile(apk: File, tmp: File): Boolean = ZipFile(apk).use { zip ->
        val entry = candidateEntries()
            .firstNotNullOfOrNull { zip.getEntry(it) }
            ?: return@use false
        zip.getInputStream(entry).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
        true
    }

    private fun replaceTarget(tmp: File, target: File) {
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
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

    private fun File.isUsableExecutable(): Boolean =
        isFile && length() > MIN_BINARY_BYTES && canExecute()

    private companion object {
        const val MIN_BINARY_BYTES = 1024L * 1024L
    }
}
