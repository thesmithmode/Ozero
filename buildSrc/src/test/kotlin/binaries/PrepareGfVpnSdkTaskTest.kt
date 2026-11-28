package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PrepareGfVpnSdkTaskTest {
    @TempDir
    lateinit var tmp: File

    private val dex = byteArrayOf(1, 2, 3)
    private val lib = byteArrayOf(4, 5, 6, 7)

    @Test
    fun `pinned task rejects missing or substituted vendor input`() {
        val project = ProjectBuilder.builder().withProjectDir(tmp).build()
        val task = project.tasks.register("prepareSdk", PrepareGfVpnSdkTask::class.java).get()
        task.sourceArchive.set(tmp.resolve("missing.zip"))
        task.outputDirectory.set(tmp.resolve("generated"))
        assertThrows<GradleException> { task.prepare() }
        task.sourceArchive.set(archive(mapOf("classes.dex" to dex, "libgojni.so" to lib)))
        assertThrows<GradleException> { task.prepare() }
        assertThat(tmp.resolve("generated")).doesNotExist()
    }

    @Test
    fun `valid archive replaces stale generated assets and cleans staging`() {
        val archive = archive(mapOf("classes.dex" to dex, "libgojni.so" to lib))
        val root = tmp.resolve("generated")
        root.mkdirs()
        root.resolve("stale").writeText("old")
        val stage = tmp.resolve("stage")
        stage.mkdirs()
        stage.resolve("stale").writeText("old")

        PrepareGfVpnSdkTask.prepareArchive(archive, root, stage, manifest(archive))

        assertThat(root.resolve("gfvpn-sdk/classes.dex").readBytes()).isEqualTo(dex)
        assertThat(root.resolve("gfvpn-sdk/libgojni.so").readBytes()).isEqualTo(lib)
        assertThat(root.resolve("stale")).doesNotExist()
        assertThat(root.resolve("gfvpn-sdk/stale")).doesNotExist()
        assertThat(stage).doesNotExist()
    }

    @Test
    fun `archive size and digest are independently validated`() {
        val archive = archive(mapOf("classes.dex" to dex, "libgojni.so" to lib))
        val expected = manifest(archive)
        reject(archive, expected.copy(archive = expected.archive.copy(size = 1)))
        reject(archive, expected.copy(archive = expected.archive.copy(hash = "invalid")))
    }

    @Test
    fun `missing wrong sized and tampered entries never publish output`() {
        val missing = archive(mapOf("classes.dex" to dex))
        reject(missing, manifest(missing))
        val wrongSize = archive(mapOf("classes.dex" to dex, "libgojni.so" to byteArrayOf(4)))
        reject(wrongSize, manifest(wrongSize))
        val tampered = archive(mapOf("classes.dex" to dex, "libgojni.so" to byteArrayOf(4, 5, 6, 0)))
        reject(tampered, manifest(tampered))
    }

    @Test
    fun `copy rejects truncated or oversized streams`() {
        val output = tmp.resolve("output")
        assertThrows<GradleException> {
            PrepareGfVpnSdkTask.copyLimited(ByteArrayInputStream(dex), output, 2)
        }
        assertThrows<GradleException> {
            PrepareGfVpnSdkTask.copyLimited(ByteArrayInputStream(dex), output, 4)
        }
        PrepareGfVpnSdkTask.copyLimited(ByteArrayInputStream(dex), output, 3)
        assertThat(output.readBytes()).isEqualTo(dex)
    }

    @Test
    fun `rejects duplicate SDK entries despite valid archive digest`() {
        val duplicate = archive(mapOf("classes.dex" to dex, "classes.dfy" to dex, "libgojni.so" to lib))
        val bytes = duplicate.readBytes()
        val alternate = "classes.dfy".toByteArray()
        val expected = "classes.dex".toByteArray()
        var replaced = 0
        for (offset in 0..bytes.size - alternate.size) {
            if (bytes.copyOfRange(offset, offset + alternate.size).contentEquals(alternate)) {
                expected.copyInto(bytes, offset)
                replaced++
            }
        }
        assertThat(replaced).isEqualTo(2)
        duplicate.writeBytes(bytes)
        reject(duplicate, manifest(duplicate))
    }

    @Test
    fun `invalid ZIP cannot publish SDK even with matching archive digest`() {
        val invalid = tmp.resolve("invalid.zip").apply { writeBytes(dex) }
        val root = tmp.resolve("generated")
        val stage = tmp.resolve("stage")
        assertThrows<IOException> {
            PrepareGfVpnSdkTask.prepareArchive(invalid, root, stage, manifest(invalid))
        }
        assertThat(root).doesNotExist()
        assertThat(stage).doesNotExist()
    }

    @Test
    fun `failed destination creation cleans staging and preserves existing parent file`() {
        val archive = archive(mapOf("classes.dex" to dex, "libgojni.so" to lib))
        val parent = tmp.resolve("not-a-directory").apply { writeText("keep") }
        val stage = tmp.resolve("stage")
        assertThrows<IOException> {
            PrepareGfVpnSdkTask.prepareArchive(archive, parent.resolve("assets"), stage, manifest(archive))
        }
        assertThat(parent.readText()).isEqualTo("keep")
        assertThat(stage).doesNotExist()
    }

    @Test
    fun `valid files spanning multiple buffers are copied and hashed completely`() {
        val largeDex = ByteArray(150_000) { (it % 251).toByte() }
        val archive = archive(mapOf("classes.dex" to largeDex, "libgojni.so" to lib))
        val expected = SdkManifest(
            sdkFile("archive", archive.readBytes()),
            listOf(sdkFile("classes.dex", largeDex), sdkFile("libgojni.so", lib)),
        )
        val root = tmp.resolve("generated")
        PrepareGfVpnSdkTask.prepareArchive(archive, root, tmp.resolve("stage"), expected)
        assertThat(root.resolve("gfvpn-sdk/classes.dex").readBytes()).isEqualTo(largeDex)
    }

    private fun reject(archive: File, manifest: SdkManifest) {
        val root = tmp.resolve("generated")
        val stage = tmp.resolve("stage")
        assertThrows<GradleException> {
            PrepareGfVpnSdkTask.prepareArchive(archive, root, stage, manifest)
        }
        assertThat(root).doesNotExist()
        assertThat(stage).doesNotExist()
    }

    private fun archive(entries: Map<String, ByteArray>): File {
        val file = File.createTempFile("gfvpn", ".zip", tmp)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return file
    }

    private fun manifest(archive: File) = SdkManifest(
        sdkFile("archive", archive.readBytes()),
        listOf(sdkFile("classes.dex", dex), sdkFile("libgojni.so", lib)),
    )

    private fun sdkFile(name: String, data: ByteArray) = SdkFile(
        name,
        data.size.toLong(),
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it.toInt() and 255) },
    )
}
