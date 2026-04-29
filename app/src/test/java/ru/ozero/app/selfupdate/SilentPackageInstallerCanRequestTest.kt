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

class SilentPackageInstallerCanRequestTest {

    @Test
    fun `install returns PermissionDenied when canRequestPackageInstalls is false`() = runTest {
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
        val anyApk = File("/tmp/ozero-perm-${System.nanoTime()}.apk")

        val result = installer.install(anyApk)

        assertIs<SilentPackageInstaller.Result.PermissionDenied>(result)
        verify(exactly = 0) { packageInstaller.createSession(any()) }
    }

    @Test
    fun `install does not return PermissionDenied when canRequestPackageInstalls is true`() = runTest {
        val packageManager = mockk<PackageManager>()
        every { packageManager.canRequestPackageInstalls() } returns true

        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(
            context = context,
            installer = packageInstaller,
            sdkInt = { Build.VERSION_CODES.O },
        )
        val missingApk = File("/tmp/ozero-perm-ok-${System.nanoTime()}.apk")

        val result = installer.install(missingApk)

        assertTrue(result !is SilentPackageInstaller.Result.PermissionDenied)
        assertIs<SilentPackageInstaller.Result.FileError>(result)
    }

    @Test
    fun `install skips canRequestPackageInstalls check below API 26`() = runTest {
        val packageManager = mockk<PackageManager>()

        val context = mockk<Context>(relaxed = true)
        every { context.packageManager } returns packageManager

        val packageInstaller = mockk<PackageInstaller>()
        val installer = SilentPackageInstaller(
            context = context,
            installer = packageInstaller,
            sdkInt = { Build.VERSION_CODES.N },
        )
        val missingApk = File("/tmp/ozero-perm-pre26-${System.nanoTime()}.apk")

        val result = installer.install(missingApk)

        assertIs<SilentPackageInstaller.Result.FileError>(result)
        verify(exactly = 0) { packageManager.canRequestPackageInstalls() }
    }
}
