package binaries

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.nio.file.Path

abstract class DownloadBinaryTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val lockFile: RegularFileProperty

    @get:Internal
    abstract val cacheDir: DirectoryProperty

    @get:Internal
    abstract val moduleDir: DirectoryProperty

    @get:Input
    abstract val requestedArtifacts: ListProperty<String>

    @get:Input
    abstract val retryDelaysMs: ListProperty<Long>

    @TaskAction
    fun run() {
        val lockPath = lockFile.get().asFile.toPath()
        val lock =
            try {
                LockFileParser.parse(lockPath)
            } catch (e: LockFileException) {
                throw GradleException(e.message ?: "Lock file parse error", e)
            }

        val cache = cacheDir.get().asFile.toPath()
        val module = moduleDir.get().asFile.toPath()
        val downloader = BinaryDownloader(cacheDir = cache, retryDelaysMs = retryDelaysMs.get())

        val requested = requestedArtifacts.get()
        if (requested.isEmpty()) {
            logger.info("[ozero-binaries] No artifacts requested for ${project.path}")
            return
        }

        for (name in requested) {
            val art = lock.findByName(name)
                ?: throw GradleException(
                    "Artifact '$name' not declared in ${lockPath.fileName}. " +
                        "Add it via binaries.yml workflow dispatch.",
                )
            val dst = resolveDestination(module, art)
            logger.lifecycle("[ozero-binaries] downloading $name → ${module.relativize(dst)}")
            try {
                downloader.download(art.downloadUrl, art.sha256, dst)
            } catch (e: BinaryDownloadException) {
                throw GradleException(e.message ?: "Download failed", e)
            } catch (e: IntegrityException) {
                throw GradleException(e.message ?: "Integrity check failed", e)
            }
        }
    }

    private fun resolveDestination(moduleDir: Path, art: Artifact): Path {
        val finalName = art.targetFilename ?: art.name
        return when (art.destination) {
            Destination.LIBS -> moduleDir.resolve("libs/$finalName")
            Destination.JNI_LIBS -> moduleDir.resolve("src/main/jniLibs/${art.abi}/$finalName")
        }
    }
}
