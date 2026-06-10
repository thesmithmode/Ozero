package ru.ozero.app.selfupdate

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SilentPackageInstallerTest {

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
        val packageInstaller = mockk<PackageInstaller>(relaxed = true)
        val installer = SilentPackageInstaller(context, packageInstaller)
        val unreadableApk = mockk<File>()
        every { unreadableApk.exists() } returns true
        every { unreadableApk.canRead() } returns false
        every { unreadableApk.absolutePath } returns "/tmp/unreadable.apk"

        val result = installer.install(unreadableApk)

        assertIs<SilentPackageInstaller.Result.FileError>(result)
        verify(exactly = 0) { packageInstaller.createSession(any()) }
    }

    @Test
    fun `install returns PermissionDenied when sdk allows request installs but user denies it`() = runTest {
        val packageManager = mockk<PackageManager>()
        every { packageManager.canRequestPackageInstalls() } returns false

        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(
            context = context,
            installer = packageInstaller,
            sdkInt = { Build.VERSION_CODES.O },
        )
        val apk = File("/tmp/ozero-denied-${System.nanoTime()}.apk")

        val result = installer.install(apk)

        assertIs<SilentPackageInstaller.Result.PermissionDenied>(result)
        assertTrue(result.reason.contains("REQUEST_INSTALL_PACKAGES"))
        verify(exactly = 0) { packageInstaller.createSession(any()) }
    }

    @Test
    fun `install below android O still checks apk file before session`() = runTest {
        val context = mockk<Context>(relaxed = true)
        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(
            context = context,
            installer = packageInstaller,
            sdkInt = { Build.VERSION_CODES.N },
        )
        val missingApk = File("/tmp/ozero-before-o-${System.nanoTime()}.apk")

        val result = installer.install(missingApk)

        assertIs<SilentPackageInstaller.Result.FileError>(result)
        verify(exactly = 0) { packageInstaller.createSession(any()) }
    }
}
