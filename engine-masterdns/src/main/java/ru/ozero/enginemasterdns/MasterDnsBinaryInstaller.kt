package ru.ozero.enginemasterdns

import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

class MasterDnsBinaryInstaller(
    private val applicationInfo: ApplicationInfo,
    private val installDir: File,
) {
    fun resolve(): File {
        nativeCandidate()?.takeIf { it.isUsableExecutable() }?.let { return it }
        synchronized(lockFor(installDir)) {
            val installed = File(installDir, MasterDnsClientWrapper.BINARY_NAME)
            if (installed.isUsableExecutable()) return installed
            ensureInstallDir()
            extractFromApk(installed)
            installed.setReadable(true, true)
            installed.setExecutable(true, true)
            if (installed.isUsableExecutable()) return installed
            throw FileNotFoundException("masterdns_binary_not_executable")
        }
    }

    private fun nativeCandidate(): File? = applicationInfo.nativeLibraryDir
        ?.takeIf { it.isNotBlank() }
        ?.let { File(it, MasterDnsClientWrapper.BINARY_NAME) }

    private fun ensureInstallDir() {
        if (!installDir.mkdirs() && !installDir.isDirectory) {
            throw IOException("masterdns_binary_install_dir_unavailable")
        }
    }

    private fun extractFromApk(target: File) {
        for (apk in apkFiles()) {
            val tmp = File.createTempFile("${target.name}.", ".tmp", target.parentFile)
            if (extractFromApkFile(apk, tmp)) {
                replaceTarget(tmp, target)
                return
            }
            tmp.delete()
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
        runCatching {
            Files.move(
                tmp.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
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

    private companion object {
        private val installLocks = ConcurrentHashMap<String, Any>()

        fun lockFor(dir: File): Any = installLocks.getOrPut(dir.absolutePath) { Any() }
    }
}
