package ru.ozero.app.ui.splittunnel

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import io.mockk.any
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppListProviderCoverageTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `drawableToImageBitmap returns null for BitmapDrawable without bitmap`() {
        val drawable = mockk<BitmapDrawable>()
        every { drawable.bitmap } returns null

        assertNull(invokeDrawableToImageBitmap(drawable))
    }

    @Test
    fun `drawableToImageBitmap returns image bitmap for BitmapDrawable with bitmap`() {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val drawable = mockk<BitmapDrawable>()
        every { drawable.bitmap } returns bitmap

        assertNotNull(invokeDrawableToImageBitmap(drawable))
    }

    @Test
    fun `drawableToImageBitmap converts generic drawable`() {
        val drawable = mockk<Drawable>()
        every { drawable.intrinsicWidth } returns 0
        every { drawable.intrinsicHeight } returns 0
        every { drawable.setBounds(any(), any(), any(), any()) } returns Unit
        every { drawable.draw(any()) } returns Unit

        assertNotNull(invokeDrawableToImageBitmap(drawable))
    }

    @Test
    fun `loadIcon caches missing icons after package manager miss`() = runTest(dispatcher) {
        val pm = mockk<PackageManager>()
        every { pm.getApplicationIcon("missing.pkg") } throws PackageManager.NameNotFoundException()
        val context = mockk<Context> {
            every { packageManager } returns pm
            every { applicationContext } returns this
        }
        val provider = DefaultAppListProvider(context)

        assertNull(provider.loadIcon("missing.pkg"))
        assertNull(provider.loadIcon("missing.pkg"))
    }

    @Test
    fun `loadIcon caches successful bitmap drawable result`() = runTest(dispatcher) {
        val pm = mockk<PackageManager>()
        val drawable = mockk<BitmapDrawable>()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        every { drawable.bitmap } returns bitmap
        every { pm.getApplicationIcon("ok.pkg") } returns drawable
        val context = mockk<Context> {
            every { packageManager } returns pm
            every { applicationContext } returns this
        }
        val provider = DefaultAppListProvider(context)

        assertNotNull(provider.loadIcon("ok.pkg"))
        assertNotNull(provider.loadIcon("ok.pkg"))
    }

    private fun invokeDrawableToImageBitmap(drawable: Drawable): Any? {
        val method = Class.forName("ru.ozero.app.ui.splittunnel.AppListProviderKt")
            .getDeclaredMethod("drawableToImageBitmap", Drawable::class.java)
        method.isAccessible = true
        return method.invoke(null, drawable)
    }
}
