package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtractGfVpnHevTaskTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `extracts only ARM64 native code with the isolated filename`() {
        val aar = tmp.resolve("sdk.aar")
        val expected = byteArrayOf(127, 69, 76, 70, 1, 2, 3)
        writeAar(
            aar,
            mapOf(
                "jni/arm64-v8a/libhev-socks5-tunnel.so" to expected,
                "jni/x86_64/libhev-socks5-tunnel.so" to byteArrayOf(9),
                "classes.jar" to byteArrayOf(8),
            ),
        )
        val output = tmp.resolve("generated/jniLibs")

        newTask(aar, output).extract()

        assertThat(Files.readAllBytes(output.resolve("arm64-v8a/libhev-gfvpn-socks5-tunnel.so")))
            .isEqualTo(expected)
        assertThat(output.resolve("x86_64").toFile()).doesNotExist()
        assertThat(output.resolve("classes.jar").toFile()).doesNotExist()
    }

    @Test
    fun `rejects missing and empty ARM64 libraries`() {
        val missing = tmp.resolve("missing.aar")
        writeAar(missing, mapOf("classes.jar" to byteArrayOf(1)))
        assertThrows<GradleException> { newTask(missing, tmp.resolve("missing")).extract() }

        val empty = tmp.resolve("empty.aar")
        writeAar(empty, mapOf("jni/arm64-v8a/libhev-socks5-tunnel.so" to ByteArray(0)))
        val output = tmp.resolve("empty")
        assertThrows<GradleException> { newTask(empty, output).extract() }
        assertThat(output.resolve("arm64-v8a/libhev-gfvpn-socks5-tunnel.so").toFile()).doesNotExist()
    }

    private fun newTask(aar: Path, output: Path): ExtractGfVpnHevTask {
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        return project.tasks.register("extractHev", ExtractGfVpnHevTask::class.java) {
            sourceAar.set(aar.toFile())
            outputDirectory.set(output.toFile())
        }.get()
    }

    private fun writeAar(path: Path, entries: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}
