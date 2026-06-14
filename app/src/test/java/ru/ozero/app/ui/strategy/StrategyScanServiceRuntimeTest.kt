package ru.ozero.app.ui.strategy

import android.app.Application
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class StrategyScanServiceRuntimeTest {

    @Test
    fun `onBind returns null because scan service is started only`() {
        val service = Robolectric.buildService(StrategyScanService::class.java).create().get()

        val binder = service.onBind(Intent())

        assertNull(binder)
    }

    @Test
    fun `start command creates notification channel and stays non sticky`() {
        val context = RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(StrategyScanService::class.java).create().get()

        val result = service.onStartCommand(Intent(context, StrategyScanService::class.java), 0, 1)

        assertEquals(Service.START_NOT_STICKY, result)
        val manager = context.getSystemService(NotificationManager::class.java)
        assertNotNull(manager.getNotificationChannel(StrategyScanService.CHANNEL_ID))
    }

    @Test
    fun `stop action is non sticky and can be followed by destroy`() {
        val context = RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(StrategyScanService::class.java).create().get()
        val intent = Intent(context, StrategyScanService::class.java).setAction(StrategyScanService.ACTION_STOP)

        val result = service.onStartCommand(intent, 0, 2)
        service.onDestroy()

        assertEquals(Service.START_NOT_STICKY, result)
    }
}
