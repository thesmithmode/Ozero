package binaries

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

abstract class StripGoClassesFromAarTask : DefaultTask() {
    @get:InputFile
    abstract val sourceAar: RegularFileProperty

    @get:OutputFile
    abstract val outputAar: RegularFileProperty

    @TaskAction
    fun strip() {
        val output = outputAar.get().asFile
        output.parentFile.mkdirs()
        ZipFile(sourceAar.get().asFile).use { aar ->
            ZipOutputStream(output.outputStream().buffered()).use { destination ->
                val entries = aar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    destination.putNextEntry(ZipEntry(entry.name))
                    if (entry.name == "classes.jar") {
                        destination.write(filterClasses(aar.getInputStream(entry)))
                    } else {
                        aar.getInputStream(entry).use { input -> input.copyTo(destination) }
                    }
                    destination.closeEntry()
                }
            }
        }
    }

    private fun filterClasses(source: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(source).use { classes ->
            ZipOutputStream(output).use { filtered ->
                while (true) {
                    val entry = classes.nextEntry ?: break
                    if (!entry.name.startsWith("go/")) {
                        filtered.putNextEntry(ZipEntry(entry.name))
                        classes.copyTo(filtered)
                        filtered.closeEntry()
                    }
                }
            }
        }
        return output.toByteArray()
    }
}
