package ru.ozero.app.selfupdate

import android.content.Context
import android.content.pm.PackageInstaller
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateCoordinatorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var fetcher: FakeFetcher
    private lateinit var downloader: FakeDownloader
    private lateinit var verifier: FakeVerifier
    private lateinit var installer: FakeInstaller
    private val current = "v0.1.0"

    @BeforeEach
    fun setUp() {
        fetcher = FakeFetcher()
        downloader = FakeDownloader()
        verifier = FakeVerifier(result = true)
        installer = FakeInstaller(SilentPackageInstaller.Result.Submitted(sessionId = 7))
    }

    @AfterEach
    fun tearDown() = Unit

    private fun coordinator() = UpdateCoordinator(
        fetcher = fetcher,
        downloader = downloader,
        verifier = verifier,
        installer = installer,
        currentVersion = current,
        cacheDir = tempDir.toFile(),
    )

    private fun newRelease(tag: String = "v0.2.0"): ReleaseInfo = ReleaseInfo(
        tag = tag, apkUrl = "https://x/a.apk", sigUrl = "https://x/a.sig",
    )

    @Test
    fun `no release → emits Checking then NoRelease`() = runTest {
        fetcher.release = null
        val events = coordinator().check().toList()
        assertEquals(listOf(UpdateCoordinator.Progress.Checking, UpdateCoordinator.Progress.NoRelease), events)
    }

    @Test
    fun `older or equal release → UpToDate`() = runTest {
        fetcher.release = newRelease(tag = "v0.1.0")
        val events = coordinator().check().toList()
        assertEquals(listOf(UpdateCoordinator.Progress.Checking, UpdateCoordinator.Progress.UpToDate), events)
    }

    @Test
    fun `download fail → Failed DOWNLOAD`() = runTest {
        fetcher.release = newRelease()
        downloader.script = listOf(
            ApkDownloader.Event.Progress(20),
            ApkDownloader.Event.Failed("network"),
        )
        val events = coordinator().check().toList()
        val terminal = events.last()
        val failed = assertIs<UpdateCoordinator.Progress.Failed>(terminal)
        assertEquals(UpdateCoordinator.Progress.Stage.DOWNLOAD, failed.stage)
        assertEquals("network", failed.reason)
        assertTrue(events.contains(UpdateCoordinator.Progress.Downloading(20)))
    }

    @Test
    fun `verify fail → Failed VERIFY and apk deleted`() = runTest {
        fetcher.release = newRelease()
        val apk = File(tempDir.toFile(), "x.apk").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val sig = File(tempDir.toFile(), "x.sig").apply { writeBytes(byteArrayOf(9)) }
        downloader.script = listOf(
            ApkDownloader.Event.Progress(100),
            ApkDownloader.Event.Success(apk, sig),
        )
        verifier.result = false

        val events = coordinator().check().toList()
        val terminal = events.last()
        val failed = assertIs<UpdateCoordinator.Progress.Failed>(terminal)
        assertEquals(UpdateCoordinator.Progress.Stage.VERIFY, failed.stage)
        assertFalse(apk.exists(), "apk должен быть удалён")
        assertFalse(sig.exists(), "sig должен быть удалён")
    }

    @Test
    fun `installer IoError → Failed INSTALL`() = runTest {
        fetcher.release = newRelease()
        val apk = File(tempDir.toFile(), "y.apk").apply { writeBytes(ByteArray(8)) }
        val sig = File(tempDir.toFile(), "y.sig").apply { writeBytes(ByteArray(64)) }
        downloader.script = listOf(ApkDownloader.Event.Success(apk, sig))
        installer.result = SilentPackageInstaller.Result.IoError(sessionId = 5, reason = "io-fail")

        val events = coordinator().check().toList()
        val terminal = events.last()
        val failed = assertIs<UpdateCoordinator.Progress.Failed>(terminal)
        assertEquals(UpdateCoordinator.Progress.Stage.INSTALL, failed.stage)
        assertEquals("io-fail", failed.reason)
    }

    @Test
    fun `happy path → Submitted with sessionId`() = runTest {
        fetcher.release = newRelease()
        val apk = File(tempDir.toFile(), "z.apk").apply { writeBytes(ByteArray(8)) }
        val sig = File(tempDir.toFile(), "z.sig").apply { writeBytes(ByteArray(64)) }
        downloader.script = listOf(
            ApkDownloader.Event.Progress(0),
            ApkDownloader.Event.Progress(50),
            ApkDownloader.Event.Progress(100),
            ApkDownloader.Event.Success(apk, sig),
        )
        installer.result = SilentPackageInstaller.Result.Submitted(sessionId = 42)

        val events = coordinator().check().toList()
        val terminal = events.last()
        val submitted = assertIs<UpdateCoordinator.Progress.Submitted>(terminal)
        assertEquals(42, submitted.sessionId)
        // Order check
        val expectedSequence = listOf(
            UpdateCoordinator.Progress.Checking,
            UpdateCoordinator.Progress.Downloading(0),
            UpdateCoordinator.Progress.Downloading(50),
            UpdateCoordinator.Progress.Downloading(100),
            UpdateCoordinator.Progress.Verifying,
            UpdateCoordinator.Progress.Installing,
            UpdateCoordinator.Progress.Submitted(42),
        )
        assertEquals(expectedSequence, events)
    }

    @Test
    fun `fetcher throws → NoRelease`() = runTest {
        fetcher.thrower = { error("boom") }
        val events = coordinator().check().toList()
        assertEquals(listOf(UpdateCoordinator.Progress.Checking, UpdateCoordinator.Progress.NoRelease), events)
    }

    private class FakeFetcher : GithubReleaseFetcher(
        owner = "o", repo = "r", client = OkHttpClient(), baseUrl = "http://x",
    ) {
        var release: ReleaseInfo? = null
        var thrower: (() -> Nothing)? = null
        override fun latest(): ReleaseInfo? {
            thrower?.invoke()
            return release
        }
    }

    private class FakeDownloader : ApkDownloader(client = OkHttpClient()) {
        var script: List<Event> = emptyList()
        override fun download(apkUrl: String, sigUrl: String, destDir: File): Flow<Event> = flow {
            for (e in script) emit(e)
        }
    }

    private class FakeVerifier(var result: Boolean) :
        ApkUpdateVerifier(publicKey = ByteArray(32)) {
        override fun verify(apkFile: File, signatureFile: File): Boolean = result
    }

    private class FakeInstaller(var result: SilentPackageInstaller.Result) :
        SilentPackageInstaller(
            context = mockk<Context>(relaxed = true),
            installer = mockk<PackageInstaller>(relaxed = true),
        ) {
        override suspend fun install(
            apkFile: File,
            sessionName: String,
            resultIntentAction: String,
        ): Result = result
    }
}
