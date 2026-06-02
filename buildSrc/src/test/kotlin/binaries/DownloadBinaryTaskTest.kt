package binaries

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DownloadBinaryTaskTest {
    private lateinit var server: MockWebServer

    @TempDir
    lateinit var tmp: Path

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeLock(soBytes: ByteArray, urlPath: String): Path {
        val sha = sha256(soBytes)
        val lock = tmp.resolve("binaries.lock.yaml")
        Files.writeString(
            lock,
            """
            tag: binaries-test
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: libbyedpi-arm64-v8a.so
                engine: byedpi
                abi: arm64-v8a
                destination: jniLibs
                download_url: ${server.url(urlPath)}
                sha256: $sha
                size_bytes: ${soBytes.size}
                source_repo: https://github.com/hufrea/byedpi
                source_commit: ${"a".repeat(40)}
            """.trimIndent(),
        )
        return lock
    }

    private fun writeLock(content: String): Path {
        val lock = tmp.resolve("binaries-${System.nanoTime()}.lock.yaml")
        Files.writeString(lock, content.trimIndent())
        return lock
    }

    private fun newTask(
        lockPath: Path,
        testModuleDir: Path = tmp.resolve("module-${System.nanoTime()}"),
        requested: List<String>,
    ): DownloadBinaryTask {
        Files.createDirectories(testModuleDir)
        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        return project.tasks.register(
            "downloadBinaries${System.nanoTime()}",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(requested)
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()
    }

    @Test
    fun `task downloads declared artifact to jniLibs destination`() {
        val data = "fake-so".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val lockPath = writeLock(data, "/lib.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)

        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("libbyedpi-arm64-v8a.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        task.run()

        val expected = testModuleDir.resolve("src/main/jniLibs/arm64-v8a/libbyedpi-arm64-v8a.so")
        assertThat(Files.exists(expected)).isTrue()
        assertThat(Files.readAllBytes(expected)).isEqualTo(data)
    }

    @Test
    fun `task fails with canonical message when artifact missing from lock`() {
        val data = "fake".toByteArray()
        val lockPath = writeLock(data, "/x.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)

        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("not-in-lock.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        assertThatThrownBy { task.run() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("not-in-lock.so")
            .hasMessageContaining("not declared in")
    }

    @Test
    fun `task is idempotent on second invocation due to cache`() {
        val data = "fake-so".toByteArray()
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val lockPath = writeLock(data, "/lib.so")

        val project = ProjectBuilder.builder().withProjectDir(tmp.toFile()).build()
        val testModuleDir = tmp.resolve("module")
        Files.createDirectories(testModuleDir)
        val task = project.tasks.register(
            "downloadBinaries",
            DownloadBinaryTask::class.java,
            Action<DownloadBinaryTask> {
                lockFile.set(lockPath.toFile())
                cacheDir.set(tmp.resolve("cache").toFile())
                moduleDir.set(testModuleDir.toFile())
                requestedArtifacts.set(listOf("libbyedpi-arm64-v8a.so"))
                retryDelaysMs.set(listOf(0L, 0L, 0L))
            },
        ).get()

        task.run()
        task.run()
        val expected = testModuleDir.resolve("src/main/jniLibs/arm64-v8a/libbyedpi-arm64-v8a.so")
        assertThat(Files.exists(expected)).isTrue()
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun `task returns without downloads when requested artifacts are empty`() {
        val data = "fake-so".toByteArray()
        val lockPath = writeLock(data, "/lib.so")
        val task = newTask(lockPath = lockPath, requested = emptyList())

        task.run()

        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `task downloads libs artifact using target filename`() {
        val data = "fake-aar".toByteArray()
        val sha = sha256(data)
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(data)))
        val lockPath = writeLock(
            """
            tag: binaries-test
            generated_at: 2026-04-25T10:00:00Z
            artifacts:
              - name: original.aar
                target_filename: renamed.aar
                engine: xray
                destination: libs
                download_url: ${server.url("/x.aar")}
                sha256: $sha
                size_bytes: ${data.size}
                source_repo: https://github.com/XTLS/Xray-core
                source_commit: ${"a".repeat(40)}
            """,
        )
        val testModuleDir = tmp.resolve("module-libs")
        val task = newTask(lockPath = lockPath, testModuleDir = testModuleDir, requested = listOf("original.aar"))

        task.run()

        assertThat(Files.readAllBytes(testModuleDir.resolve("libs/renamed.aar"))).isEqualTo(data)
    }

    @Test
    fun `task wraps malformed lock file as gradle exception`() {
        val lockPath = writeLock("tag: [unclosed")
        val task = newTask(lockPath = lockPath, requested = listOf("libbyedpi-arm64-v8a.so"))

        assertThatThrownBy { task.run() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("Malformed YAML")
            .hasCauseInstanceOf(LockFileException::class.java)
    }

    @Test
    fun `task wraps download failure as gradle exception`() {
        server.enqueue(MockResponse().setResponseCode(404))
        val data = "fake-so".toByteArray()
        val lockPath = writeLock(data, "/missing.so")
        val task = newTask(lockPath = lockPath, requested = listOf("libbyedpi-arm64-v8a.so"))

        assertThatThrownBy { task.run() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("404")
            .hasCauseInstanceOf(BinaryDownloadException::class.java)
    }

    @Test
    fun `task wraps integrity failure as gradle exception`() {
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write("wrong".toByteArray())))
        val lockPath = writeLock("expected".toByteArray(), "/lib.so")
        val task = newTask(lockPath = lockPath, requested = listOf("libbyedpi-arm64-v8a.so"))

        assertThatThrownBy { task.run() }
            .isInstanceOf(GradleException::class.java)
            .hasMessageContaining("SHA256")
            .hasCauseInstanceOf(IntegrityException::class.java)
    }
}
