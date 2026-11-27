package binaries

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.zip.ZipFile

abstract class ExtractGfVpnHevTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceAar: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun extract() {
        val root = outputDirectory.get().asFile
        val target = root.resolve("arm64-v8a/libhev-gfvpn-socks5-tunnel.so")
        target.parentFile.mkdirs()
        val temporary = target.resolveSibling(target.name + ".tmp")
        try {
            ZipFile(sourceAar.get().asFile).use { archive ->
                val entry = archive.getEntry("jni/arm64-v8a/libhev-socks5-tunnel.so")
                    ?: throw GradleException("Verified gfVPN HEV AAR has no ARM64 library")
                // The exact filename lookup cannot match a directory entry (which ends in '/').
                if (entry.size <= 0) {
                    throw GradleException("Verified gfVPN HEV library is empty")
                }
                archive.getInputStream(entry).use { input ->
                    temporary.outputStream().use { output -> input.copyTo(output) }
                }
                if (temporary.length() != entry.size) {
                    throw GradleException("gfVPN HEV extraction size mismatch")
                }
            }
            java.nio.file.Files.move(
                temporary.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            temporary.delete()
        }
    }
}
