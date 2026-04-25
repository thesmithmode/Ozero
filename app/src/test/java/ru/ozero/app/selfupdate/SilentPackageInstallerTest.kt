package ru.ozero.app.selfupdate

import android.content.Context
import android.content.pm.PackageInstaller
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertIs

class SilentPackageInstallerTest {

    /**
     * Если APK файл отсутствует — install() возвращает FileError ДО
     * createSession. Защищает от orphan PackageInstaller сессий когда
     * verifier удалил битый APK раньше installer'а (race в self-update flow).
     */
    @Test
    fun `install returns FileError when apk file missing and skips PackageInstaller`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(context, packageInstaller)
        val missingApk = File("/tmp/no-such-apk-${System.nanoTime()}.apk")

        val result = installer.install(missingApk)

        assertIs<SilentPackageInstaller.Result.FileError>(result)
        verify(exactly = 0) { packageInstaller.createSession(any()) }
    }

    @Test
    fun `install returns FileError when apk exists but is unreadable`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(context, packageInstaller)
        val tmpApk = File.createTempFile("ozero-test-", ".apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B))
            setReadable(false, false)
            deleteOnExit()
        }
        try {
            val result = installer.install(tmpApk)

            // На POSIX setReadable(false) обычно срабатывает; root-юзер игнорирует.
            // CI runs as non-root → File.canRead() = false → FileError. Если по какой-то
            // причине canRead вернул true (root в Docker) — тест не падает, просто скипает.
            if (!tmpApk.canRead()) {
                assertIs<SilentPackageInstaller.Result.FileError>(result)
                verify(exactly = 0) { packageInstaller.createSession(any()) }
            }
        } finally {
            tmpApk.setReadable(true, false)
            tmpApk.delete()
        }
    }
}
