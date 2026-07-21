package binaries

import org.assertj.core.api.Assertions.assertThat
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class StripGoClassesFromAarTaskTest {
    @TempDir
    lateinit var tmp: Path

    @Test
    fun `task removes Go runtime classes and keeps SDK AAR content`() {
        val source = tmp.resolve("input.aar")
        val output = tmp.resolve("generated/runtime/output.aar")
        writeAar(
            source,
            mapOf(
                "go/Seq.class" to "go-runtime".toByteArray(),
                "go/error.class" to "go-error".toByteArray(),
                "com/example/Sdk.class" to "sdk".toByteArray(),
            ),
        )
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val task = project.tasks.register(
            "stripGoClasses",
            StripGoClassesFromAarTask::class.java,
        ) {
            sourceAar.set(source.toFile())
            outputAar.set(output.toFile())
        }.get()

        task.strip()

        assertThat(Files.exists(output)).isTrue()
        ZipFile(output.toFile()).use { aar ->
            assertThat(aar.entries().asSequence().map { it.name }.toList())
                .containsExactlyInAnyOrder("AndroidManifest.xml", "classes.jar", "proguard.txt")
            assertThat(aar.getInputStream(aar.getEntry("AndroidManifest.xml")).readBytes())
                .isEqualTo("manifest".toByteArray())
            assertThat(aar.getInputStream(aar.getEntry("proguard.txt")).readBytes())
                .isEqualTo("rules".toByteArray())
            val classes = readZip(aar.getInputStream(aar.getEntry("classes.jar")))
            assertThat(classes).containsOnlyKeys("com/example/Sdk.class")
            assertThat(classes.getValue("com/example/Sdk.class")).isEqualTo("sdk".toByteArray())
        }
    }

    @Test
    fun `task preserves AAR without classes jar`() {
        val source = tmp.resolve("resources-only.aar")
        val output = tmp.resolve("resources-only-output.aar")
        ZipOutputStream(Files.newOutputStream(source)).use { aar ->
            writeEntry(aar, "AndroidManifest.xml", "manifest".toByteArray())
        }

        newTask("stripResourcesOnly", source, output).strip()

        ZipFile(output.toFile()).use { aar ->
            assertThat(aar.entries().asSequence().map { it.name }.toList())
                .containsExactly("AndroidManifest.xml")
        }
    }

    @Test
    fun `task preserves empty classes jar`() {
        val source = tmp.resolve("empty-classes.aar")
        val output = tmp.resolve("empty-classes-output.aar")
        writeAar(source, emptyMap())

        newTask("stripEmptyClasses", source, output).strip()

        ZipFile(output.toFile()).use { aar ->
            assertThat(readZip(aar.getInputStream(aar.getEntry("classes.jar")))).isEmpty()
        }
    }

    private fun newTask(name: String, source: Path, output: Path): StripGoClassesFromAarTask {
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        return project.tasks.register(name, StripGoClassesFromAarTask::class.java) {
            sourceAar.set(source.toFile())
            outputAar.set(output.toFile())
        }.get()
    }

    private fun writeAar(target: Path, classes: Map<String, ByteArray>) {
        ZipOutputStream(Files.newOutputStream(target)).use { aar ->
            writeEntry(aar, "AndroidManifest.xml", "manifest".toByteArray())
            writeEntry(aar, "classes.jar", zipBytes(classes))
            writeEntry(aar, "proguard.txt", "rules".toByteArray())
        }
    }

    private fun zipBytes(entries: Map<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, content) -> writeEntry(zip, name, content) }
            }
            output.toByteArray()
        }

    private fun readZip(source: java.io.InputStream): Map<String, ByteArray> =
        ZipInputStream(source).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes())
                }
            }
        }

    private fun writeEntry(output: ZipOutputStream, name: String, content: ByteArray) {
        output.putNextEntry(ZipEntry(name))
        ByteArrayInputStream(content).copyTo(output)
        output.closeEntry()
    }
}
