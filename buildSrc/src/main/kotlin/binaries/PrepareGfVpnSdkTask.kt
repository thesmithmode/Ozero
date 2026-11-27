package binaries

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipFile

@CacheableTask
abstract class PrepareGfVpnSdkTask : DefaultTask() {
    @get:Optional
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val localApk: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val root = outputDirectory.get().asFile
        val temporary = File.createTempFile("gfvpn-", ".apk", temporaryDir)
        try {
            val source = localApk.orNull?.asFile ?: throw GradleException("Verified gfVPN build input is missing")
            verify(source, APK_SIZE, APK_SHA)
            val stage = temporaryDir.resolve("sdk-stage")
            stage.deleteRecursively()
            check(stage.mkdirs())
            ZipFile(source).use { zip ->
                extract(zip, "classes.dex", stage.resolve("classes.dex"), DEX_SIZE, DEX_SHA)
                extract(zip, "lib/arm64-v8a/libgojni.so", stage.resolve("libgojni.so"), LIB_SIZE, LIB_SHA)
            }
            root.deleteRecursively()
            val target = root.resolve("gfvpn-sdk")
            check(target.mkdirs())
            stage.copyRecursively(target, overwrite = true)
            stage.deleteRecursively()
        } finally {
            temporary.delete()
        }
    }

    private fun extract(zip: ZipFile, name: String, target: File, size: Long, hash: String) {
        val entries = zip.entries().asSequence().filter { it.name == name }.toList()
        if (entries.size != 1 || entries.single().size != size) {
            throw GradleException("Unexpected gfVPN SDK entry")
        }
        zip.getInputStream(entries.single()).use { copyLimited(it, target, size) }
        verify(target, size, hash)
    }

    private fun copyLimited(input: InputStream, target: File, limit: Long) {
        target.outputStream().use { output ->
            val buffer = ByteArray(65536)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > limit) throw GradleException("gfVPN source exceeds expected size")
                output.write(buffer, 0, count)
            }
            if (total != limit) throw GradleException("gfVPN source size mismatch")
        }
    }

    private fun verify(file: File, size: Long, hash: String) {
        if (file.length() != size) throw GradleException("gfVPN source size mismatch")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != hash) throw GradleException("gfVPN source SHA-256 mismatch")
    }

    private companion object {
        const val APK_SIZE = 155155974L
        const val DEX_SIZE = 5377536L
        const val LIB_SIZE = 75485264L
        const val APK_SHA = "9309781c889578388c250651a9fa3c5c1d4a6e9a0b432dec7e7cc1f93abf57d6"
        const val DEX_SHA = "103f8155e3bd33b8f13d8ff7d1f63cb5ec1fe7acc1aa1c395eaedb83d336c829"
        const val LIB_SHA = "7fdffc628230ce188c474c42bfc5fef5b802ace74bd9e9b249f0272f9090e404"
    }
}
