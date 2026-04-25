import binaries.DownloadBinaryTask
import binaries.OzeroBinariesExtension

val ext = extensions.create<OzeroBinariesExtension>("ozeroBinaries")

val rootDirPath = project.rootDir.toPath()
val lockFilePath = rootDirPath.resolve("build-tools/binaries.lock.yaml")
val cacheDirPath = project.gradle.gradleUserHomeDir.toPath().resolve("caches/ozero-binaries")

val downloadBinaries = tasks.register("downloadBinaries", DownloadBinaryTask::class.java) {
    lockFile.set(lockFilePath.toFile())
    cacheDir.set(cacheDirPath.toFile())
    moduleDir.set(project.projectDir)
    requestedArtifacts.set(provider { ext.names() })
    retryDelaysMs.set(listOf(3000L, 9000L, 27000L))
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(downloadBinaries)
}
